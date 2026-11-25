package com.emotionalleaves.sprout.domain.merchant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MerchantStatsTest {
    private val z = ZoneId.of("UTC")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).atZone(z).toEpochSecond()

    @Test fun totalsAveragesAndMonths() {
        val txs = listOf(
            MerchantTx(-4_000, at(2026, 11, 3)), MerchantTx(-6_000, at(2026, 11, 10)), MerchantTx(1_000, at(2026, 11, 12)),
            MerchantTx(-2_000, at(2026, 9, 1)), MerchantTx(-3_000, at(2025, 12, 30)),
        )
        val s = MerchantStats.of(txs, YearMonth.of(2026, 11), z)
        assertEquals(4, s.visits)
        assertEquals(14_000L, s.totalSpentMinor)
        assertEquals(3_750L, s.averageMinor)
        assertEquals(11_000L, s.thisYearMinor)
        assertEquals(6, s.byMonth.size)
        assertEquals(9_000L, s.byMonth.last().second)
        assertEquals(2_000L, s.byMonth.first { it.first.monthValue == 9 }.second)
        assertEquals(at(2025, 12, 30), s.firstAt)
    }

    @Test fun empty() {
        val s = MerchantStats.of(emptyList(), YearMonth.of(2026, 11), z)
        assertEquals(0, s.visits)
        assertEquals(0L, s.averageMinor)
        assertEquals(null, s.lastAt)
    }
}
