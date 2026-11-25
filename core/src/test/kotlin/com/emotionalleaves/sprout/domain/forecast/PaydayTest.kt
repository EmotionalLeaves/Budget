package com.emotionalleaves.sprout.domain.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaydayTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun billsBeforePaydayComeOffTheTop() {
        val p = PaydayPlan.compute(
            today, 100_000,
            income = listOf(Expected("Acme", LocalDate.of(2026, 12, 5), 150_000), Expected("Acme", LocalDate.of(2026, 11, 21), 150_000)),
            bills = listOf(
                Expected("Phone", LocalDate.of(2026, 11, 28), -6_000),
                Expected("Rent", LocalDate.of(2026, 12, 1), -80_000),
                Expected("Gym", LocalDate.of(2026, 12, 5), -3_000), // payday itself: next paycheck covers it
            ),
        )!!
        assertEquals(LocalDate.of(2026, 12, 5), p.payday)
        assertEquals(11, p.daysUntil)
        assertEquals(86_000, p.billsMinor)
        assertEquals(14_000, p.leftMinor)
        assertEquals(1_272, p.perDayMinor)
        assertEquals(listOf("Phone", "Rent"), p.bills.map { it.name })
    }

    @Test fun youCanPickWhichIncomeIsPayday() {
        val income = listOf(Expected("Side gig", today.plusDays(2), 5_000), Expected("Acme", today.plusDays(10), 150_000))
        assertEquals("Side gig", PaydayPlan.compute(today, 1_000, income, emptyList())!!.payName)
        val p = PaydayPlan.compute(today, 1_000, income, emptyList(), preferred = "Acme")!!
        assertEquals("Acme", p.payName)
        assertEquals(listOf("Side gig", "Acme"), p.sources)
        assertEquals("Side gig", PaydayPlan.compute(today, 1_000, income, emptyList(), preferred = "Gone")!!.payName)
    }

    @Test fun shortWhenBillsExceedCash() {
        val p = PaydayPlan.compute(today, 10_000, listOf(Expected("Pay", today.plusDays(3), 50_000)), listOf(Expected("Rent", today.plusDays(1), -20_000)))!!
        assertTrue(p.short)
        assertEquals(0, p.perDayMinor)
    }

    @Test fun nothingToPlanWithoutIncomeOrCash() {
        assertNull(PaydayPlan.compute(today, 10_000, emptyList(), emptyList()))
        assertNull(PaydayPlan.compute(today, null, listOf(Expected("Pay", today.plusDays(3), 1)), emptyList()))
    }
}
