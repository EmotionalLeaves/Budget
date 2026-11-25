package com.emotionalleaves.sprout.domain.insights

import com.emotionalleaves.sprout.domain.recurring.Cadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val nov = YearMonth.of(2026, 11)
    private fun cm(id: Long, m: YearMonth, v: Long) = CategoryMonth(id, if (id == 1L) "Eating Out" else "Groceries", m, v)

    @Test fun flagsCategoriesAboveUsualAndPraisesLess() {
        val rows = listOf(
            cm(1, nov, 40_000), cm(1, nov.minusMonths(1), 20_000), cm(1, nov.minusMonths(2), 22_000), cm(1, nov.minusMonths(3), 18_000),
            cm(2, nov, 10_000), cm(2, nov.minusMonths(1), 40_000), cm(2, nov.minusMonths(2), 38_000),
        )
        val out = InsightEngine.build(today, rows, emptyList(), 0, 0, 0, 0)
        assertEquals("over-1", out[0].key)
        assertEquals(1L, out[0].categoryId)
        assertTrue(out.any { it.key == "under-2" && it.tone == Tone.GOOD })
    }

    @Test fun smallChangesAreNoise() {
        val rows = listOf(cm(1, nov, 21_000), cm(1, nov.minusMonths(1), 20_000), cm(1, nov.minusMonths(2), 20_000))
        assertTrue(InsightEngine.build(today, rows, emptyList(), 0, 0, 0, 0).isEmpty())
    }

    @Test fun merchantsSubscriptionsAndSavingsRate() {
        val out = InsightEngine.build(
            today, emptyList(), listOf(MerchantSpend("Kwik Star", 6_000, 8), MerchantSpend("Target", 12_000, 2)),
            subscriptionsMonthlyMinor = 4_500, subscriptionCount = 3, incomeThisMonthMinor = 300_000, spentThisMonthMinor = 240_000,
        )
        val keys = out.map { it.key }
        assertTrue(keys.containsAll(listOf("merchant", "frequent", "subs", "savings-rate")))
        assertEquals("Keeping 20% of your income", out.first { it.key == "savings-rate" }.title)
        assertEquals("Most spent at Target", out.first { it.key == "merchant" }.title)
    }

    @Test fun monthlyEquivalents() {
        assertEquals(4_333L, InsightEngine.monthlyEquivalent(1_000, Cadence.WEEKLY))
        assertEquals(1_000L, InsightEngine.monthlyEquivalent(12_000, Cadence.YEARLY))
        assertEquals(1_000L, InsightEngine.monthlyEquivalent(3_000, Cadence.QUARTERLY))
    }

    @Test fun netWorthRebuildsBackwards() {
        val z = ZoneId.of("UTC")
        val txs = listOf(
            BalanceTx("A", 100_000, LocalDate.of(2026, 11, 1).atTime(12, 0).atZone(z).toEpochSecond()),
            BalanceTx("A", -30_000, LocalDate.of(2026, 10, 15).atTime(12, 0).atZone(z).toEpochSecond()),
            BalanceTx("B", -5_000, LocalDate.of(2026, 11, 20).atTime(12, 0).atZone(z).toEpochSecond()),
            BalanceTx("HIDDEN", 999_999, LocalDate.of(2026, 11, 20).atTime(12, 0).atZone(z).toEpochSecond()),
        )
        val points = NetWorthHistory.monthEnds(today, 3)
        assertEquals(listOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 31), today), points)
        val series = NetWorthHistory.compute(mapOf("A" to 200_000, "B" to 50_000), txs, points, z)
        // Today 250,000. End of Oct: minus Nov's +100,000 and −5,000 → 155,000. End of Sep: also minus Oct's −30,000 → 185,000.
        assertEquals(listOf(185_000L, 155_000L, 250_000L), series.map { it.second })
        assertEquals(LocalDate.of(2026, 10, 15), NetWorthHistory.earliest(txs, z))
    }
}

class RecapTest {
    @Test fun headlineAndChange() {
        val r = Recap(java.time.YearMonth.of(2026, 10), 200_000, 300_000, 250_000, listOf("Rent" to 120_000L), "Target" to 9_000L, 5, 42)
        assertEquals(100_000L, r.savedMinor)
        assertEquals(-20, r.changePercent)
        assertEquals("You kept 33% of what came in", r.headline())
        assertEquals("Spending was above income", r.copy(incomeMinor = 100_000).headline())
        assertEquals(null, r.copy(previousSpentMinor = null).changePercent)
    }
}
