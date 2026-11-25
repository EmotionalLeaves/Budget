package com.emotionalleaves.sprout.domain.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtPlannerTest {
    private val card = Debt("card", "Card", 500_000, 24.0, 15_000)
    private val car = Debt("car", "Car loan", 800_000, 6.0, 25_000)
    private val store = Debt("store", "Store card", 60_000, 29.0, 3_500)

    @Test fun avalanchePaysLessInterestSnowballWinsSooner() {
        val a = DebtPlanner.plan(listOf(card, car, store), 80_000, Strategy.AVALANCHE)
        val s = DebtPlanner.plan(listOf(card, car, store), 80_000, Strategy.SNOWBALL)
        assertTrue(a.months != null && s.months != null)
        assertTrue(a.totalInterestMinor <= s.totalInterestMinor)
        assertEquals("store", s.order.first().id)
        assertEquals("store", a.order.first().id) // highest APR too
        assertEquals(listOf("store", "card", "car"), a.order.map { it.id })
        // Everything paid = what was owed + interest (within rounding).
        assertTrue(kotlin.math.abs(a.totalPaidMinor - (1_360_000 + a.totalInterestMinor)) <= 3)
    }

    @Test fun zeroInterestIsSimpleDivision() {
        val p = DebtPlanner.plan(listOf(Debt("x", "X", 100_000, 0.0, 10_000)), 25_000, Strategy.AVALANCHE)
        assertEquals(4, p.months)
        assertEquals(0L, p.totalInterestMinor)
    }

    @Test fun paymentTooSmallNeverFinishes() {
        val p = DebtPlanner.plan(listOf(Debt("x", "X", 1_000_000, 30.0, 1_000)), 2_000, Strategy.AVALANCHE)
        assertNull(p.months)
        assertTrue(!p.belowMinimums)
        assertTrue(DebtPlanner.plan(listOf(card, car), 10_000, Strategy.SNOWBALL).belowMinimums)
    }

    @Test fun noDebt() = assertEquals(0, DebtPlanner.plan(emptyList(), 10_000, Strategy.SNOWBALL).months)
}
