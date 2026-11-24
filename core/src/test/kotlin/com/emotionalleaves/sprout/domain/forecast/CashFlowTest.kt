package com.emotionalleaves.sprout.domain.forecast

import com.emotionalleaves.sprout.domain.recurring.ChargeTx
import com.emotionalleaves.sprout.domain.recurring.Cadence
import com.emotionalleaves.sprout.domain.recurring.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CashFlowTest {
    private val today = LocalDate.of(2026, 11, 21) // 10 days left including today
    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    @Test fun simpleProjection() {
        val f = CashFlow.forecast(today, 100_000, listOf(Expected("Payroll", d(27), 185_000)), listOf(Expected("Rent", d(30), 95_000)), 20_000, 0)
        assertEquals(100_000 + 185_000 - 95_000 - 20_000L, f.endMinor)
        assertEquals("Payroll", f.nextIncome!!.name)
        assertTrue(f.spendingFromBudgets)
    }

    @Test fun lowPointBeforePayday() {
        val f = CashFlow.forecast(today, 50_000, listOf(Expected("Payroll", d(28), 185_000)), listOf(Expected("Rent", d(22), 95_000)), 0, 0)
        assertEquals(-45_000L, f.lowMinor)
        assertEquals(d(22), f.lowDate)
        assertEquals(140_000L, f.endMinor)
    }

    @Test fun pastAndNextMonthItemsIgnored() {
        val f = CashFlow.forecast(today, 10_000, listOf(Expected("Old pay", d(5), 99_999)), listOf(Expected("Next rent", LocalDate.of(2026, 12, 1), 95_000)), 0, 0)
        assertEquals(10_000L, f.endMinor)
        assertNull(f.nextIncome)
    }

    @Test fun withoutBudgetsUsesRecentAverage() {
        val f = CashFlow.forecast(today, 100_000, emptyList(), emptyList(), null, 30_000)
        assertEquals(10_000L, f.spendingMinor) // $300 over 30 days → $100 for 10 days
        assertEquals(90_000L, f.endMinor)
        assertTrue(!f.spendingFromBudgets)
    }

    @Test fun overspentBudgetsDontAddMoney() {
        val f = CashFlow.forecast(today, 1_000, emptyList(), emptyList(), -5_000, 0)
        assertEquals(0L, f.spendingMinor)
        assertEquals(1_000L, f.endMinor)
    }

    @Test fun lastDayOfMonth() {
        val f = CashFlow.forecast(d(30), 5_000, emptyList(), listOf(Expected("Rent", d(30), 4_000)), 700, 0)
        assertEquals(300L, f.endMinor)
    }

    @Test fun trendPercent() {
        assertEquals(25, CashFlow.Trend(12_500, 10_000).percent)
        assertEquals(-50, CashFlow.Trend(5_000, 10_000).percent)
        assertNull(CashFlow.Trend(5_000, 0).percent)
    }

    @Test fun biweeklyPaycheckDetectedAsIncome() {
        val zone = ZoneId.of("America/Chicago")
        val start = LocalDate.of(2026, 8, 7)
        val pays = (0..7).map { ChargeTx("ACME PAYROLL", 185_000, start.plusWeeks(2L * it).atTime(9, 0).atZone(zone).toEpochSecond(), 1, "a") }
        val r = RecurringDetector.detectIncome(pays, LocalDate.of(2026, 11, 21).atStartOfDay(zone).toEpochSecond(), zone).single()
        assertEquals(Cadence.BIWEEKLY, r.cadence)
        assertEquals(185_000L, r.typicalAmountMinor)
    }
}
