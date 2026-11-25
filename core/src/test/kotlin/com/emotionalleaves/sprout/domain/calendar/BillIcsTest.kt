package com.emotionalleaves.sprout.domain.calendar

import com.emotionalleaves.sprout.domain.recurring.Cadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillIcsTest {
    @Test fun buildsRepeatingAllDayEvents() {
        val ics = BillIcs.build(
            listOf(
                BillIcs.Bill(1, "Netflix, Inc; HD", "$15.49", LocalDate.of(2026, 12, 3), Cadence.MONTHLY),
                BillIcs.Bill(2, "Rent", "$800.00", LocalDate.of(2026, 12, 31), Cadence.MONTHLY),
                BillIcs.Bill(3, "Gym", "$10.00", LocalDate.of(2026, 12, 1), Cadence.BIWEEKLY),
            ),
            stamp = "20261124T120000Z",
        )
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue("DTSTART;VALUE=DATE:20261203" in ics)
        assertTrue("SUMMARY:Netflix\\, Inc\\; HD · $15.49" in ics)
        assertTrue("RRULE:FREQ=MONTHLY;BYMONTHDAY=-1" in ics)
        assertTrue("RRULE:FREQ=WEEKLY;INTERVAL=2" in ics)
    }

    @Test fun escapeDropsControlCharacters() {
        assertEquals("AB", BillIcs.escape("A\nB"))
    }
}
