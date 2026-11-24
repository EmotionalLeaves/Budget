package com.emotionalleaves.sprout.data.csv

import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** A transaction as exported. */
data class ExportTx(
    val occurredAt: Long,
    val description: String,
    val amountMinor: Long,
    val category: String,
    val account: String,
    val pending: Boolean,
    val note: String?,
    val merchantKey: String,
)

/**
 * RFC 4180 CSV with a header row, ISO-8601 dates (yyyy-MM-dd) and plain decimal amounts ("-12.34").
 * The format is what [CsvImport] reads back, so an export re-imports to the same transactions.
 * Exports never contain the SimpleFIN access URL or any credential: callers only pass the fields below.
 */
object CsvExport {
    val TRANSACTION_HEADER = listOf("Date", "Description", "Amount", "Category", "Account", "Pending", "Note", "Merchant")

    fun amount(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()

    fun isoDate(epochSeconds: Long, zone: ZoneId): String = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate().toString()

    fun transactions(rows: List<ExportTx>, zone: ZoneId): String = table(
        TRANSACTION_HEADER,
        rows.map { t ->
            listOf(
                isoDate(t.occurredAt, zone), t.description, amount(t.amountMinor), t.category, t.account,
                if (t.pending) "yes" else "no", t.note.orEmpty(), t.merchantKey,
            )
        },
    )

    fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        append(Csv.row(header))
        rows.forEach { append(Csv.row(it.map(::neutralize))) }
    }

    /**
     * Spreadsheet apps run cells starting with = + - @ as formulas. Text cells that look like that get a leading
     * apostrophe; plain numbers ("-12.34") are left alone so they stay numbers and re-import cleanly.
     */
    fun neutralize(cell: String): String {
        if (cell.isEmpty()) return cell
        val first = cell[0]
        if (first !in "=+-@\t\r") return cell
        return if (Regex("""[+-]?\d+(\.\d+)?""").matches(cell)) cell else "'$cell"
    }
}
