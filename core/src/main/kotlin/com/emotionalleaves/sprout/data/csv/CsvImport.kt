package com.emotionalleaves.sprout.data.csv

import com.emotionalleaves.sprout.domain.text.Sanitize
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Which column holds what. Either [amount] or at least one of [debit]/[credit] is set. */
data class CsvMapping(
    val headerRow: Int,
    val date: Int,
    val description: Int,
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
    val dateFormat: String,
    /** Flip every sign (some card exports show purchases as positive). */
    val invert: Boolean = false,
    /** European amounts: "1.234,56" means 1234.56. */
    val decimalComma: Boolean = false,
    /** The bank's own category column, if the file has one. */
    val category: Int? = null,
) {
    val isValid: Boolean get() = date >= 0 && description >= 0 && (amount != null || debit != null || credit != null)
}

data class CsvRow(val line: Int, val date: LocalDate, val amountMinor: Long, val description: String, val category: String? = null)
data class CsvRowError(val line: Int, val reason: String)
data class CsvParseResult(val rows: List<CsvRow>, val errors: List<CsvRowError>)

/**
 * Turns a bank's CSV export into transactions: finds the header row (skipping summary lines above it),
 * guesses the column mapping and date format, parses amounts like "$1,234.56", "(12.00)" and "12.00-".
 */
object CsvImport {

    private val DATE_HEADERS = listOf("transaction date", "trans. date", "trans date", "posted date", "post date",
        "posting date", "date posted", "date")
    private val DESC_HEADERS = listOf("description", "payee", "name", "merchant", "details", "transaction description",
        "original description", "memo")
    private val AMOUNT_HEADERS = listOf("amount", "transaction amount", "amount (usd)", "value")
    private val DEBIT_HEADERS = listOf("debit", "debits", "withdrawal", "withdrawals", "money out", "debit amount", "charges")
    private val CREDIT_HEADERS = listOf("credit", "credits", "deposit", "deposits", "money in", "credit amount", "payments")

    /** Date formats tried in order (US first). */
    val DATE_FORMATS = listOf(
        "MM/dd/yyyy", "M/d/yyyy", "MM/dd/yy", "M/d/yy", "yyyy-MM-dd", "yyyy/MM/dd", "MM-dd-yyyy", "M-d-yyyy",
        "yyyyMMdd", "MMM d, yyyy", "MMM dd, yyyy", "d MMM yyyy", "dd/MM/yyyy",
    )

    private val CATEGORY_HEADERS = listOf("category", "categories", "transaction category")

    private fun norm(h: String) = h.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Finds the header row in the first 20 rows and guesses columns and date format. Null if none fits. */
    fun detect(rows: List<List<String>>): CsvMapping? {
        for ((idx, row) in rows.take(20).withIndex()) {
            val h = row.map(::norm)
            fun find(names: List<String>): Int? = names.firstNotNullOfOrNull { n -> h.indexOf(n).takeIf { it >= 0 } }
                ?: names.firstNotNullOfOrNull { n -> h.indexOfFirst { it.contains(n) }.takeIf { it >= 0 } }
            val date = find(DATE_HEADERS) ?: continue
            val desc = find(DESC_HEADERS) ?: continue
            val amount = find(AMOUNT_HEADERS)?.takeIf { it != date && it != desc }
            val debit = find(DEBIT_HEADERS)?.takeIf { it != date && it != desc }
            val credit = find(CREDIT_HEADERS)?.takeIf { it != date && it != desc }
            if (amount == null && debit == null && credit == null) continue
            val samples = rows.drop(idx + 1).take(50).mapNotNull { it.getOrNull(date)?.trim()?.takeIf(String::isNotEmpty) }
            val format = detectDateFormat(samples) ?: DATE_FORMATS.first()
            val amountCols = listOfNotNull(amount, debit, credit)
            val amountSamples = rows.drop(idx + 1).take(50).flatMap { r -> amountCols.mapNotNull { r.getOrNull(it)?.trim()?.takeIf(String::isNotEmpty) } }
            val category = h.indexOfFirst { it in CATEGORY_HEADERS }.takeIf { it >= 0 && it != date && it != desc && it !in amountCols }
            return CsvMapping(
                idx, date, desc, if (debit != null || credit != null) null else amount, debit, credit, format,
                decimalComma = detectDecimalComma(amountSamples), category = category,
            )
        }
        return null
    }

    /** The first format that parses every sample (or the most samples). */
    fun detectDateFormat(samples: List<String>): String? {
        if (samples.isEmpty()) return null
        val scored = DATE_FORMATS.map { f -> f to samples.count { parseDate(it, f) != null } }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second == 0) null else best.first
    }

    fun parseDate(text: String, format: String): LocalDate? = try {
        val pattern = if (format.contains("yyyy")) format.replace("yyyy", "uuuu") else format.replace("yy", "uu")
        val f = DateTimeFormatter.ofPattern(pattern, Locale.US).withResolverStyle(ResolverStyle.STRICT)
        val d = LocalDate.parse(text.trim(), f)
        // Two-digit years: keep them within 1950–2049.
        if (!format.contains("yyyy") && format.contains("yy") && d.year >= 2050) d.minusYears(100) else d
    } catch (e: DateTimeParseException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * True when amounts use a decimal comma ("12,34", "1.234,56"). Votes per sample: a comma followed by 1–2
     * digits at the end is a decimal comma; a period followed by 1–2 digits is a decimal point. "1,234" (US
     * thousands) votes for neither.
     */
    fun detectDecimalComma(samples: List<String>): Boolean {
        val comma = Regex(""",\d{1,2}\)?-?$""")
        val dot = Regex("""\.\d{1,2}\)?-?$""")
        val c = samples.count { comma.containsMatchIn(it.trim()) }
        val d = samples.count { dot.containsMatchIn(it.trim()) }
        return c > d
    }

    /** "$1,234.56" → 123456, "(12.00)" → -1200, "-12.00", "12.00-" → -1200, "" → null. */
    fun parseAmount(text: String?, decimalComma: Boolean = false): Long? {
        if (text == null) return null
        var s = text.trim().replace("€", "").replace("\u00A0", "").trim()
        if (decimalComma) s = s.replace(".", "").replace(" ", "").replace(",", ".")
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1).trim() }
        if (s.endsWith("-")) { negative = !negative; s = s.dropLast(1).trim() }
        if (s.startsWith("-")) { negative = !negative; s = s.drop(1).trim() } else if (s.startsWith("+")) s = s.drop(1).trim()
        s = s.removePrefix("USD").trim().removePrefix("$").trim().replace(",", "").replace(" ", "")
        if (s.startsWith("-")) { negative = !negative; s = s.drop(1) }
        if (s.length > 20 || !Regex("""\d+(\.\d+)?|\.\d+""").matches(s)) return null
        val minor = try {
            BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            return null
        }
        return if (negative) -minor else minor
    }

    fun parse(rows: List<List<String>>, m: CsvMapping): CsvParseResult {
        val out = mutableListOf<CsvRow>()
        val errors = mutableListOf<CsvRowError>()
        rows.drop(m.headerRow + 1).forEachIndexed { i, row ->
            val line = m.headerRow + 2 + i
            if (row.all { it.isBlank() }) return@forEachIndexed
            val dateText = row.getOrNull(m.date)?.trim().orEmpty()
            val date = parseDate(dateText, m.dateFormat)
            if (date == null) {
                errors += CsvRowError(line, if (dateText.isEmpty()) "No date" else "Unreadable date \"${Sanitize.text(dateText, 20)}\"")
                return@forEachIndexed
            }
            val amount: Long? = if (m.amount != null) {
                parseAmount(row.getOrNull(m.amount), m.decimalComma)
            } else {
                val debit = m.debit?.let { parseAmount(row.getOrNull(it), m.decimalComma) }
                val credit = m.credit?.let { parseAmount(row.getOrNull(it), m.decimalComma) }
                if (debit == null && credit == null) null else (credit?.let { kotlin.math.abs(it) } ?: 0L) - (debit?.let { kotlin.math.abs(it) } ?: 0L)
            }
            if (amount == null) {
                errors += CsvRowError(line, "No amount")
                return@forEachIndexed
            }
            val raw = row.getOrNull(m.description).orEmpty().let {
                // Undo the apostrophe exports add before formula-like text (=, +, -, @).
                if (it.length > 1 && it[0] == '\'' && it[1] in "=+-@") it.substring(1) else it
            }
            val desc = Sanitize.text(raw, 300).ifBlank { "Imported transaction" }
            val category = m.category?.let { row.getOrNull(it) }?.let { Sanitize.text(it, 60) }?.takeIf { it.isNotBlank() }
            out += CsvRow(line, date, if (m.invert) -amount else amount, desc, category)
        }
        return CsvParseResult(out, errors)
    }

    /**
     * Deterministic ids, so re-importing the same or an overlapping file adds nothing twice:
     * "{accountKey}:csv:{sha256(date|amountMinor|normalizedDescription|occurrenceIndex)}".
     * The occurrence index tells genuinely identical rows in one file apart (two $5 coffees the same day).
     */
    fun ids(accountKey: String, rows: List<CsvRow>): List<String> {
        val seen = HashMap<String, Int>()
        return rows.map { r ->
            val base = "${r.date}|${r.amountMinor}|${normalizeDescription(r.description)}"
            val n = seen.getOrDefault(base, 0)
            seen[base] = n + 1
            "$accountKey:csv:" + sha256("$base|$n")
        }
    }

    /** Uppercase, single spaces: so whitespace or case changes between exports don't create duplicates. */
    fun normalizeDescription(d: String): String = d.uppercase().replace(Regex("\\s+"), " ").trim()

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
