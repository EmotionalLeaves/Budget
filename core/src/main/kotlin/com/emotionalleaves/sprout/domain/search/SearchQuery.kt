package com.emotionalleaves.sprout.domain.search

import com.emotionalleaves.sprout.domain.money.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * What the search box means. Text matches descriptions, merchants and notes. An amount finds charges of that
 * size: "42.10" (exact, either sign), ">100" (over $100), "<5" (under $5), "20-50" (between).
 * A date phrase anywhere narrows it to a time: "today", "yesterday", "this week", "last month", "this year",
 * "nov", "november 2025". They combine: "hy-vee last month", ">50 october".
 */
data class SearchQuery(
    val text: String,
    val minAbsMinor: Long? = null,
    val maxAbsMinor: Long? = null,
    /** First and last day (both included) the search is limited to, or null for any time. */
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    /** The date phrase in plain words ("November 2025"), to show what Sprout understood. */
    val dateLabel: String? = null,
) {
    val isAmount: Boolean get() = minAbsMinor != null || maxAbsMinor != null
    val hasDate: Boolean get() = fromDate != null

    companion object {
        private val RANGE = Regex("""^\$?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:-|–|to)\s*\$?\s*(\d[\d,]*(?:\.\d{1,2})?)$""")

        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December")

        private fun month(token: String): Int? {
            val t = token.lowercase().trimEnd('.', ',')
            if (t.length < 3) return null
            val i = MONTHS.indexOf(t.take(3))
            // "nov", "november" and "sept" count; "novel" or "marathon" don't.
            return i.takeIf { it >= 0 && (t.length == 3 || MONTH_NAMES[it].lowercase().startsWith(t) || t == "sept") }?.plus(1)
        }

        private data class DateHit(val from: LocalDate, val to: LocalDate, val label: String, val start: Int, val len: Int)

        /** Finds the first date phrase among the words, or null. */
        private fun findDate(words: List<String>, today: LocalDate): DateHit? {
            val lower = words.map { it.lowercase().trimEnd('.', ',') }
            for (i in lower.indices) {
                val w = lower[i]; val next = lower.getOrNull(i + 1)
                if (w == "this" || w == "last") {
                    val back = if (w == "last") 1L else 0L
                    when (next) {
                        "week" -> {
                            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(back)
                            return DateHit(start, if (back == 0L) today else start.plusDays(6), if (back == 0L) "This week" else "Last week", i, 2)
                        }
                        "month" -> {
                            val m = YearMonth.from(today).minusMonths(back)
                            return DateHit(m.atDay(1), if (back == 0L) today else m.atEndOfMonth(), if (back == 0L) "This month" else "Last month", i, 2)
                        }
                        "year" -> {
                            val y = today.year - back.toInt()
                            return DateHit(LocalDate.of(y, 1, 1), if (back == 0L) today else LocalDate.of(y, 12, 31), if (back == 0L) "This year" else "Last year", i, 2)
                        }
                    }
                }
                if (w == "today") return DateHit(today, today, "Today", i, 1)
                if (w == "yesterday") return DateHit(today.minusDays(1), today.minusDays(1), "Yesterday", i, 1)
                val m = month(w) ?: continue
                val year = next?.toIntOrNull()?.takeIf { it in 2000..2100 }
                // Without a year, a month means its latest one that has started.
                val y = year ?: if (m > today.monthValue) today.year - 1 else today.year
                val ym = YearMonth.of(y, m)
                return DateHit(ym.atDay(1), ym.atEndOfMonth(), "${MONTH_NAMES[m - 1]} $y", i, if (year != null) 2 else 1)
            }
            return null
        }

        fun parse(raw: String, today: LocalDate = LocalDate.now()): SearchQuery {
            val words = raw.trim().take(80).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val hit = findDate(words, today) ?: return parseRest(raw)
            val rest = (words.take(hit.start) + words.drop(hit.start + hit.len)).joinToString(" ")
            return parseRest(rest).copy(fromDate = hit.from, toDate = hit.to, dateLabel = hit.label)
        }

        private fun parseRest(raw: String): SearchQuery {
            val q = raw.trim().take(80)
            if (q.isEmpty()) return SearchQuery("")
            fun amt(s: String) = Money.parseUserInput(s.trim().removePrefix("$"))?.let { kotlin.math.abs(it) }
            when {
                q.startsWith(">=") -> amt(q.drop(2))?.let { return SearchQuery("", minAbsMinor = it) }
                q.startsWith(">") -> amt(q.drop(1))?.let { return SearchQuery("", minAbsMinor = it + 1) }
                q.startsWith("<=") -> amt(q.drop(2))?.let { return SearchQuery("", maxAbsMinor = it) }
                q.startsWith("<") -> amt(q.drop(1))?.let { return SearchQuery("", maxAbsMinor = (it - 1).coerceAtLeast(0)) }
            }
            RANGE.matchEntire(q)?.let { m ->
                val a = amt(m.groupValues[1]); val b = amt(m.groupValues[2])
                if (a != null && b != null) return SearchQuery("", minOf(a, b), maxOf(a, b))
            }
            if (Regex("""^\$?\s*-?\d[\d,]*(\.\d{1,2})?$""").matches(q)) {
                amt(q.replace("-", ""))?.let { return SearchQuery("", it, it) }
            }
            return SearchQuery(q)
        }
    }
}
