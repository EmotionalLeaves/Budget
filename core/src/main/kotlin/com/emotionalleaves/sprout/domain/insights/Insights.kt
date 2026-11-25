package com.emotionalleaves.sprout.domain.insights

import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.domain.recurring.Cadence
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class Tone { GOOD, NEUTRAL, HEADS_UP }

/** One plain-language observation about your money. [categoryId] links to the category when there is one. */
data class Insight(val key: String, val title: String, val body: String, val tone: Tone, val categoryId: Long? = null)

/** Spending of one category in one month (positive cents). */
data class CategoryMonth(val categoryId: Long, val name: String, val month: YearMonth, val spentMinor: Long)

/** Spending at one merchant this month (positive cents). */
data class MerchantSpend(val name: String, val spentMinor: Long, val count: Int)

/**
 * Turns this month's numbers into a few short, useful observations, most important first.
 * Pure: everything comes in as arguments.
 */
object InsightEngine {
    /** Ignore changes smaller than this: a $6 jump is noise. */
    private const val MIN_CHANGE_MINOR = 2_500L

    fun build(
        today: LocalDate,
        categoryMonths: List<CategoryMonth>,
        merchants: List<MerchantSpend>,
        subscriptionsMonthlyMinor: Long,
        subscriptionCount: Int,
        incomeThisMonthMinor: Long,
        spentThisMonthMinor: Long,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val month = YearMonth.from(today)
        val dayFraction = today.dayOfMonth.toDouble() / month.lengthOfMonth()
        val prior = (1..3).map { month.minusMonths(it.toLong()) }.toSet()
        val byCategory = categoryMonths.groupBy { it.categoryId }

        // Pace: categories running well ahead of their usual month.
        byCategory.forEach { (id, rows) ->
            val now = rows.firstOrNull { it.month == month }?.spentMinor ?: 0L
            val history = rows.filter { it.month in prior }.map { it.spentMinor }
            if (history.size < 2) return@forEach
            val avg = history.sum() / history.size
            if (avg <= 0) return@forEach
            val name = rows.first().name
            if (now > avg && now - avg >= MIN_CHANGE_MINOR) {
                out += Insight("over-$id", "$name is above usual",
                    "${Money.formatWhole(now)} so far vs about ${Money.formatWhole(avg)} in a typical month.", Tone.HEADS_UP, id)
            } else if (dayFraction >= 0.6 && now < avg * 0.6 && avg - now >= MIN_CHANGE_MINOR) {
                out += Insight("under-$id", "Spending less on $name",
                    "${Money.formatWhole(now)} so far vs about ${Money.formatWhole(avg)} usually. Nice.", Tone.GOOD, id)
            }
        }

        merchants.maxByOrNull { it.spentMinor }?.takeIf { it.spentMinor >= 5_000 }?.let { m ->
            out += Insight("merchant", "Most spent at ${m.name}",
                "${Money.formatWhole(m.spentMinor)} across ${m.count} purchase${if (m.count == 1) "" else "s"} this month.", Tone.NEUTRAL)
        }
        merchants.filter { it.count >= 6 }.maxByOrNull { it.count }?.let { m ->
            out += Insight("frequent", "${m.count} trips to ${m.name}",
                "Small buys add up: ${Money.formatWhole(m.spentMinor)} this month.", Tone.NEUTRAL)
        }

        if (subscriptionCount > 0) {
            out += Insight("subs", "Subscriptions: ${Money.formatWhole(subscriptionsMonthlyMinor)} a month",
                "$subscriptionCount repeating charge${if (subscriptionCount == 1) "" else "s"} ≈ ${Money.formatWhole(subscriptionsMonthlyMinor * 12)} a year.", Tone.NEUTRAL)
        }

        if (incomeThisMonthMinor > 0 && today.dayOfMonth >= 20) {
            val saved = incomeThisMonthMinor - spentThisMonthMinor
            out += if (saved > 0) {
                Insight("savings-rate", "Keeping ${(saved * 100 / incomeThisMonthMinor)}% of your income",
                    "${Money.formatWhole(incomeThisMonthMinor)} in, ${Money.formatWhole(spentThisMonthMinor)} spent this month.", Tone.GOOD)
            } else {
                Insight("savings-rate", "Spending more than came in",
                    "${Money.formatWhole(spentThisMonthMinor)} spent vs ${Money.formatWhole(incomeThisMonthMinor)} in this month.", Tone.HEADS_UP)
            }
        }
        val order = mapOf(Tone.HEADS_UP to 0, Tone.GOOD to 1, Tone.NEUTRAL to 2)
        return out.sortedBy { order[it.tone] }.take(6)
    }

    /** A repeating charge's cost per month: weekly ×52/12, yearly ÷12 and so on. */
    fun monthlyEquivalent(amountMinor: Long, cadence: Cadence): Long = when (cadence) {
        Cadence.WEEKLY -> amountMinor * 52 / 12
        Cadence.BIWEEKLY -> amountMinor * 26 / 12
        Cadence.MONTHLY -> amountMinor
        Cadence.QUARTERLY -> amountMinor / 3
        Cadence.YEARLY -> amountMinor / 12
    }
}

/** One account's change, for rebuilding past balances. */
data class BalanceTx(val accountId: String, val amountMinor: Long, val occurredAt: Long)

/**
 * Net worth on past dates, rebuilt backwards from today's balances: the balance at the end of a day is today's
 * balance minus everything that happened after that day. Only as far back as transactions go.
 */
object NetWorthHistory {
    fun monthEnds(today: LocalDate, months: Int): List<LocalDate> =
        (months - 1 downTo 1).map { YearMonth.from(today).minusMonths(it.toLong()).atEndOfMonth() } + today

    fun compute(balances: Map<String, Long>, txs: List<BalanceTx>, points: List<LocalDate>, zone: ZoneId): List<Pair<LocalDate, Long>> {
        val total = balances.values.sum()
        val sorted = txs.filter { it.accountId in balances }.sortedByDescending { it.occurredAt }
        return points.sorted().map { d ->
            val endOfDay = d.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val after = sorted.asSequence().takeWhile { it.occurredAt >= endOfDay }.sumOf { it.amountMinor }
            d to (total - after)
        }
    }

    fun earliest(txs: List<BalanceTx>, zone: ZoneId): LocalDate? =
        txs.minOfOrNull { it.occurredAt }?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
}
