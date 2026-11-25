package com.emotionalleaves.sprout.domain.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One day of the month: what you spent (positive cents), and whether it's still to come. */
data class SpendDay(val date: LocalDate, val spentMinor: Long, val future: Boolean)

/** The month as a Sunday-first grid of weeks, with no-spend-day counts. */
data class SpendCalendar(val month: YearMonth, val weeks: List<List<SpendDay?>>, val noSpendDays: Int, val maxDayMinor: Long) {
    companion object {
        /** [rows] are (signed amount, epoch seconds) of everyday spending; refunds reduce a day's total. */
        fun of(month: YearMonth, today: LocalDate, rows: List<Pair<Long, Long>>, zone: ZoneId): SpendCalendar {
            val byDay = rows.groupBy { Instant.ofEpochSecond(it.second).atZone(zone).toLocalDate() }
                .mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.first }) }
            val days = (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                SpendDay(date, byDay[date] ?: 0L, date.isAfter(today))
            }
            val lead = (days.first().date.dayOfWeek.value % 7) // Sunday = 0
            val cells: List<SpendDay?> = List(lead) { null } + days
            val weeks = cells.chunked(7).map { w -> w + List(7 - w.size) { null } }
            val noSpend = days.count { !it.future && it.spentMinor == 0L }
            return SpendCalendar(month, weeks, noSpend, days.maxOf { it.spentMinor })
        }

        val headers = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    }
}
