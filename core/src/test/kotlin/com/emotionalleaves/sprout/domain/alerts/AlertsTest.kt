package com.emotionalleaves.sprout.domain.alerts

import com.emotionalleaves.sprout.domain.budget.BudgetCategory
import com.emotionalleaves.sprout.domain.budget.CategoryLine
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private val cat = BudgetCategory(7, "Eating Out", CategoryKind.EXPENSE)
    private fun line(spent: Long, budget: Long? = 10_000) = CategoryLine(cat, budget, spent)

    @Test fun below80Nothing() = assertTrue(BudgetAlerts.evaluate(listOf(line(7_999)), emptySet()).isEmpty())

    @Test fun crossing80Fires80() {
        val d = BudgetAlerts.evaluate(listOf(line(8_000)), emptySet()).single()
        assertEquals(80, d.threshold)
        assertEquals(listOf(80), d.record)
    }

    @Test fun eightyFiresOnlyOnce() = assertTrue(BudgetAlerts.evaluate(listOf(line(9_000)), setOf(7L to 80)).isEmpty())

    @Test fun bothCrossedByOneTransactionFiresOnly100AndRecordsBoth() {
        val d = BudgetAlerts.evaluate(listOf(line(12_000)), emptySet()).single()
        assertEquals(100, d.threshold)
        assertEquals(setOf(80, 100), d.record.toSet())
    }

    @Test fun hundredAfterEarlier80() {
        val d = BudgetAlerts.evaluate(listOf(line(10_000)), setOf(7L to 80)).single()
        assertEquals(100, d.threshold)
        assertEquals(listOf(100), d.record)
    }

    @Test fun hundredFiresOnlyOnce() = assertTrue(BudgetAlerts.evaluate(listOf(line(20_000)), setOf(7L to 80, 7L to 100)).isEmpty())

    @Test fun noBudgetNoAlert() = assertTrue(BudgetAlerts.evaluate(listOf(line(50_000, null)), emptySet()).isEmpty())

    @Test fun zeroBudgetNoAlert() = assertTrue(BudgetAlerts.evaluate(listOf(line(500, 0)), emptySet()).isEmpty())

    // Unusual detection
    private val big = UnusualDetector.DEFAULT_LARGE_THRESHOLD_MINOR

    @Test fun inflowsNeverAlert() = assertNull(UnusualDetector.check(100_000, emptyList(), big))

    @Test fun largeAtThreshold() = assertEquals(UnusualReason.LARGE, UnusualDetector.check(-25_000, listOf(-25_000, -25_000, -25_000), big))

    @Test fun justBelowLargeIsNotLarge() = assertNull(UnusualDetector.check(-24_999, listOf(-24_000, -24_000, -24_000), big))

    @Test fun unusualForMerchant() =
        assertEquals(UnusualReason.UNUSUAL_FOR_MERCHANT, UnusualDetector.check(-6_000, listOf(-2_000, -2_200, -1_800), big))

    @Test fun exactly2point5TimesMedianIsNotUnusual() = assertNull(UnusualDetector.check(-5_000, listOf(-2_000, -2_000, -2_000), big))

    @Test fun unusualNeedsAtLeast50Dollars() = assertNull(UnusualDetector.check(-4_900, listOf(-500, -600, -700), big))

    @Test fun unusualNeedsThreePriors() = assertNull(UnusualDetector.check(-9_000, listOf(-1_000, -1_000), big))

    @Test fun evenCountMedian() = // median 3,000 → more than 7,500 is unusual
        assertEquals(UnusualReason.UNUSUAL_FOR_MERCHANT, UnusualDetector.check(-7_600, listOf(-1_000, -2_000, -4_000, -5_000), big))

    @Test fun newMerchantAt150() = assertEquals(UnusualReason.NEW_MERCHANT, UnusualDetector.check(-15_000, emptyList(), big))

    @Test fun newMerchantBelow150() = assertNull(UnusualDetector.check(-14_999, emptyList(), big))

    @Test fun customThreshold() = assertEquals(UnusualReason.LARGE, UnusualDetector.check(-10_000, listOf(-10_000, -10_000, -10_000), 10_000))

    @Test fun refundPriorsCountByAbsoluteValue() = assertNull(UnusualDetector.check(-5_000, listOf(2_000, -2_000, -2_000), big))
}
