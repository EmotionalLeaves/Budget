package com.emotionalleaves.sprout.domain.insights

import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.insights.SpendingStats.Row
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SpendingStatsTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toEpochSecond()
    private fun spend(d: LocalDate, cents: Long, m: String = "SHOP", cat: Long = 1) = Row(-cents, at(d), cat, CategoryKind.EXPENSE, m)
    private val nov = LocalDate.of(2026, 11, 1)

    @Test fun summarizesAMonthInProgress() {
        val rows = listOf(
            spend(nov, 5_00, "CAFE"), spend(nov, 3_00, "CAFE"), spend(nov.plusDays(1), 120_00, "HYVEE"),
            spend(nov.plusDays(12), 250_00, "BESTBUY"),
            Row(2_000_00, at(nov.plusDays(14)), 9, CategoryKind.INCOME, "PAYROLL"),
            Row(-500_00, at(nov.plusDays(3)), 7, CategoryKind.TRANSFER, "SAVINGS"),
        )
        val s = SpendingStats.summarize(rows, nov, nov.plusMonths(1), today = nov.plusDays(19), zone = zone)
        assertEquals(378_00, s.spent)
        assertEquals(2_000_00, s.earned)
        assertEquals(20, s.days)
        assertEquals(378_00 / 20, s.dailyAverage)
        assertEquals(4, s.purchases)
        assertEquals(17, s.noSpendDays)
        assertEquals(81, s.savingsRate)
        assertEquals("BESTBUY", s.biggest?.merchantKey)
        assertEquals(listOf(2, 0, 1, 1), s.sizes.map { it.count })
        assertEquals("CAFE", s.topByVisits.first().merchantKey)
        assertEquals("BESTBUY", s.topByTotal.first().merchantKey)
        assertEquals(DayOfWeek.FRIDAY, s.busiestWeekday) // Nov 13, 2026 is a Friday
        assertEquals(listOf(128_00L, 250_00L, 0L), s.byMonthPart)
        assertEquals(378_00, s.cumulative.last())
    }

    @Test fun categoryTrendsByMonth() {
        val rows = listOf(spend(nov, 10_00, cat = 3), spend(nov.minusMonths(2), 20_00, cat = 3), spend(nov.minusMonths(9), 99_00, cat = 3))
        assertEquals(mapOf(3L to listOf(20_00L, 0L, 10_00L)), SpendingStats.categoryTrends(rows, YearMonth.of(2026, 11), 3, zone))
    }

    @Test fun change() {
        assertEquals(50, SpendingStats.changePercent(100, 150))
        assertEquals(null, SpendingStats.changePercent(0, 150))
    }

    @Test fun heatmapAndInOut() {
        val rows = listOf(spend(nov, 10_00), spend(nov.plusDays(2), 5_00), Row(100_00, at(nov.plusDays(1)), 9, CategoryKind.INCOME, "PAY"))
        assertEquals(listOf(10_00L, 0L, 5_00L), SpendingStats.dailySpending(rows, nov.plusDays(2), 3, zone))
        assertEquals(listOf(0L to 0L, 100_00L to 15_00L), SpendingStats.monthlyInOut(rows, YearMonth.of(2026, 11), 2, zone))
    }
}
