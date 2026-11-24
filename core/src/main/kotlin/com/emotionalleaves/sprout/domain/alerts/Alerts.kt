package com.emotionalleaves.sprout.domain.alerts

import com.emotionalleaves.sprout.domain.budget.CategoryLine
import kotlin.math.abs

/** A budget threshold alert to show, plus every threshold to record as fired. */
data class BudgetAlertDecision(
    val categoryId: Long,
    val categoryName: String,
    /** 80 or 100: the alert to show. */
    val threshold: Int,
    val spentMinor: Long,
    val budgetMinor: Long,
    /** Thresholds to mark as fired (crossing 100% also marks 80% so it never fires late). */
    val record: List<Int>,
)

object BudgetAlerts {
    const val WARN = 80
    const val OVER = 100

    /**
     * Each threshold fires at most once per category per month. If one transaction crosses both,
     * only the 100% alert shows. [fired] holds (categoryId, threshold) already fired this month.
     */
    fun evaluate(lines: List<CategoryLine>, fired: Set<Pair<Long, Int>>): List<BudgetAlertDecision> =
        lines.mapNotNull { line ->
            val budget = line.budgetMinor ?: return@mapNotNull null
            if (budget <= 0) return@mapNotNull null
            val pct = line.percentUsed ?: return@mapNotNull null
            val id = line.category.id
            val has80 = (id to WARN) in fired
            val has100 = (id to OVER) in fired
            when {
                pct >= OVER && !has100 ->
                    BudgetAlertDecision(id, line.category.name, OVER, line.spentMinor, budget, listOfNotNull(OVER, if (!has80) WARN else null))
                pct >= WARN && pct < OVER && !has80 ->
                    BudgetAlertDecision(id, line.category.name, WARN, line.spentMinor, budget, listOf(WARN))
                else -> null
            }
        }
}

enum class UnusualReason { LARGE, UNUSUAL_FOR_MERCHANT, NEW_MERCHANT }

object UnusualDetector {
    const val DEFAULT_LARGE_THRESHOLD_MINOR = 25_000L
    /** A merchant needs this many earlier charges before "unusual for this merchant" applies. */
    const val MIN_PRIOR = 3
    /** "Unusual" means more than 2.5× the merchant's median charge (compared in integers: 4a > 5 × 2median). */
    const val UNUSUAL_MIN_MINOR = 5_000L
    const val NEW_MERCHANT_MIN_MINOR = 15_000L

    /**
     * Checks one newly synced outflow. [priorAmountsMinor] are the merchant's earlier charges (any sign; absolute
     * values are compared). Returns null for inflows and ordinary charges. Large wins over unusual.
     */
    fun check(amountMinor: Long, priorAmountsMinor: List<Long>, largeThresholdMinor: Long): UnusualReason? {
        if (amountMinor >= 0) return null
        val a = abs(amountMinor)
        if (largeThresholdMinor > 0 && a >= largeThresholdMinor) return UnusualReason.LARGE
        val priors = priorAmountsMinor.map { abs(it) }.filter { it > 0 }
        return when {
            priors.isEmpty() -> if (a >= NEW_MERCHANT_MIN_MINOR) UnusualReason.NEW_MERCHANT else null
            priors.size >= MIN_PRIOR && a >= UNUSUAL_MIN_MINOR && 4 * a > 5 * medianTimes2(priors) ->
                UnusualReason.UNUSUAL_FOR_MERCHANT
            else -> null
        }
    }

    /** 2 × median, in integers (avoids halves for even counts). */
    internal fun medianTimes2(values: List<Long>): Long {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) 2 * s[n / 2] else s[n / 2 - 1] + s[n / 2]
    }
}
