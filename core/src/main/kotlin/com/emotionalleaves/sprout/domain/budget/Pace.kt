package com.emotionalleaves.sprout.domain.budget

/**
 * Pace: where this month's spending is heading if it continues at today's rate. Early in the month a single
 * purchase would swing the forecast wildly, so the first [MIN_DAY] days only show "too early".
 */
data class Pace(val projectedMinor: Long, val budgetMinor: Long?, val tooEarly: Boolean) {
    val overByMinor: Long? get() = budgetMinor?.let { projectedMinor - it }?.takeIf { !tooEarly }
    val onTrack: Boolean get() = tooEarly || budgetMinor == null || projectedMinor <= budgetMinor

    companion object {
        const val MIN_DAY = 5

        fun of(spentMinor: Long, budgetMinor: Long?, dayOfMonth: Int, daysInMonth: Int): Pace {
            val day = dayOfMonth.coerceIn(1, daysInMonth)
            val projected = if (day >= daysInMonth) spentMinor else spentMinor * daysInMonth / day
            return Pace(maxOf(projected, spentMinor), budgetMinor, day < MIN_DAY)
        }
    }
}

/**
 * Flex budgeting (one number for everyday spending): instead of a cap per category, fixed costs (bills and
 * categories you mark fixed) are expected, and one Flex amount covers everything else.
 */
data class FlexMonth(
    val flexBudgetMinor: Long,
    val flexSpentMinor: Long,
    val fixedSpentMinor: Long,
    val fixedExpectedMinor: Long,
    val plannedSetAsideMinor: Long,
    val daysRemaining: Int?,
) {
    val flexLeftMinor: Long get() = flexBudgetMinor - flexSpentMinor
    /** Safe to spend in flex mode: what's left of Flex minus this month's set-asides for planned expenses. */
    val safeMinor: Long get() = flexLeftMinor - plannedSetAsideMinor
    val perDayMinor: Long? get() = daysRemaining?.takeIf { it > 0 && safeMinor > 0 }?.let { safeMinor / it }

    companion object {
        /** Flex categories are every EXPENSE category not in [fixedIds]. Sums are signed (spending negative). */
        fun of(
            flexBudgetMinor: Long,
            expenseSums: Map<Long, Long>,
            fixedIds: Set<Long>,
            fixedExpectedMinor: Long,
            plannedSetAsideMinor: Long,
            daysRemaining: Int?,
        ): FlexMonth {
            val spent = expenseSums.mapValues { (_, v) -> maxOf(0L, -v) }
            return FlexMonth(
                flexBudgetMinor = flexBudgetMinor,
                flexSpentMinor = spent.filterKeys { it !in fixedIds }.values.sum(),
                fixedSpentMinor = spent.filterKeys { it in fixedIds }.values.sum(),
                fixedExpectedMinor = fixedExpectedMinor,
                plannedSetAsideMinor = plannedSetAsideMinor,
                daysRemaining = daysRemaining,
            )
        }
    }
}
