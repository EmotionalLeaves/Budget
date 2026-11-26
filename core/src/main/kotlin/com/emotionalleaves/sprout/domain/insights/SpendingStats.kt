package com.emotionalleaves.sprout.domain.insights

import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Everything the Stats screen shows, worked out from plain rows so it can be tested without a database.
 * Money out is negative in [Row.amountMinor]; transfers are ignored everywhere.
 */
object SpendingStats {

    data class Row(
        val amountMinor: Long,
        val occurredAt: Long,
        val categoryId: Long,
        val kind: CategoryKind,
        val merchantKey: String,
    )

    data class Place(val merchantKey: String, val total: Long, val visits: Int) {
        val perVisit: Long get() = if (visits == 0) 0 else total / visits
    }

    data class SizeBucket(val label: String, val count: Int, val total: Long)

    data class Summary(
        val spent: Long,
        val earned: Long,
        val days: Int,
        val purchases: Int,
        val averagePurchase: Long,
        val medianPurchase: Long,
        val biggest: Row?,
        val noSpendDays: Int,
        /** Total spent on each weekday, Monday first. */
        val byWeekday: List<Long>,
        /** Spent in days 1–10, 11–20 and 21–end of each month. */
        val byMonthPart: List<Long>,
        val sizes: List<SizeBucket>,
        val topByTotal: List<Place>,
        val topByVisits: List<Place>,
        /** Cumulative spending per day of the period (index 0 = first day). */
        val cumulative: List<Long>,
    ) {
        val net: Long get() = earned - spent
        val dailyAverage: Long get() = if (days == 0) 0 else spent / days
        /** Share of income kept, 0..100, or null without income. */
        val savingsRate: Int? get() = if (earned <= 0) null else ((earned - spent) * 100 / earned).toInt().coerceIn(-999, 100)
        val busiestWeekday: DayOfWeek? get() = byWeekday.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.let { DayOfWeek.of(it.index + 1) }
    }

    /** Purchase sizes, chosen so everyday buys, bigger shops and large one-offs land in different buckets. */
    private val SIZE_EDGES = listOf(10_00L, 50_00L, 200_00L)
    private val SIZE_LABELS = listOf("Under \$10", "\$10–50", "\$50–200", "Over \$200")

    /**
     * [start] inclusive and [end] exclusive, as local dates; days after [today] are not counted (so a month in
     * progress isn't averaged over days that haven't happened).
     */
    fun summarize(rows: List<Row>, start: LocalDate, end: LocalDate, today: LocalDate, zone: ZoneId, top: Int = 8): Summary {
        val last = minOf(end.minusDays(1), today)
        val days = if (last < start) 0 else (ChronoUnit.DAYS.between(start, last) + 1).toInt()
        fun date(r: Row): LocalDate = Instant.ofEpochSecond(r.occurredAt).atZone(zone).toLocalDate()
        val inRange = rows.filter { val d = date(it); d >= start && d < end }
        val spending = inRange.filter { it.kind == CategoryKind.EXPENSE }
        val purchases = spending.filter { it.amountMinor < 0 }
        val spent = -spending.sumOf { it.amountMinor }
        val earned = inRange.filter { it.kind == CategoryKind.INCOME }.sumOf { it.amountMinor }
        val sizes = purchases.map { -it.amountMinor }.sorted()

        val perDay = LongArray(maxOf(days, 0))
        val weekday = LongArray(7)
        val part = LongArray(3)
        for (r in spending) {
            val d = date(r)
            val amt = -r.amountMinor
            val i = ChronoUnit.DAYS.between(start, d).toInt()
            if (i in perDay.indices) perDay[i] += amt
            weekday[d.dayOfWeek.value - 1] += amt
            part[when (d.dayOfMonth) { in 1..10 -> 0; in 11..20 -> 1; else -> 2 }] += amt
        }
        val spendDays = purchases.map { date(it) }.filter { it <= last }.toSet()
        var running = 0L
        val cumulative = perDay.map { running += it; running }

        val bucketIdx = { a: Long -> SIZE_EDGES.indexOfFirst { a < it }.let { if (it < 0) SIZE_EDGES.size else it } }
        val buckets = SIZE_LABELS.mapIndexed { i, label ->
            val inB = sizes.filter { bucketIdx(it) == i }
            SizeBucket(label, inB.size, inB.sum())
        }
        val places = purchases.filter { it.merchantKey.isNotBlank() }.groupBy { it.merchantKey }
            .map { (k, l) -> Place(k, -l.sumOf { it.amountMinor }, l.size) }

        return Summary(
            spent = spent,
            earned = earned,
            days = days,
            purchases = purchases.size,
            averagePurchase = if (sizes.isEmpty()) 0 else sizes.sum() / sizes.size,
            medianPurchase = if (sizes.isEmpty()) 0 else sizes[sizes.size / 2],
            biggest = purchases.minByOrNull { it.amountMinor },
            noSpendDays = (days - spendDays.size).coerceAtLeast(0),
            byWeekday = weekday.toList(),
            byMonthPart = part.toList(),
            sizes = buckets,
            topByTotal = places.sortedByDescending { it.total }.take(top),
            topByVisits = places.sortedWith(compareByDescending<Place> { it.visits }.thenByDescending { it.total }).take(top),
            cumulative = cumulative,
        )
    }

    /** Monthly spending per category for the [months] months ending with [lastMonth]: categoryId -> totals, oldest first. */
    fun categoryTrends(rows: List<Row>, lastMonth: java.time.YearMonth, months: Int, zone: ZoneId): Map<Long, List<Long>> {
        val first = lastMonth.minusMonths((months - 1).toLong())
        val out = HashMap<Long, LongArray>()
        for (r in rows) {
            if (r.kind != CategoryKind.EXPENSE) continue
            val ym = java.time.YearMonth.from(Instant.ofEpochSecond(r.occurredAt).atZone(zone))
            val i = ChronoUnit.MONTHS.between(first, ym).toInt()
            if (i !in 0 until months) continue
            out.getOrPut(r.categoryId) { LongArray(months) }[i] += -r.amountMinor
        }
        return out.mapValues { it.value.toList() }
    }

    /** "+12%" style change from [before] to [now]; null when there's nothing to compare with. */
    fun changePercent(before: Long, now: Long): Int? = if (before == 0L) null else ((now - before) * 100 / abs(before)).toInt()
}
