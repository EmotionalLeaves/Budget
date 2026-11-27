package com.emotionalleaves.sprout.domain.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * This week so far against your usual week, compared like for like: what earlier weeks had spent by the same
 * weekday. Weeks run Monday to Sunday. Only full weeks after your first day of data count as "usual".
 */
data class WeekPace(
    val soFarMinor: Long,
    /** What a usual week had spent by this weekday; null without at least two earlier weeks. */
    val usualByNowMinor: Long?,
    val usualWeekMinor: Long?,
    val weeksCompared: Int,
) {
    companion object {
        const val MAX_WEEKS = 8

        /** [spending] is (day, positive cents) for counted spending; [firstDay] is your first day of data. */
        fun of(spending: List<Pair<LocalDate, Long>>, today: LocalDate, firstDay: LocalDate?): WeekPace {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val daysIn = ChronoUnit.DAYS.between(monday, today).toInt() // 0 on Monday
            val soFar = spending.filter { it.first in monday..today }.sumOf { it.second }
            val weeks = (1..MAX_WEEKS).map { monday.minusWeeks(it.toLong()) }
                .filter { firstDay != null && !it.isBefore(firstDay) }
            if (weeks.size < 2) return WeekPace(soFar, null, null, weeks.size)
            val byNow = weeks.map { w -> spending.filter { it.first >= w && it.first <= w.plusDays(daysIn.toLong()) }.sumOf { it.second } }
            val whole = weeks.map { w -> spending.filter { it.first >= w && it.first < w.plusDays(7) }.sumOf { it.second } }
            return WeekPace(soFar, byNow.sum() / weeks.size, whole.sum() / weeks.size, weeks.size)
        }
    }
}
