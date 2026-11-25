package com.emotionalleaves.sprout.data.forecast

import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.recurring.RecurringRepository
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.forecast.CashFlow
import com.emotionalleaves.sprout.domain.forecast.Expected
import com.emotionalleaves.sprout.domain.forecast.Forecast
import com.emotionalleaves.sprout.domain.recurring.ChargeTx
import com.emotionalleaves.sprout.domain.recurring.RecurringDetector
import com.emotionalleaves.sprout.domain.recurring.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HomeOutlook(val forecast: Forecast?, val trend: CashFlow.Trend?, val payday: com.emotionalleaves.sprout.domain.forecast.PaydayPlan? = null)

/** Builds the month-end cash forecast and the spending trend from what's stored. */
class ForecastRepository(
    private val db: SproutDatabase,
    private val budgets: BudgetRepository,
    private val recurring: RecurringRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun outlook(): HomeOutlook = withContext(Dispatchers.IO) {
        val z = zone()
        val today = LocalDate.now(clock.withZone(z))
        val now = clock.instant().epochSecond
        val month = budgets.currentMonth()

        // Cash: checking, savings and cash accounts counted in totals (USD only).
        val cashAccounts = db.accounts().getAll().filter {
            it.includeInTotals && !it.hidden && AccountTypes.isUsd(it.currency) && AccountTypes.isCashLike(it.type)
        }
        val payday = if (cashAccounts.isEmpty()) null else runCatching {
            val incomeRows = db.recurring().inflowsSince(now - RecurringDetector.LOOKBACK_DAYS * 86_400)
            val income = RecurringDetector.detectIncome(
                incomeRows.map { ChargeTx(it.merchantKey, it.amountMinor, it.occurredAt, it.categoryId, it.accountId) }, now, z,
            ).flatMap { r ->
                val first = date(r.nextDueAt, z)
                Schedule.occurrences(first, r.cadence, today, today.plusDays(45), first.dayOfMonth)
                    .map { Expected(MerchantNormalizer.displayName(r.merchantKey), it, r.typicalAmountMinor) }
            }
            val bills = (recurring.expectedCharges(month).first().filter { !it.matchedThisMonth } + recurring.expectedCharges(month.plusMonths(1)).first())
                .map { Expected(it.name, date(it.dueAt, z), it.amountMinor) }
                .distinctBy { it.name to it.date }
            com.emotionalleaves.sprout.domain.forecast.PaydayPlan.compute(today, cashAccounts.sumOf { it.balanceMinor }, income, bills)
        }.getOrNull()
        val forecast = if (cashAccounts.isEmpty()) null else {
            val monthBudget = budgets.observeMonth(month).first()
            val budgetedIds = monthBudget.budgeted.map { it.category.id }.toSet()
            val bills = recurring.expectedCharges(month).first()
                .filter { !it.matchedThisMonth && (monthBudget.safeToSpend == null || it.categoryId !in budgetedIds) }
                .map { Expected(it.name, date(it.dueAt, z), it.amountMinor) }
            val incomeRows = db.recurring().inflowsSince(now - RecurringDetector.LOOKBACK_DAYS * 86_400)
            val income = RecurringDetector.detectIncome(
                incomeRows.map { ChargeTx(it.merchantKey, it.amountMinor, it.occurredAt, it.categoryId, it.accountId) }, now, z,
            ).flatMap { r ->
                val first = date(r.nextDueAt, z)
                Schedule.occurrences(first, r.cadence, today, today.plusDays(62), first.dayOfMonth)
                    .map { Expected(MerchantNormalizer.displayName(r.merchantKey), it, r.typicalAmountMinor) }
            }
            CashFlow.forecast(
                today = today,
                cashMinor = cashAccounts.sumOf { it.balanceMinor },
                income = income,
                bills = bills,
                budgetRemainingMinor = monthBudget.safeToSpend?.budgetRemainingMinor,
                recentSpendingMinor = db.recurring().everydaySpending(now - 30 * 86_400, now + 1),
            )
        }

        // Trend: this month through today vs the same days last month, EXPENSE categories only.
        val expenseIds = db.categories().getAll().filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        val thisStart = MonthWindow.of(month, z).start
        val tomorrow = today.plusDays(1).atStartOfDay(z).toEpochSecond()
        val last = month.minusMonths(1)
        val lastStart = MonthWindow.of(last, z).start
        val lastEndDay = minOf(today.dayOfMonth, last.lengthOfMonth())
        val lastEnd = last.atDay(lastEndDay).plusDays(1).atStartOfDay(z).toEpochSecond()
        suspend fun spent(from: Long, to: Long) = db.budgets().spendRows(from, to)
            .filter { it.categoryId in expenseIds }.groupBy { it.categoryId }
            .values.sumOf { rows -> maxOf(0L, -rows.sumOf { it.amountMinor }) }
        val hasLastMonth = (db.budgets().firstOccurredAt() ?: Long.MAX_VALUE) < lastStart + 86_400
        val trend = if (hasLastMonth) CashFlow.Trend(spent(thisStart, tomorrow), spent(lastStart, lastEnd)) else null

        HomeOutlook(forecast, trend, payday)
    }

    private fun date(epoch: Long, z: ZoneId): LocalDate = Instant.ofEpochSecond(epoch).atZone(z).toLocalDate()

    /** Expected paydays in [month] from your repeating income (name, amount). */
    suspend fun paydays(month: java.time.YearMonth): List<Expected> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val z = java.time.ZoneId.systemDefault()
        val now = clock.instant().epochSecond
        val incomeRows = db.recurring().inflowsSince(now - RecurringDetector.LOOKBACK_DAYS * 86_400)
        RecurringDetector.detectIncome(
            incomeRows.map { ChargeTx(it.merchantKey, it.amountMinor, it.occurredAt, it.categoryId, it.accountId) }, now, z,
        ).flatMap { r ->
            val first = java.time.Instant.ofEpochSecond(r.nextDueAt).atZone(z).toLocalDate()
            Schedule.occurrences(first, r.cadence, month.atDay(1), month.atEndOfMonth(), first.dayOfMonth)
                .map { Expected(MerchantNormalizer.displayName(r.merchantKey), it, r.typicalAmountMinor) }
        }.sortedBy { it.date }
    }
}
