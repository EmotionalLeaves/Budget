package com.emotionalleaves.sprout.domain.insights

/** One category in a month-vs-month comparison. Amounts are positive spending in cents. */
data class CompareRow(val name: String, val nowMinor: Long, val beforeMinor: Long) {
    val deltaMinor: Long get() = nowMinor - beforeMinor
    /** Percent change; null when there was nothing before (a "new" line). */
    val percent: Int? get() = if (beforeMinor <= 0) null else ((deltaMinor * 100) / beforeMinor).toInt()
}

object MonthCompare {
    /** Every category in either month, biggest change first. Rows with no spending in both are dropped. */
    fun compare(now: Map<String, Long>, before: Map<String, Long>): List<CompareRow> =
        (now.keys + before.keys).map { CompareRow(it, now[it] ?: 0, before[it] ?: 0) }
            .filter { it.nowMinor != 0L || it.beforeMinor != 0L }
            .sortedWith(compareByDescending<CompareRow> { kotlin.math.abs(it.deltaMinor) }.thenBy { it.name })
}
