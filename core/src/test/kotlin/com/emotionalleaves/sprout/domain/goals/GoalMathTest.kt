package com.emotionalleaves.sprout.domain.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalMathTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun progressAndPerMonth() {
        val p = GoalMath.progress(25_000, 100_000, today, LocalDate.of(2027, 5, 31))
        assertEquals(25, p.percent)
        assertEquals(75_000L, p.remainingMinor)
        assertEquals(7, p.monthsLeft) // Nov (rest), Dec, Jan, Feb, Mar, Apr, May
        assertEquals(10_715L, p.perMonthMinor) // 75,000 / 7 rounded up
    }

    @Test fun noDateNoPerMonth() {
        val p = GoalMath.progress(10_000, 50_000, today, null)
        assertNull(p.perMonthMinor)
        assertNull(p.monthsLeft)
    }

    @Test fun reachedCapsAt100() {
        val p = GoalMath.progress(120_000, 100_000, today, LocalDate.of(2027, 1, 1))
        assertTrue(p.reached)
        assertEquals(100, p.percent)
        assertEquals(0L, p.remainingMinor)
        assertNull(p.perMonthMinor)
    }

    @Test fun overdue() {
        val p = GoalMath.progress(1_000, 100_000, today, LocalDate.of(2026, 10, 1))
        assertTrue(p.overdue)
        assertNull(p.perMonthMinor)
    }

    @Test fun dueThisMonthIsOneMonth() {
        val p = GoalMath.progress(0, 30_000, today, LocalDate.of(2026, 11, 30))
        assertEquals(1, p.monthsLeft)
        assertEquals(30_000L, p.perMonthMinor)
        assertFalse(p.overdue)
    }

    @Test fun negativeBalanceCountsAsZero() = assertEquals(0, GoalMath.progress(-5_000, 10_000, today, null).percent)

    @Test fun monthsBetweenPartialMonths() {
        assertEquals(1, GoalMath.monthsBetween(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 10)))
        assertEquals(2, GoalMath.monthsBetween(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 24)))
    }
}
