package com.emotionalleaves.sprout.domain.insights

import java.time.YearMonth

/** A month in review. Amounts are positive cents. */
data class Recap(
    val month: YearMonth,
    val spentMinor: Long,
    val incomeMinor: Long,
    val previousSpentMinor: Long?,
    val topCategories: List<Pair<String, Long>>,
    val biggest: Pair<String, Long>?,
    val noSpendDays: Int,
    val transactions: Int,
) {
    val savedMinor: Long get() = incomeMinor - spentMinor
    /** Change vs the month before, in whole percent; null without a previous month. */
    val changePercent: Int? get() = previousSpentMinor?.takeIf { it > 0 }?.let { (((spentMinor - it) * 100) / it).toInt() }

    /** One-line headline for the card. */
    fun headline(): String = when {
        incomeMinor > 0 && savedMinor > 0 -> "You kept ${savedMinor * 100 / incomeMinor}% of what came in"
        incomeMinor > 0 -> "Spending was above income"
        else -> "Here's where your money went"
    }
}
