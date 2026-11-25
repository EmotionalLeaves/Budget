package com.emotionalleaves.sprout.data.insights

import com.emotionalleaves.sprout.data.db.RecurringStatus
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.insights.BalanceTx
import com.emotionalleaves.sprout.domain.insights.CategoryMonth
import com.emotionalleaves.sprout.domain.insights.Insight
import com.emotionalleaves.sprout.domain.insights.InsightEngine
import com.emotionalleaves.sprout.domain.insights.MerchantSpend
import com.emotionalleaves.sprout.domain.insights.NetWorthHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class Subscriptions(val monthlyMinor: Long, val yearlyMinor: Long, val count: Int)

/** A category's spending per month for the year view. */
data class YearRow(val categoryId: Long, val name: String, val colorArgb: Int, val iconKey: String, val byMonth: List<Long>) {
    val total: Long get() = byMonth.sum()
    val average: Long get() = byMonth.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
}

class InsightsRepository(private val db: SproutDatabase, private val clock: Clock, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone()))

    suspend fun subscriptions(): Subscriptions = withContext(Dispatchers.IO) {
        val active = db.recurring().getAll().filter { it.status != RecurringStatus.DISMISSED }
        val monthly = active.sumOf { InsightEngine.monthlyEquivalent(kotlin.math.abs(it.typicalAmountMinor), it.cadence) }
        Subscriptions(monthly, monthly * 12, active.size)
    }

    /** Spending per EXPENSE category per month, oldest month first, for [months] months ending this month. */
    suspend fun yearGrid(months: Int = 12): Pair<List<YearMonth>, List<YearRow>> = withContext(Dispatchers.IO) {
        val z = zone()
        val current = YearMonth.from(today())
        val list = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val rows = db.budgets().spendRows(MonthWindow.of(list.first(), z).start, MonthWindow.of(current, z).end)
        val cats = db.categories().getAll().filter { it.kind == CategoryKind.EXPENSE }.associateBy { it.id }
        val grid = rows.filter { it.categoryId in cats }.groupBy { it.categoryId }.map { (id, l) ->
            val byMonth = l.groupBy { MonthWindow.monthOf(it.occurredAt, z) }.mapValues { (_, x) -> maxOf(0L, -x.sumOf { it.amountMinor }) }
            val c = cats.getValue(id)
            YearRow(id, c.name, c.colorArgb, c.iconKey, list.map { byMonth[it] ?: 0L })
        }.filter { it.total > 0 }.sortedByDescending { it.total }
        list to grid
    }

    suspend fun insights(): List<Insight> = withContext(Dispatchers.IO) {
        val z = zone()
        val today = today()
        val month = YearMonth.from(today)
        val (months, grid) = yearGrid(4)
        val categoryMonths = grid.flatMap { r -> months.mapIndexed { i, m -> CategoryMonth(r.categoryId, r.name, m, r.byMonth[i]) } }
        val w = MonthWindow.of(month, z)
        val merchants = db.insights().merchantTotals(w.start, w.end).map { MerchantSpend(MerchantNormalizer.displayName(it.merchantKey), it.total, it.count) }
        val subs = subscriptions()
        val spent = grid.sumOf { it.byMonth.last() }
        InsightEngine.build(today, categoryMonths, merchants, subs.monthlyMinor, subs.count, db.insights().income(w.start, w.end), spent)
    }

    /** Net worth at the end of each of the last [months] months (today for the current one), from included USD accounts. */
    suspend fun netWorth(months: Int = 12): List<Pair<LocalDate, Long>> = withContext(Dispatchers.IO) {
        val z = zone()
        val accounts = db.accounts().getAll().filter { it.includeInTotals && !it.hidden && AccountTypes.isUsd(it.currency) }
        if (accounts.isEmpty()) return@withContext emptyList()
        val points = NetWorthHistory.monthEnds(today(), months)
        val since = points.first().atStartOfDay(z).toEpochSecond()
        val moves = accounts.map { it.id }.chunked(500).flatMap { db.insights().moves(since, it) }
            .map { BalanceTx(it.accountId, it.amountMinor, it.occurredAt) }
        val allMoves = accounts.map { it.id }.chunked(500).flatMap { db.insights().moves(0, it) }.map { BalanceTx(it.accountId, it.amountMinor, it.occurredAt) }
        val earliest = NetWorthHistory.earliest(allMoves, z)
        val series = NetWorthHistory.compute(accounts.associate { it.id to it.balanceMinor }, moves, points, z)
        // Before the first transaction we know about, the rebuilt numbers would be guesses: drop them.
        series.filter { (d, _) -> earliest == null || !d.isBefore(earliest.minusDays(1)) || d == points.last() }
    }
}
