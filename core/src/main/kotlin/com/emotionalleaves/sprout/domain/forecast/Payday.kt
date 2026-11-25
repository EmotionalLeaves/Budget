package com.emotionalleaves.sprout.domain.forecast

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "Until payday": how much cash is really free between now and the next paycheck.
 * cash now − bills due before payday = what's left; divided by the days until payday = a daily allowance.
 */
data class PaydayPlan(
    val payday: LocalDate,
    val payName: String,
    val payMinor: Long,
    val daysUntil: Int,
    val cashMinor: Long,
    val bills: List<Expected>,
    val billsMinor: Long,
    val leftMinor: Long,
    /** Per day until payday (today counts); 0 when there's nothing left. */
    val perDayMinor: Long,
) {
    val short: Boolean get() = leftMinor < 0

    companion object {
        /** Null when no income repeats (nothing to plan to) or there's no cash account. */
        fun compute(today: LocalDate, cashMinor: Long?, income: List<Expected>, bills: List<Expected>): PaydayPlan? {
            if (cashMinor == null) return null
            val next = income.filter { !it.date.isBefore(today) && it.amountMinor > 0 }.minByOrNull { it.date } ?: return null
            // Bills due today up to the day before payday (payday bills usually come out of the new paycheck).
            val due = bills.filter { !it.date.isBefore(today) && it.date.isBefore(next.date) }.sortedBy { it.date }
            val billsMinor = due.sumOf { kotlin.math.abs(it.amountMinor) }
            val left = cashMinor - billsMinor
            val days = maxOf(1, ChronoUnit.DAYS.between(today, next.date).toInt())
            return PaydayPlan(next.date, next.name, next.amountMinor, days, cashMinor, due, billsMinor, left, if (left > 0) left / days else 0)
        }
    }
}
