package com.emotionalleaves.sprout.domain.forecast

import java.time.LocalDate

/** A dated expected money movement. [amountMinor] is positive for both income and bills. */
data class Expected(val name: String, val date: LocalDate, val amountMinor: Long)

data class Forecast(
    val startMinor: Long,
    val incomeMinor: Long,
    val billsMinor: Long,
    /** Everyday spending expected for the rest of the month (budget left, or the recent daily average). */
    val spendingMinor: Long,
    val endMinor: Long,
    /** The lowest projected balance before month end and its date (bills can land before payday). */
    val lowMinor: Long,
    val lowDate: LocalDate,
    val nextIncome: Expected?,
    /** True when everyday spending came from budgets; false when from the last 30 days' average. */
    val spendingFromBudgets: Boolean,
    /** Projected balance at the end of each day from today to month end. */
    val daily: List<Pair<LocalDate, Long>> = emptyList(),
    /** Paychecks (true) and bills (false) still expected this month, by date. */
    val events: List<Pair<Expected, Boolean>> = emptyList(),
)

/**
 * Projects the cash balance to the end of the month: today's balance, plus expected paychecks, minus expected
 * bills, minus everyday spending spread evenly over the remaining days. Pure; amounts in cents.
 */
object CashFlow {

    fun forecast(
        today: LocalDate,
        cashMinor: Long,
        income: List<Expected>,
        bills: List<Expected>,
        /** Budget left this month for budgeted categories, or null when there are no budgets. */
        budgetRemainingMinor: Long?,
        /** Everyday (non-bill) spending over the last 30 days, positive. Used when there are no budgets. */
        recentSpendingMinor: Long,
    ): Forecast {
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        fun inRange(e: Expected) = !e.date.isBefore(today) && !e.date.isAfter(monthEnd)
        val inc = income.filter(::inRange).sortedBy { it.date }
        val out = bills.filter(::inRange).sortedBy { it.date }
        val days = (monthEnd.toEpochDay() - today.toEpochDay() + 1).toInt()
        val spending = budgetRemainingMinor?.coerceAtLeast(0) ?: (recentSpendingMinor.coerceAtLeast(0) * days / 30)
        val perDay = if (days > 0) spending / days else 0
        val remainder = spending - perDay * days

        // Walk day by day to find the low point.
        var balance = cashMinor
        var low = cashMinor
        var lowDate = today
        var d = today
        var i = 0
        val daily = ArrayList<Pair<LocalDate, Long>>(days)
        while (!d.isAfter(monthEnd)) {
            balance += inc.filter { it.date == d }.sumOf { it.amountMinor }
            balance -= out.filter { it.date == d }.sumOf { it.amountMinor }
            balance -= perDay + if (i == 0) remainder else 0
            if (balance < low) { low = balance; lowDate = d }
            daily += d to balance
            d = d.plusDays(1)
            i++
        }
        return Forecast(
            startMinor = cashMinor,
            incomeMinor = inc.sumOf { it.amountMinor },
            billsMinor = out.sumOf { it.amountMinor },
            spendingMinor = spending,
            endMinor = balance,
            lowMinor = low,
            lowDate = lowDate,
            nextIncome = income.filter { !it.date.isBefore(today) }.minByOrNull { it.date },
            spendingFromBudgets = budgetRemainingMinor != null,
            daily = daily,
            events = (inc.map { it to true } + out.map { it to false }).sortedBy { it.first.date },
        )
    }

    /** Spending this month through today vs the same days last month. Positive delta = spending more. */
    data class Trend(val thisMonthMinor: Long, val lastMonthSameDaysMinor: Long) {
        val deltaMinor: Long get() = thisMonthMinor - lastMonthSameDaysMinor
        /** Whole percent change, or null when last month had nothing to compare with. */
        val percent: Int? get() = if (lastMonthSameDaysMinor <= 0) null else ((deltaMinor * 100) / lastMonthSameDaysMinor).toInt()
    }
}
