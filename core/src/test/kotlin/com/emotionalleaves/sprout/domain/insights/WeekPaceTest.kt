package com.emotionalleaves.sprout.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekPaceTest {
    private val today = LocalDate.of(2026, 11, 26) // Thursday; week started Monday Nov 23
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test fun comparesLikeForLike() {
        val rows = listOf(
            d(11, 23) to 1_000L, d(11, 26) to 500L,           // this week: 15.00 so far
            d(11, 16) to 2_000L, d(11, 21) to 9_000L,         // last week: Mon 20 by Thu, 110 whole
            d(11, 10) to 4_000L,                              // two weeks ago: Tue 40 by Thu, 40 whole
        )
        val p = WeekPace.of(rows, today, d(11, 1))
        assertEquals(1_500L, p.soFarMinor)
        // Weeks of Nov 16, 9 and 2 (Oct 26 is before the first day): (20 + 40 + 0) / 3. The Saturday 90 isn't "by Thursday".
        assertEquals(3, p.weeksCompared)
        assertEquals(2_000L, p.usualByNowMinor)
        assertEquals((11_000L + 4_000L) / 3, p.usualWeekMinor)
    }

    @Test fun needsTwoFullWeeksOfHistory() {
        val p = WeekPace.of(listOf(d(11, 24) to 700L), today, d(11, 12))
        assertEquals(700L, p.soFarMinor)
        assertNull(p.usualByNowMinor)
        assertNull(WeekPace.of(emptyList(), today, null).usualWeekMinor)
    }
}
