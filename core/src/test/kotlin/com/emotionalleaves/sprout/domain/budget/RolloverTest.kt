package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class RolloverTest {
    private val nov = YearMonth.of(2026, 11)

    @Test fun nothingCarriesInTheFirstMonth() =
        assertEquals(0L, BudgetMath.rolloverCarry(nov, nov, { 20_000 }, { 5_000 }))

    @Test fun unspentMoneyAccumulates() {
        val spent = mapOf(YearMonth.of(2026, 9) to 15_000L, YearMonth.of(2026, 10) to 18_000L)
        assertEquals(7_000L, BudgetMath.rolloverCarry(YearMonth.of(2026, 9), nov, { 20_000 }, { spent[it] ?: 0 }))
    }

    @Test fun overspendingReducesTheNextMonth() =
        assertEquals(-5_000L, BudgetMath.rolloverCarry(YearMonth.of(2026, 10), nov, { 20_000 }, { 25_000 }))

    @Test fun monthsWithoutABudgetAddNothing() {
        val budget = { m: YearMonth -> if (m == YearMonth.of(2026, 9)) null else 10_000L }
        assertEquals(2_000L, BudgetMath.rolloverCarry(YearMonth.of(2026, 9), nov, budget, { 8_000 }))
    }

    @Test fun usesThatMonthsOverride() {
        val budget = { m: YearMonth -> if (m == YearMonth.of(2026, 10)) 30_000L else 20_000L }
        assertEquals(10_000L, BudgetMath.rolloverCarry(YearMonth.of(2026, 10), nov, budget, { 20_000 }))
    }

    private val cat = BudgetCategory(1, "Groceries", CategoryKind.EXPENSE)
    private fun month(carries: Map<Long, Long>, spent: Long = 0) = BudgetMath.computeFromSums(
        nov, ZoneId.of("UTC"), LocalDate.of(2026, 11, 10), listOf(cat), mapOf(1L to 20_000L), emptyMap(),
        mapOf(1L to -spent), carries = carries,
    )

    @Test fun carryAddsToTheBudgetAndSafeToSpend() {
        val m = month(mapOf(1L to 5_000L), spent = 10_000)
        val line = m.budgeted.single()
        assertEquals(25_000L, line.budgetMinor)
        assertEquals(20_000L, line.baseBudgetMinor)
        assertEquals(5_000L, line.carryMinor)
        assertTrue(line.rolloverOn)
        assertEquals(15_000L, line.remainingMinor)
        assertEquals(15_000L, m.safeToSpend!!.amountMinor)
    }

    @Test fun bigOverspendingKeepsTheBudgetAtZero() {
        val line = month(mapOf(1L to -30_000L)).budgeted.single()
        assertEquals(0L, line.budgetMinor)
        assertEquals(-30_000L, line.carryMinor)
    }

    @Test fun withoutRolloverNothingChanges() {
        val line = month(emptyMap()).budgeted.single()
        assertEquals(20_000L, line.budgetMinor)
        assertEquals(0L, line.carryMinor)
        assertFalse(line.rolloverOn)
    }
}
