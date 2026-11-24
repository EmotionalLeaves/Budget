package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The epoch-second range [start, end) of a calendar month in the device's time zone. */
data class MonthWindow(val month: YearMonth, val start: Long, val end: Long) {
    operator fun contains(epochSeconds: Long) = epochSeconds in start until end

    companion object {
        fun of(month: YearMonth, zone: ZoneId) = MonthWindow(
            month,
            month.atDay(1).atStartOfDay(zone).toEpochSecond(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toEpochSecond(),
        )

        fun monthOf(epochSeconds: Long, zone: ZoneId): YearMonth =
            YearMonth.from(java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone))
    }
}

data class BudgetCategory(val id: Long, val name: String, val kind: CategoryKind, val archived: Boolean = false)

/** A transaction as budget math sees it. */
data class BudgetTx(val categoryId: Long, val amountMinor: Long, val occurredAt: Long, val excluded: Boolean = false)

/** An expected recurring charge. [amountMinor] is positive (the expected outflow). */
data class ExpectedCharge(
    val name: String,
    val categoryId: Long,
    val amountMinor: Long,
    val dueAt: Long,
    /** True once a transaction this month has been matched to it ("Paid ✓"). */
    val matchedThisMonth: Boolean,
)

enum class ProgressLevel { GREEN, AMBER, RED }

data class CategoryLine(
    val category: BudgetCategory,
    /** Effective budget: this month's override, else the default, else null. */
    val budgetMinor: Long?,
    val spentMinor: Long,
) {
    val remainingMinor: Long? get() = budgetMinor?.let { it - spentMinor }

    /** Whole percent used, or null without a budget. A $0 budget with spending counts as over. */
    val percentUsed: Int?
        get() = budgetMinor?.let { b ->
            when {
                b <= 0L -> if (spentMinor > 0) 100 else 0
                else -> ((spentMinor * 100) / b).coerceAtMost(9_999L).toInt()
            }
        }

    val level: ProgressLevel?
        get() = percentUsed?.let { p ->
            when {
                p >= 100 -> ProgressLevel.RED
                p >= 80 -> ProgressLevel.AMBER
                else -> ProgressLevel.GREEN
            }
        }
}

data class SafeToSpend(
    /** Σ over budgeted EXPENSE categories of (budget − spent). */
    val budgetRemainingMinor: Long,
    /** Unmatched recurring charges still due this month in categories without a budget. */
    val upcomingUnbudgeted: List<ExpectedCharge>,
    val amountMinor: Long,
    /** Days left in the month including today; null for months that aren't the current one. */
    val daysRemaining: Int?,
    /** "≈ $Y/day": only when the amount is positive and it's the current month. */
    val perDayMinor: Long?,
)

data class BudgetMonth(
    val month: YearMonth,
    /** Budgeted EXPENSE categories, in category order. */
    val budgeted: List<CategoryLine>,
    /** EXPENSE categories with spending but no budget. */
    val unbudgetedWithSpending: List<CategoryLine>,
    val totalBudgetMinor: Long,
    val totalSpentBudgetedMinor: Long,
    /** All EXPENSE spending this month, budgeted or not. Transfers and income never count. */
    val totalSpentMinor: Long,
    /** Null when no budgets exist: show "Set a budget to see what's safe to spend". */
    val safeToSpend: SafeToSpend?,
)

/**
 * Pure monthly budget math. Money in cents, months in the device's zone.
 *
 * - Spent in a category = max(0, −Σ amountMinor) of the month's non-excluded transactions. Refunds reduce spending.
 * - Only EXPENSE categories count. TRANSFER and INCOME transactions never count as spending.
 * - Safe to spend = Σ(budget − spent) over budgeted EXPENSE categories − unmatched recurring charges expected
 *   before month end in categories without a budget.
 */
object BudgetMath {

    fun compute(
        month: YearMonth,
        zone: ZoneId,
        today: LocalDate,
        categories: List<BudgetCategory>,
        defaultBudgets: Map<Long, Long>,
        overrides: Map<Long, Long>,
        transactions: List<BudgetTx>,
        expectedCharges: List<ExpectedCharge> = emptyList(),
    ): BudgetMonth {
        val window = MonthWindow.of(month, zone)
        val sums = HashMap<Long, Long>()
        for (t in transactions) {
            if (t.excluded || t.occurredAt !in window) continue
            sums[t.categoryId] = (sums[t.categoryId] ?: 0L) + t.amountMinor
        }
        return computeFromSums(month, zone, today, categories, defaultBudgets, overrides, sums, expectedCharges)
    }

    /** Same as [compute], from per-category sums of amountMinor (the database aggregates them). */
    fun computeFromSums(
        month: YearMonth,
        zone: ZoneId,
        today: LocalDate,
        categories: List<BudgetCategory>,
        defaultBudgets: Map<Long, Long>,
        overrides: Map<Long, Long>,
        sumsByCategory: Map<Long, Long>,
        expectedCharges: List<ExpectedCharge> = emptyList(),
    ): BudgetMonth {
        val window = MonthWindow.of(month, zone)
        val expense = categories.filter { it.kind == CategoryKind.EXPENSE }
        val lines = expense.map { c ->
            val spent = maxOf(0L, -(sumsByCategory[c.id] ?: 0L))
            CategoryLine(c, overrides[c.id] ?: defaultBudgets[c.id], spent)
        }
        val budgeted = lines.filter { it.budgetMinor != null }
        val unbudgeted = lines.filter { it.budgetMinor == null && it.spentMinor > 0 }
        val budgetedIds = budgeted.map { it.category.id }.toSet()

        val safe = if (budgeted.isEmpty()) null else {
            val remaining = budgeted.sumOf { it.remainingMinor ?: 0L }
            val todayStart = today.atStartOfDay(zone).toEpochSecond()
            val from = maxOf(window.start, if (YearMonth.from(today) == month) todayStart else window.start)
            val upcoming = expectedCharges.filter { ch ->
                !ch.matchedThisMonth && ch.categoryId !in budgetedIds && ch.dueAt >= from && ch.dueAt < window.end
            }.sortedBy { it.dueAt }
            val amount = remaining - upcoming.sumOf { it.amountMinor }
            val days = if (YearMonth.from(today) == month) month.lengthOfMonth() - today.dayOfMonth + 1 else null
            val perDay = if (days != null && amount > 0) amount / days else null
            SafeToSpend(remaining, upcoming, amount, days, perDay)
        }

        return BudgetMonth(
            month = month,
            budgeted = budgeted,
            unbudgetedWithSpending = unbudgeted.sortedByDescending { it.spentMinor },
            totalBudgetMinor = budgeted.sumOf { it.budgetMinor ?: 0L },
            totalSpentBudgetedMinor = budgeted.sumOf { it.spentMinor },
            totalSpentMinor = lines.sumOf { it.spentMinor },
            safeToSpend = safe,
        )
    }

    /**
     * Quick setup: the average monthly spending over the last 3 full months (or fewer if history is shorter),
     * rounded up to the next $10. Months before [firstDataMonth] don't count. Returns null when there is
     * no full month of history or no spending.
     */
    fun suggestBudget(spentByMonth: Map<YearMonth, Long>, currentMonth: YearMonth, firstDataMonth: YearMonth?): Long? {
        if (firstDataMonth == null) return null
        val months = (1..3).map { currentMonth.minusMonths(it.toLong()) }.filter { !it.isBefore(firstDataMonth) }
        if (months.isEmpty()) return null
        val total = months.sumOf { maxOf(0L, spentByMonth[it] ?: 0L) }
        if (total <= 0L) return null
        val avg = (total + months.size - 1) / months.size
        return roundUpToTenDollars(avg)
    }

    fun roundUpToTenDollars(amountMinor: Long): Long {
        if (amountMinor <= 0) return 0
        val step = 1_000L
        return ((amountMinor + step - 1) / step) * step
    }
}
