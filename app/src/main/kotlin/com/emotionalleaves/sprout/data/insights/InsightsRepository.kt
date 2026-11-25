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

    /** Last month in review (or [month]). */
    suspend fun recap(month: YearMonth = YearMonth.from(today()).minusMonths(1)): com.emotionalleaves.sprout.domain.insights.Recap = withContext(Dispatchers.IO) {
        val z = zone()
        val w = MonthWindow.of(month, z)
        val pw = MonthWindow.of(month.minusMonths(1), z)
        val cats = db.categories().getAll().associateBy { it.id }
        val expense = cats.values.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        val rows = db.budgets().spendRows(w.start, w.end).filter { it.categoryId in expense }
        val byCat = rows.groupBy { it.categoryId }.mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        val prevRows = db.budgets().spendRows(pw.start, pw.end).filter { it.categoryId in expense }
        val prev = prevRows.groupBy { it.categoryId }.values.sumOf { l -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        val merchants = db.insights().merchantTotals(w.start, w.end)
        val biggest = db.insights().biggestCharge(w.start, w.end)
        val days = rows.map { java.time.Instant.ofEpochSecond(it.occurredAt).atZone(z).toLocalDate() }.toSet()
        com.emotionalleaves.sprout.domain.insights.Recap(
            month = month,
            spentMinor = byCat.values.sum(),
            incomeMinor = db.insights().income(w.start, w.end),
            previousSpentMinor = prev.takeIf { prevRows.isNotEmpty() },
            topCategories = byCat.entries.sortedByDescending { it.value }.take(3).map { (cats[it.key]?.name ?: "Other") to it.value },
            biggest = biggest?.let { MerchantNormalizer.displayName(it.merchantKey) to -it.amountMinor },
            noSpendDays = month.lengthOfMonth() - days.size,
            transactions = merchants.sumOf { it.count },
        )
    }

    /** This month's everyday spending per day, as a calendar. */
    suspend fun calendar(): com.emotionalleaves.sprout.domain.calendar.SpendCalendar = withContext(Dispatchers.IO) {
        val z = zone()
        val today = today()
        val month = YearMonth.from(today)
        val w = MonthWindow.of(month, z)
        val expense = db.categories().getAll().filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        val rows = db.budgets().spendRows(w.start, w.end).filter { it.categoryId in expense }.map { it.amountMinor to it.occurredAt }
        com.emotionalleaves.sprout.domain.calendar.SpendCalendar.of(month, today, rows, z)
    }

    /** YNAB-style Age of Money over cash accounts (checking, savings, cash), in days; null without enough history. */
    suspend fun ageOfMoney(): Int? = withContext(Dispatchers.IO) {
        val accounts = db.accounts().getAll().filter { it.includeInTotals && !it.hidden && AccountTypes.isUsd(it.currency) && AccountTypes.isCashLike(it.type) }
        if (accounts.isEmpty()) return@withContext null
        val since = clock.instant().epochSecond - 365 * 86_400L
        val moves = accounts.map { it.id }.chunked(500).flatMap { db.insights().moves(since, it) }.map { it.amountMinor to it.occurredAt }
        com.emotionalleaves.sprout.domain.insights.AgeOfMoney.days(moves)
    }

    /** Money health: savings rate (last 3 full months), emergency fund, Age of Money and this month's pace. */
    suspend fun health(): com.emotionalleaves.sprout.domain.challenge.HealthScore.Result = withContext(Dispatchers.IO) {
        val z = zone()
        val current = YearMonth.from(today())
        val months = (1..3).map { current.minusMonths(it.toLong()) }
        val (_, grid) = yearGrid(4)
        val spentPrev = grid.sumOf { r -> r.byMonth.dropLast(1).sum() }
        val incomePrev = months.sumOf { m -> MonthWindow.of(m, z).let { w -> db.insights().income(w.start, w.end) } }
        val rate = if (incomePrev > 0) (((incomePrev - spentPrev) * 100) / incomePrev).toInt() else null
        val cash = db.accounts().getAll().filter { it.includeInTotals && !it.hidden && AccountTypes.isUsd(it.currency) && AccountTypes.isCashLike(it.type) }
            .sumOf { it.balanceMinor }
        val avgSpend = spentPrev / 3
        val emergency = if (avgSpend > 0) maxOf(0.0, cash.toDouble() / avgSpend) else null
        val spentNow = grid.sumOf { it.byMonth.last() }
        val t = today()
        val pace = com.emotionalleaves.sprout.domain.budget.Pace.of(spentNow, avgSpend.takeIf { it > 0 }, t.dayOfMonth, t.lengthOfMonth())
        com.emotionalleaves.sprout.domain.challenge.HealthScore.compute(
            rate?.coerceIn(-100, 100), emergency, ageOfMoney(), if (avgSpend > 0 && !pace.tooEarly) pace.onTrack else null,
        )
    }

    /** Repeating charges whose latest price differs from the one before. */
    suspend fun priceChanges(): List<com.emotionalleaves.sprout.domain.insights.PriceChange> = withContext(Dispatchers.IO) {
        db.recurring().getAll().filter { it.status != RecurringStatus.DISMISSED }.mapNotNull { r ->
            com.emotionalleaves.sprout.domain.insights.PriceChange.detect(r.displayName, db.insights().recentCharges(r.merchantKey).reversed())
        }
    }

    /** This month's totals for pinned categories ("c:<id>") and merchants ("m:<key>"). */
    suspend fun watch(items: Set<String>): List<Triple<String, String, Long>> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()
        val z = zone()
        val w = MonthWindow.of(YearMonth.from(today()), z)
        val cats = db.categories().getAll().associateBy { it.id }
        val sums = db.budgets().spendRows(w.start, w.end).groupBy { it.categoryId }.mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        val merchants = db.insights().merchantTotals(w.start, w.end).associate { it.merchantKey to it.total }
        items.mapNotNull { item ->
            when {
                item.startsWith("c:") -> item.drop(2).toLongOrNull()?.let { id -> cats[id]?.let { Triple(item, it.name, sums[id] ?: 0L) } }
                item.startsWith("m:") -> item.drop(2).let { k -> Triple(item, MerchantNormalizer.displayName(k), maxOf(0L, merchants[k] ?: 0L)) }
                else -> null
            }
        }
    }

    data class Reports(
        val months: List<YearMonth>,
        val incomeByMonth: List<Long>,
        val spendingByMonth: List<Long>,
        val categories: List<Pair<String, Long>>,
        val accounts: List<Pair<String, Long>>,
    )

    /** Reports for the last [months] months: cash flow per month, and spending by category and by account. */
    suspend fun reports(months: Int): Reports = withContext(Dispatchers.IO) {
        val z = zone()
        val current = YearMonth.from(today())
        val list = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val start = MonthWindow.of(list.first(), z).start
        val end = MonthWindow.of(current, z).end
        val expense = db.categories().getAll().filter { it.kind == CategoryKind.EXPENSE }.associateBy { it.id }
        val rows = db.budgets().spendRows(start, end).filter { it.categoryId in expense }
        val spendMonth = rows.groupBy { MonthWindow.monthOf(it.occurredAt, z) }
            .mapValues { (_, l) -> l.groupBy { it.categoryId }.values.sumOf { c -> maxOf(0L, -c.sumOf { it.amountMinor }) } }
        val byCat = rows.groupBy { it.categoryId }.mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
            .filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { expense.getValue(it.key).name to it.value }
        Reports(
            months = list,
            incomeByMonth = list.map { m -> MonthWindow.of(m, z).let { w -> db.insights().income(w.start, w.end) } },
            spendingByMonth = list.map { spendMonth[it] ?: 0L },
            categories = byCat,
            accounts = db.insights().spendingByAccount(start, end).map { it.name to it.total },
        )
    }

    data class YearReview(
        val year: Int,
        val spentMinor: Long,
        val incomeMinor: Long,
        val topCategories: List<Pair<String, Long>>,
        val topMerchants: List<Pair<String, Long>>,
        val biggest: Pair<String, Long>?,
        val priciestMonth: Pair<YearMonth, Long>?,
        val cheapestMonth: Pair<YearMonth, Long>?,
        val noSpendDays: Int,
        val transactions: Int,
        val byMonth: List<Pair<YearMonth, Long>>,
    )

    /** A whole year in review (this year so far by default). */
    suspend fun yearReview(year: Int = today().year): YearReview = withContext(Dispatchers.IO) {
        val z = zone()
        val first = YearMonth.of(year, 1)
        val last = minOf(YearMonth.of(year, 12), YearMonth.from(today()))
        val start = MonthWindow.of(first, z).start
        val end = MonthWindow.of(last, z).end
        val expense = db.categories().getAll().filter { it.kind == CategoryKind.EXPENSE }.associateBy { it.id }
        val rows = db.budgets().spendRows(start, end).filter { it.categoryId in expense }
        val byCat = rows.groupBy { it.categoryId }.mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
        val byMonth = rows.groupBy { MonthWindow.monthOf(it.occurredAt, z) }
            .mapValues { (_, l) -> l.groupBy { it.categoryId }.values.sumOf { c -> maxOf(0L, -c.sumOf { it.amountMinor }) } }
        val monthList = months.map { it to (byMonth[it] ?: 0L) }
        val spentDays = rows.map { java.time.Instant.ofEpochSecond(it.occurredAt).atZone(z).toLocalDate() }.toSet()
        val firstDay = rows.minOfOrNull { it.occurredAt }?.let { java.time.Instant.ofEpochSecond(it).atZone(z).toLocalDate() }
        val endDay = minOf(last.atEndOfMonth(), today())
        val daysCovered = firstDay?.let { java.time.temporal.ChronoUnit.DAYS.between(it, endDay).toInt() + 1 } ?: 0
        val withSpend = monthList.filter { it.second > 0 }
        YearReview(
            year = year,
            spentMinor = byCat.values.sum(),
            incomeMinor = db.insights().income(start, end),
            topCategories = byCat.entries.sortedByDescending { it.value }.take(5).map { expense.getValue(it.key).name to it.value },
            topMerchants = db.insights().merchantTotals(start, end).take(5).map { MerchantNormalizer.displayName(it.merchantKey) to it.total },
            biggest = db.insights().biggestCharge(start, end)?.let { MerchantNormalizer.displayName(it.merchantKey) to -it.amountMinor },
            priciestMonth = withSpend.maxByOrNull { it.second },
            cheapestMonth = withSpend.minByOrNull { it.second },
            noSpendDays = (daysCovered - spentDays.size).coerceAtLeast(0),
            transactions = db.insights().countIn(start, end),
            byMonth = monthList,
        )
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
        val base = InsightEngine.build(today, categoryMonths, merchants, subs.monthlyMinor, subs.count, db.insights().income(w.start, w.end), spent)
        val changes = priceChanges().filter { it.up }.map {
            Insight("price-${it.name}", "${it.name} went up", "From ${com.emotionalleaves.sprout.domain.money.Money.format(it.fromMinor)} to " +
                "${com.emotionalleaves.sprout.domain.money.Money.format(it.toMinor)}. Still worth it?", com.emotionalleaves.sprout.domain.insights.Tone.HEADS_UP)
        }
        (changes + base).take(8)
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
