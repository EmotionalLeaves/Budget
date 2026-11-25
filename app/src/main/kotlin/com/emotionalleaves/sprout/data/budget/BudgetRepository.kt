package com.emotionalleaves.sprout.data.budget

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.BudgetOverrideEntity
import com.emotionalleaves.sprout.data.db.BudgetRolloverEntity
import com.emotionalleaves.sprout.data.db.CategoryBudgetEntity
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.budget.BudgetCategory
import com.emotionalleaves.sprout.domain.budget.BudgetMath
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.budget.ExpectedCharge
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class Suggestion(val categoryId: Long, val name: String, val suggestedMinor: Long, val currentMinor: Long?)

class BudgetRepository(
    private val db: SproutDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Expected recurring charges for a month; wired to recurring detection in M7. */
    private val expectedCharges: (YearMonth) -> Flow<List<ExpectedCharge>> = { flowOf(emptyList()) },
) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(zone()))
    fun currentMonth(): YearMonth = YearMonth.from(today())

    /** Today's date, re-emitted when it changes (checked every minute) so screens roll over at midnight. */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    fun observeMonth(month: YearMonth): Flow<BudgetMonth> {
        val window = MonthWindow.of(month, zone())
        return combine(
            db.categories().observeAll(),
            db.budgets().observeDefaults(),
            db.budgets().observeOverrides(month.toString()),
            db.budgets().observeSums(window.start, window.end),
            expectedCharges(month),
        ) { cats, defaults, overrides, sums, charges -> Inputs(cats, defaults, overrides, sums, charges) }
            .combine(combine(db.budgets().observeRollovers(), db.budgets().observeOverridesBefore(month.toString())) { r, o -> r to o }) { i, (rollovers, pastOverrides) ->
            val (cats, defaults, overrides, sums, charges) = i
            val defaultMap = defaults.associate { it.categoryId to it.amountMinor }
            BudgetMath.computeFromSums(
                month = month,
                zone = zone(),
                today = today(),
                // Archived categories still count if they have a budget or spending this month.
                categories = cats.map { BudgetCategory(it.id, it.name, it.kind, it.archived) },
                defaultBudgets = defaultMap,
                overrides = overrides.associate { it.categoryId to it.amountMinor },
                sumsByCategory = sums.associate { it.categoryId to it.total },
                expectedCharges = charges,
                carries = carries(month, rollovers, defaultMap, pastOverrides),
            )
        }
    }

    private data class Inputs(
        val cats: List<com.emotionalleaves.sprout.data.db.CategoryEntity>,
        val defaults: List<CategoryBudgetEntity>,
        val overrides: List<BudgetOverrideEntity>,
        val sums: List<com.emotionalleaves.sprout.data.db.CategorySum>,
        val charges: List<ExpectedCharge>,
    )

    /**
     * Rollover carry into [month] for each category with rollover on. Past months use that month's override or
     * today's every-month amount (Sprout doesn't keep a history of every-month amounts). Recomputed whenever
     * transactions change, since observeSums re-emits on every transactions write.
     */
    private suspend fun carries(
        month: YearMonth,
        rollovers: List<BudgetRolloverEntity>,
        defaults: Map<Long, Long>,
        pastOverrides: List<BudgetOverrideEntity>,
    ): Map<Long, Long> {
        if (rollovers.isEmpty()) return emptyMap()
        val z = zone()
        val starts = rollovers.associate { it.categoryId to (runCatching { YearMonth.parse(it.sinceMonth) }.getOrNull() ?: month) }
        val earliest = starts.values.minOrNull()?.takeIf { it.isBefore(month) } ?: return starts.mapValues { 0L }
        val rows = withContext(Dispatchers.IO) {
            db.budgets().spendRows(MonthWindow.of(earliest, z).start, MonthWindow.of(month, z).start)
        }
        val spent = rows.filter { it.categoryId in starts }
            .groupBy { it.categoryId }
            .mapValues { (_, l) -> l.groupBy { MonthWindow.monthOf(it.occurredAt, z) }.mapValues { (_, x) -> maxOf(0L, -x.sumOf { it.amountMinor }) } }
        val overrideBy = pastOverrides.groupBy { it.categoryId }.mapValues { (_, l) -> l.associate { it.yearMonth to it.amountMinor } }
        return starts.mapValues { (id, since) ->
            BudgetMath.rolloverCarry(
                since = since,
                month = month,
                budgetFor = { m -> overrideBy[id]?.get(m.toString()) ?: defaults[id] },
                spentFor = { m -> spent[id]?.get(m) ?: 0L },
            )
        }
    }

    /** Turns rollover on (starting with [month]'s leftover) or off for a category. */
    suspend fun setRollover(categoryId: Long, on: Boolean, month: YearMonth) = withContext(Dispatchers.IO) {
        if (on) db.budgets().upsertRollover(BudgetRolloverEntity(categoryId, month.toString()))
        else db.budgets().deleteRollover(categoryId)
    }

    /** Sets a budget for every month, or only [month] when [thisMonthOnly]. Amount in cents, ≥ 0. */
    suspend fun setBudget(categoryId: Long, amountMinor: Long, thisMonthOnly: Boolean, month: YearMonth) = withContext(Dispatchers.IO) {
        val amount = amountMinor.coerceAtLeast(0)
        if (thisMonthOnly) {
            db.budgets().upsertOverride(BudgetOverrideEntity(categoryId, month.toString(), amount))
        } else {
            db.withTransaction {
                db.budgets().upsertDefault(CategoryBudgetEntity(categoryId, amount, clock.instant().epochSecond))
                // "Every month" replaces a one-off value for the month being edited.
                db.budgets().deleteOverride(categoryId, month.toString())
            }
        }
    }

    /** Removes the budget: this month's override only, or the every-month default (and this month's override). */
    suspend fun removeBudget(categoryId: Long, thisMonthOnly: Boolean, month: YearMonth) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.budgets().deleteOverride(categoryId, month.toString())
            if (!thisMonthOnly) {
                db.budgets().deleteDefault(categoryId)
                db.budgets().deleteRollover(categoryId)
            }
        }
    }

    /** Quick setup suggestions for EXPENSE categories with spending in the last 3 full months. */
    suspend fun suggestions(): List<Suggestion> = withContext(Dispatchers.IO) {
        val z = zone()
        val current = currentMonth()
        val first = db.budgets().firstOccurredAt()?.let { MonthWindow.monthOf(it, z) }
        val from = MonthWindow.of(current.minusMonths(3), z).start
        val to = MonthWindow.of(current, z).start
        val rows = db.budgets().spendRows(from, to)
        val byCategory = rows.groupBy { it.categoryId }.mapValues { (_, list) ->
            list.groupBy { MonthWindow.monthOf(it.occurredAt, z) }
                .mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        }
        val defaults = db.budgets().observeDefaults().first().associate { it.categoryId to it.amountMinor }
        db.categories().getAll()
            .filter { it.kind == CategoryKind.EXPENSE && !it.archived }
            .mapNotNull { c ->
                val s = BudgetMath.suggestBudget(byCategory[c.id].orEmpty(), current, first) ?: return@mapNotNull null
                Suggestion(c.id, c.name, s, defaults[c.id])
            }
    }

    suspend fun applySuggestions(amounts: Map<Long, Long>) = withContext(Dispatchers.IO) {
        val now = clock.instant().epochSecond
        db.budgets().upsertDefaults(amounts.filterValues { it >= 0 }.map { (id, a) -> CategoryBudgetEntity(id, a, now) })
    }

    /** Spending per month for the last [months] months (oldest first), including the current one. */
    suspend fun history(categoryId: Long, months: Int = 6): List<Pair<YearMonth, Long>> = withContext(Dispatchers.IO) {
        val z = zone()
        val current = currentMonth()
        val start = MonthWindow.of(current.minusMonths((months - 1).toLong()), z).start
        val end = MonthWindow.of(current, z).end
        val sums = db.budgets().spendRows(start, end, categoryId)
            .groupBy { MonthWindow.monthOf(it.occurredAt, z) }
            .mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
        (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }.map { it to (sums[it] ?: 0L) }
    }
}
