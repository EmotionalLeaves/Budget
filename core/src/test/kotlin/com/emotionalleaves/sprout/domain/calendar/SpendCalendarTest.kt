package com.emotionalleaves.sprout.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SpendCalendarTest {
    private val z = ZoneId.of("UTC")
    private fun at(d: Int) = LocalDate.of(2026, 11, d).atTime(12, 0).atZone(z).toEpochSecond()

    @Test fun gridAndNoSpendDays() {
        // November 2026 starts on a Sunday.
        val c = SpendCalendar.of(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 10),
            listOf(-1_000L to at(1), -500L to at(1), 200L to at(1), -3_000L to at(5)), z)
        assertEquals(LocalDate.of(2026, 11, 1), c.weeks[0][0]!!.date)
        assertEquals(1_300L, c.weeks[0][0]!!.spentMinor)
        assertEquals(5, c.weeks.size)
        assertNull(c.weeks.last().last())
        assertEquals(8, c.noSpendDays) // days 1..10 minus days 1 and 5
        assertEquals(3_000L, c.maxDayMinor)
        assertEquals(true, c.weeks[1][4]!!.future) // Nov 12
    }

    @Test fun leadingBlanksForAMidweekStart() {
        val c = SpendCalendar.of(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 1), emptyList(), z)
        assertNull(c.weeks[0][0]) // Dec 1 2026 is a Tuesday
        assertNull(c.weeks[0][1])
        assertEquals(1, c.weeks[0][2]!!.date.dayOfMonth)
    }
}
