package com.emotionalleaves.sprout.domain.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurringDetectorTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(date: LocalDate) = date.atTime(10, 0).atZone(zone).toEpochSecond()
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun c(date: LocalDate, amount: Long, merchant: String = "NETFLIX") = ChargeTx(merchant, -amount, at(date), 9, "acct")

    private fun detect(charges: List<ChargeTx>, now: LocalDate) = RecurringDetector.detect(charges, at(now), zone)
    private fun day(epoch: Long) = java.time.Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()

    @Test fun monthly() {
        val r = detect((6..10).map { c(d(2026, it, 15), 1549) }, d(2026, 11, 2)).single()
        assertEquals(Cadence.MONTHLY, r.cadence)
        assertEquals(1549L, r.typicalAmountMinor)
        assertEquals(d(2026, 11, 15), day(r.nextDueAt))
        assertEquals(5, r.occurrences)
        assertTrue(r.confidence > 0.9f)
    }

    @Test fun weekly() {
        val start = d(2026, 9, 7)
        val r = detect((0..8).map { c(start.plusWeeks(it.toLong()), 1000, "GYM CLASS") }, start.plusWeeks(8).plusDays(2)).single()
        assertEquals(Cadence.WEEKLY, r.cadence)
        assertEquals(start.plusWeeks(9), day(r.nextDueAt))
    }

    @Test fun biweekly() {
        val start = d(2026, 6, 5)
        val r = detect((0..6).map { c(start.plusWeeks(2L * it), 4000, "DAYCARE") }, d(2026, 9, 5)).single()
        assertEquals(Cadence.BIWEEKLY, r.cadence)
    }

    @Test fun quarterly() {
        val r = detect(listOf(c(d(2026, 1, 10), 9000), c(d(2026, 4, 10), 9000), c(d(2026, 7, 10), 9000), c(d(2026, 10, 10), 9000)), d(2026, 11, 1)).single()
        assertEquals(Cadence.QUARTERLY, r.cadence)
        assertEquals(d(2027, 1, 10), day(r.nextDueAt))
    }

    @Test fun yearlyWithTwoCharges() {
        val r = detect(listOf(c(d(2025, 12, 1), 13900, "AMAZON PRIME"), c(d(2026, 11, 28), 13900, "AMAZON PRIME")), d(2026, 11, 30)).single()
        assertEquals(Cadence.YEARLY, r.cadence)
        assertEquals(d(2027, 11, 28), day(r.nextDueAt))
    }

    @Test fun twoChargesNotAYearApartAreRejected() =
        assertTrue(detect(listOf(c(d(2026, 9, 1), 1000), c(d(2026, 10, 1), 1000)), d(2026, 10, 5)).isEmpty())

    @Test fun irregularIsRejected() {
        val dates = listOf(d(2026, 3, 1), d(2026, 3, 19), d(2026, 5, 2), d(2026, 5, 9), d(2026, 8, 27))
        assertTrue(detect(dates.map { c(it, 2000, "HARDWARE STORE") }, d(2026, 9, 1)).isEmpty())
    }

    @Test fun amountDriftBeyondToleranceIsRejected() {
        val amounts = listOf(2000L, 3000L, 4500L, 6000L, 8000L)
        assertTrue(detect(amounts.mapIndexed { i, a -> c(d(2026, 6 + i, 3), a, "UTILITY CO") }, d(2026, 11, 1)).isEmpty())
    }

    @Test fun smallVariationWithinTolerance() {
        val amounts = listOf(8_200L, 9_100L, 8_700L, 9_400L) // ±15% of median
        val r = detect(amounts.mapIndexed { i, a -> c(d(2026, 7 + i, 20), a, "ELECTRIC") }, d(2026, 11, 1)).single()
        assertEquals(Cadence.MONTHLY, r.cadence)
    }

    @Test fun priceIncreaseIsStillDetectedAndUsesNewPrice() {
        val r = detect(
            listOf(c(d(2026, 5, 8), 1549), c(d(2026, 6, 8), 1549), c(d(2026, 7, 8), 1549), c(d(2026, 8, 8), 1799), c(d(2026, 9, 8), 1799)),
            d(2026, 9, 20),
        ).single()
        assertEquals(1799L, r.typicalAmountMinor)
    }

    @Test fun endOfMonthJan31GoesToFeb28() =
        assertEquals(d(2027, 2, 28), Schedule.next(d(2027, 1, 31), Cadence.MONTHLY))

    @Test fun endOfMonthJan31GoesToFeb29InLeapYear() =
        assertEquals(d(2028, 2, 29), Schedule.next(d(2028, 1, 31), Cadence.MONTHLY))

    @Test fun endOfMonthAnchorSurvivesFebruary() {
        val r = detect(listOf(c(d(2026, 12, 31), 500), c(d(2027, 1, 31), 500), c(d(2027, 2, 28), 500)), d(2027, 3, 2)).single()
        assertEquals(Cadence.MONTHLY, r.cadence)
        assertEquals(d(2027, 3, 31), day(r.nextDueAt))
    }

    @Test fun stoppedSubscriptionIsDropped() =
        assertTrue(detect((1..4).map { c(d(2026, it, 5), 999) }, d(2026, 11, 1)).isEmpty())

    @Test fun refundsAndInflowsIgnored() {
        val charges = (6..10).map { c(d(2026, it, 15), 1549) } + ChargeTx("NETFLIX", 1549, at(d(2026, 10, 16)), 9, "acct")
        assertEquals(1, detect(charges, d(2026, 11, 1)).size)
    }

    @Test fun oldChargesOutsideLookbackIgnored() {
        val old = (1..3).map { c(d(2024, it, 1), 1000, "OLD") }
        assertTrue(detect(old, d(2026, 11, 1)).isEmpty())
    }

    @Test fun occurrencesForWeeklyInAMonth() {
        val occ = Schedule.occurrences(d(2026, 11, 3), Cadence.WEEKLY, d(2026, 11, 1), d(2026, 12, 1))
        assertEquals(listOf(3, 10, 17, 24), occ.map { it.dayOfMonth })
    }

    @Test fun toleranceRule() {
        assertTrue(RecurringDetector.withinTolerance(1_299, 999)) // $3 rule
        assertTrue(RecurringDetector.withinTolerance(11_500, 10_000)) // 15% rule
        assertTrue(!RecurringDetector.withinTolerance(11_600, 10_000))
        assertNull(null)
    }

    @Test fun calendarGridStartsOnSunday() {
        val cells = CalendarGrid.cells(java.time.YearMonth.of(2026, 11)) // Nov 1, 2026 is a Sunday
        assertEquals(d(2026, 11, 1), cells.first())
        assertEquals(35, cells.size)
        val feb = CalendarGrid.cells(java.time.YearMonth.of(2027, 2)) // Feb 1, 2027 is a Monday
        assertNull(feb[0])
        assertEquals(d(2027, 2, 1), feb[1])
        assertEquals(0, feb.size % 7)
    }
}
