package com.emotionalleaves.sprout.data.budget

import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.domain.budget.ExpectedCharge
import com.emotionalleaves.sprout.domain.budget.FlexMonth
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as K
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId

/** Flex budgeting: fixed costs are expected, one Flex amount covers the rest. */
class FlexRepository(
    private val db: SproutDatabase,
    private val settings: SettingsStore,
    private val budgets: BudgetRepository,
    private val expectedCharges: (YearMonth) -> Flow<List<ExpectedCharge>>,
    private val plannedSetAside: () -> Flow<Long>,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Categories that count as fixed when you haven't chosen any: housing, utilities, phone, insurance, subscriptions. */
    val defaultFixedKeys = setOf(K.RENT, K.UTILITIES, K.PHONE, K.INSURANCE, K.SUBSCRIPTIONS)

    fun observe(month: YearMonth): Flow<FlexState> {
        val w = MonthWindow.of(month, zone())
        return combine(
            db.budgets().observeSums(w.start, w.end),
            db.categories().observeAll(),
            settings.settings,
            expectedCharges(month),
            plannedSetAside(),
        ) { sums, cats, s, charges, planned ->
            val expense = cats.filter { it.kind == CategoryKind.EXPENSE }
            val fixed = s.fixedCategoryIds.takeIf { it.isNotEmpty() }
                ?: expense.filter { it.key in defaultFixedKeys }.map { it.id }.toSet()
            val today = budgets.today()
            val days = if (YearMonth.from(today) == month) month.lengthOfMonth() - today.dayOfMonth + 1 else null
            val expenseIds = expense.map { it.id }.toSet()
            FlexState(
                FlexMonth.of(
                    s.flexBudgetMinor,
                    sums.filter { it.categoryId in expenseIds }.associate { it.categoryId to it.total },
                    fixed,
                    charges.sumOf { it.amountMinor },
                    if (YearMonth.from(today) == month) planned else 0L,
                    days,
                ),
                fixed,
                expense.filter { !it.archived }.map { it.id to it.name },
            )
        }
    }
}

data class FlexState(val month: FlexMonth, val fixedIds: Set<Long>, val categories: List<Pair<Long, String>>)
