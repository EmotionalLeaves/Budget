package com.emotionalleaves.sprout.domain.search

import com.emotionalleaves.sprout.domain.money.Money

/**
 * What the search box means. Text matches descriptions, merchants and notes. An amount finds charges of that
 * size: "42.10" (exact, either sign), ">100" (over $100), "<5" (under $5), "20-50" (between).
 */
data class SearchQuery(val text: String, val minAbsMinor: Long? = null, val maxAbsMinor: Long? = null) {
    val isAmount: Boolean get() = minAbsMinor != null || maxAbsMinor != null

    companion object {
        private val RANGE = Regex("""^\$?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:-|–|to)\s*\$?\s*(\d[\d,]*(?:\.\d{1,2})?)$""")

        fun parse(raw: String): SearchQuery {
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
