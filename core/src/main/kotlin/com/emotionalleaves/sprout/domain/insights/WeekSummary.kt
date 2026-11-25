package com.emotionalleaves.sprout.domain.insights

import com.emotionalleaves.sprout.domain.money.Money

/** The Sunday "your week" line. Amounts are positive spending in cents. */
object WeekSummary {
    fun text(thisWeek: Long, lastWeek: Long, biggestName: String?, biggestMinor: Long?): String = buildString {
        append("You spent ").append(Money.formatWhole(thisWeek)).append(" this week")
        val d = thisWeek - lastWeek
        when {
            lastWeek <= 0 -> append(".")
            kotlin.math.abs(d) * 20 < lastWeek -> append(", about the same as last week.") // within 5%
            d < 0 -> append(", ").append(Money.formatWhole(-d)).append(" less than last week.")
            else -> append(", ").append(Money.formatWhole(d)).append(" more than last week.")
        }
        if (biggestName != null && biggestMinor != null && biggestMinor > 0) {
            append(" Biggest: ").append(biggestName).append(", ").append(Money.formatWhole(biggestMinor)).append(".")
        }
    }
}
