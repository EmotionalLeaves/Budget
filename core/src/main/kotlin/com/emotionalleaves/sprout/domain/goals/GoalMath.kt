package com.emotionalleaves.sprout.domain.goals

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class GoalProgress(
    val savedMinor: Long,
    val targetMinor: Long,
    /** 0–100 (capped). */
    val percent: Int,
    val remainingMinor: Long,
    /** What to set aside each month to reach the target by its date; null without a date or when done. */
    val perMonthMinor: Long?,
    /** Whole months left until the target date (at least 1 while not past it). */
    val monthsLeft: Int?,
    val reached: Boolean,
    val overdue: Boolean,
)

object GoalMath {
    /**
     * Progress toward a goal. [savedMinor] is either the linked account's balance or the amount the user entered.
     * Per-month amounts round up to the next cent so following them always reaches the goal.
     */
    fun progress(savedMinor: Long, targetMinor: Long, today: LocalDate, targetDate: LocalDate?): GoalProgress {
        val saved = savedMinor.coerceAtLeast(0)
        val target = targetMinor.coerceAtLeast(1)
        val remaining = (target - saved).coerceAtLeast(0)
        val reached = remaining == 0L
        val percent = ((saved * 100) / target).coerceAtMost(100).toInt()
        val overdue = !reached && targetDate != null && targetDate.isBefore(today)
        val months = targetDate?.takeIf { !overdue }?.let { monthsBetween(today, it) }
        val perMonth = if (reached || months == null) null else (remaining + months - 1) / months
        return GoalProgress(saved, target, percent, remaining, perMonth, months, reached, overdue)
    }

    /** Months from [from] to [to], counting a partial month as a whole one, at least 1. */
    fun monthsBetween(from: LocalDate, to: LocalDate): Int {
        val whole = ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)).toInt()
        return (whole + if (to.dayOfMonth >= from.dayOfMonth) 1 else 0).coerceAtLeast(1)
    }
}
