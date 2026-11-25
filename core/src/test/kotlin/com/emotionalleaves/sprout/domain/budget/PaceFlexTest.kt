package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.insights.AgeOfMoney
import com.emotionalleaves.sprout.domain.insights.PlannedExpense
import com.emotionalleaves.sprout.domain.insights.PriceChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaceFlexTest {
    @Test fun paceProjectsTheMonth() {
        val p = Pace.of(15_000, 30_000, 10, 30)
        assertEquals(45_000L, p.projectedMinor)
        assertEquals(15_000L, p.overByMinor)
        assertFalse(p.onTrack)
        assertTrue(Pace.of(5_000, 30_000, 2, 30).tooEarly)
        assertTrue(Pace.of(5_000, 30_000, 2, 30).onTrack)
        assertEquals(20_000L, Pace.of(20_000, null, 30, 30).projectedMinor)
    }

    @Test fun flexMonth() {
        val f = FlexMonth.of(60_000, mapOf(1L to -120_000, 2L to -20_000, 3L to -10_000, 4L to 500), setOf(1L), 150_000, 5_000, 10)
        assertEquals(30_000L, f.flexSpentMinor) // 4L is a refund: 0
        assertEquals(120_000L, f.fixedSpentMinor)
        assertEquals(30_000L, f.flexLeftMinor)
        assertEquals(25_000L, f.safeMinor)
        assertEquals(2_500L, f.perDayMinor)
    }

    @Test fun ageOfMoneyFifo() {
        val day = 86_400L
        // Paid 1,000 on day 0 and day 30; spending 100 every 3 days from day 20 to day 47.
        val moves = listOf(100_000L to 0L, 100_000L to 30 * day) + (0 until 10).map { -10_000L to (20 + it * 3) * day }
        val age = AgeOfMoney.days(moves)!!
        assertTrue(age in 25..40)
        assertNull(AgeOfMoney.days(listOf(-100L to 0L)))
    }

    @Test fun plannedSetAside() {
        val today = LocalDate.of(2026, 11, 24)
        assertEquals(3_000L, PlannedExpense.monthlySetAside(24_000, 0, LocalDate.of(2027, 6, 1), today)) // 8 months incl. Nov
        assertEquals(24_000L, PlannedExpense.monthlySetAside(24_000, 0, LocalDate.of(2026, 10, 1), today)) // overdue: all now
        assertEquals(0L, PlannedExpense.monthlySetAside(24_000, 30_000, LocalDate.of(2027, 6, 1), today))
    }

    @Test fun priceChanges() {
        assertEquals(PriceChange("Netflix", 1_549, 1_799), PriceChange.detect("Netflix", listOf(1_549, 1_549, 1_799)))
        assertNull(PriceChange.detect("Spotify", listOf(1_199, 1_201)))
        assertNull(PriceChange.detect("X", listOf(1_000)))
        assertFalse(PriceChange.detect("Gym", listOf(4_000, 3_000))!!.up)
    }
}
