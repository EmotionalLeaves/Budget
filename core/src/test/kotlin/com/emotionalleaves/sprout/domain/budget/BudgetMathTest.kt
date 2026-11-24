package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class BudgetMathTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val nov = YearMonth.of(2026, 11)
    private val groceries = BudgetCategory(1, "Groceries", CategoryKind.EXPENSE)
    private val eating = BudgetCategory(2, "Eating Out", CategoryKind.EXPENSE)
    private val subs = BudgetCategory(3, "Subscriptions", CategoryKind.EXPENSE)
    private val income = BudgetCategory(4, "Income", CategoryKind.INCOME)
    private val transfers = BudgetCategory(5, "Transfers", CategoryKind.TRANSFER)
    private val cats = listOf(groceries, eating, subs, income, transfers)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, zone: ZoneId = chicago) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toEpochSecond()

    private fun tx(cat: BudgetCategory, amount: Long, day: Int = 10, excluded: Boolean = false) =
        BudgetTx(cat.id, amount, at(2026, 11, day), excluded)

    private fun compute(
        txs: List<BudgetTx>,
        defaults: Map<Long, Long> = mapOf(1L to 40_000L, 2L to 15_000L),
        overrides: Map<Long, Long> = emptyMap(),
        today: LocalDate = LocalDate.of(2026, 11, 24),
        charges: List<ExpectedCharge> = emptyList(),
        month: YearMonth = nov,
        zone: ZoneId = chicago,
    ) = BudgetMath.compute(month, zone, today, cats, defaults, overrides, txs, charges)

    @Test fun spentAndRemaining() {
        val m = compute(listOf(tx(groceries, -12_000), tx(groceries, -8_000)))
        val g = m.budgeted.first { it.category == groceries }
        assertEquals(20_000L, g.spentMinor)
        assertEquals(20_000L, g.remainingMinor)
        assertEquals(50, g.percentUsed)
        assertEquals(ProgressLevel.GREEN, g.level)
    }

    @Test fun refundsReduceSpending() {
        val g = compute(listOf(tx(groceries, -12_000), tx(groceries, 2_000))).budgeted.first { it.category == groceries }
        assertEquals(10_000L, g.spentMinor)
    }

    @Test fun refundsLargerThanSpendingNeverGoNegative() {
        val g = compute(listOf(tx(groceries, 5_000))).budgeted.first { it.category == groceries }
        assertEquals(0L, g.spentMinor)
    }

    @Test fun overspendingIsRedAndNegativeRemaining() {
        val e = compute(listOf(tx(eating, -18_000))).budgeted.first { it.category == eating }
        assertEquals(-3_000L, e.remainingMinor)
        assertEquals(120, e.percentUsed)
        assertEquals(ProgressLevel.RED, e.level)
    }

    @Test fun amberFrom80Percent() {
        val e = compute(listOf(tx(eating, -12_000))).budgeted.first { it.category == eating }
        assertEquals(ProgressLevel.AMBER, e.level)
        val e2 = compute(listOf(tx(eating, -11_999))).budgeted.first { it.category == eating }
        assertEquals(ProgressLevel.GREEN, e2.level)
    }

    @Test fun exactly100PercentIsRed() {
        val e = compute(listOf(tx(eating, -15_000))).budgeted.first { it.category == eating }
        assertEquals(ProgressLevel.RED, e.level)
        assertEquals(0L, e.remainingMinor)
    }

    @Test fun noBudgetsMeansNoSafeToSpend() {
        val m = compute(listOf(tx(groceries, -1_000)), defaults = emptyMap())
        assertNull(m.safeToSpend)
        assertEquals(listOf(groceries), m.unbudgetedWithSpending.map { it.category })
    }

    @Test fun monthWithNoTransactions() {
        val m = compute(emptyList())
        assertEquals(55_000L, m.safeToSpend!!.amountMinor)
        assertEquals(0L, m.totalSpentMinor)
        assertTrue(m.unbudgetedWithSpending.isEmpty())
    }

    @Test fun transfersAndIncomeNeverCountAsSpending() {
        val m = compute(listOf(tx(transfers, -50_000), tx(income, -1_000), tx(income, 200_000)))
        assertEquals(0L, m.totalSpentMinor)
        assertEquals(55_000L, m.safeToSpend!!.amountMinor)
    }

    @Test fun excludedTransactionsDontCount() {
        val g = compute(listOf(tx(groceries, -30_000, excluded = true))).budgeted.first { it.category == groceries }
        assertEquals(0L, g.spentMinor)
    }

    @Test fun overrideBeatsDefault() {
        val g = compute(emptyList(), overrides = mapOf(1L to 60_000L)).budgeted.first { it.category == groceries }
        assertEquals(60_000L, g.budgetMinor)
    }

    @Test fun overrideCanBudgetACategoryWithoutDefault() {
        val m = compute(emptyList(), overrides = mapOf(3L to 2_000L))
        assertTrue(m.budgeted.any { it.category == subs })
    }

    @Test fun safeToSpendSubtractsUnmatchedBillsInUnbudgetedCategories() {
        val charges = listOf(
            ExpectedCharge("Netflix", subs.id, 1_549, at(2026, 11, 28), matchedThisMonth = false),
            ExpectedCharge("Spotify", subs.id, 1_199, at(2026, 11, 26), matchedThisMonth = true),
            // In a budgeted category: already part of that budget, so not subtracted again.
            ExpectedCharge("Costco", groceries.id, 10_000, at(2026, 11, 27), matchedThisMonth = false),
            // Next month: not before month end.
            ExpectedCharge("Hulu", subs.id, 999, at(2026, 12, 2), matchedThisMonth = false),
            // Already in the past this month and unmatched: not "expected before month end" any more.
            ExpectedCharge("Old", subs.id, 500, at(2026, 11, 3), matchedThisMonth = false),
        )
        val s = compute(listOf(tx(groceries, -10_000)), charges = charges).safeToSpend!!
        assertEquals(45_000L, s.budgetRemainingMinor)
        assertEquals(listOf("Netflix"), s.upcomingUnbudgeted.map { it.name })
        assertEquals(45_000L - 1_549L, s.amountMinor)
    }

    @Test fun overspentCategoryReducesSafeToSpend() {
        val s = compute(listOf(tx(eating, -20_000))).safeToSpend!!
        assertEquals(40_000L - 5_000L, s.amountMinor)
    }

    @Test fun perDayCountsTodayAndOnlyWhenPositive() {
        val s = compute(emptyList(), today = LocalDate.of(2026, 11, 24)).safeToSpend!!
        assertEquals(7, s.daysRemaining)
        assertEquals(55_000L / 7, s.perDayMinor)
        val neg = compute(listOf(tx(groceries, -100_000))).safeToSpend!!
        assertTrue(neg.amountMinor < 0)
        assertNull(neg.perDayMinor)
    }

    @Test fun lastDayOfMonthHasOneDayRemaining() {
        val s = compute(emptyList(), today = LocalDate.of(2026, 11, 30)).safeToSpend!!
        assertEquals(1, s.daysRemaining)
        assertEquals(55_000L, s.perDayMinor)
    }

    @Test fun pastMonthHasNoPerDay() {
        val s = compute(emptyList(), month = YearMonth.of(2026, 10)).safeToSpend!!
        assertNull(s.daysRemaining)
        assertNull(s.perDayMinor)
    }

    @Test fun localMidnightBoundary() {
        // 11:59 PM on Oct 31 in Chicago belongs to October; 12:00 AM Nov 1 belongs to November,
        // even though both are Nov 1 in UTC.
        val lastOct = BudgetTx(groceries.id, -1_000, at(2026, 10, 31, 23, 59))
        val firstNov = BudgetTx(groceries.id, -2_000, at(2026, 11, 1, 0, 0))
        val g = compute(listOf(lastOct, firstNov)).budgeted.first { it.category == groceries }
        assertEquals(2_000L, g.spentMinor)
    }

    @Test fun sameInstantLandsInDifferentMonthsByZone() {
        val instant = at(2026, 12, 1, 3, 0, ZoneId.of("UTC")) // Nov 30 9 PM in Chicago
        val txs = listOf(BudgetTx(groceries.id, -1_000, instant))
        assertEquals(1_000L, compute(txs).budgeted.first { it.category == groceries }.spentMinor)
        val tokyo = compute(txs, zone = ZoneId.of("Asia/Tokyo"))
        assertEquals(0L, tokyo.budgeted.first { it.category == groceries }.spentMinor)
    }

    @Test fun dstMonthWindowIsCorrect() {
        val w = MonthWindow.of(YearMonth.of(2026, 3), chicago)
        assertEquals(31 * 86_400L - 3_600L, w.end - w.start) // spring forward: one hour shorter
    }

    @Test fun suggestionAveragesThreeFullMonthsRoundedUpToTenDollars() {
        val spent = mapOf(YearMonth.of(2026, 8) to 31_000L, YearMonth.of(2026, 9) to 29_500L, YearMonth.of(2026, 10) to 35_250L)
        assertEquals(32_000L, BudgetMath.suggestBudget(spent, nov, YearMonth.of(2026, 1)))
    }

    @Test fun suggestionUsesAvailableHistoryOnly() {
        val spent = mapOf(YearMonth.of(2026, 10) to 12_345L)
        assertEquals(13_000L, BudgetMath.suggestBudget(spent, nov, YearMonth.of(2026, 10)))
    }

    @Test fun noSuggestionWithoutFullMonthOrSpending() {
        assertNull(BudgetMath.suggestBudget(emptyMap(), nov, nov))
        assertNull(BudgetMath.suggestBudget(emptyMap(), nov, YearMonth.of(2026, 8)))
        assertNull(BudgetMath.suggestBudget(emptyMap(), nov, null))
    }

    @Test fun roundUp() {
        assertEquals(1_000L, BudgetMath.roundUpToTenDollars(1))
        assertEquals(1_000L, BudgetMath.roundUpToTenDollars(1_000))
        assertEquals(2_000L, BudgetMath.roundUpToTenDollars(1_001))
    }

    @Test fun allLinesListEveryExpenseCategory() {
        val m = compute(emptyList(), defaults = emptyMap())
        assertEquals(listOf(groceries, eating, subs), m.allLines.map { it.category })
    }
}
