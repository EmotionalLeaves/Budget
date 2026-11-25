package com.emotionalleaves.sprout.domain.merchant

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One charge or refund at a merchant. */
data class MerchantTx(val amountMinor: Long, val occurredAt: Long)

/** How much you spend at one merchant. Spending is positive cents; refunds reduce it. */
data class MerchantStats(
    val visits: Int,
    val totalSpentMinor: Long,
    val averageMinor: Long,
    val thisYearMinor: Long,
    /** Oldest month first, last [MONTHS] months, net spending (never below 0). */
    val byMonth: List<Pair<YearMonth, Long>>,
    val firstAt: Long?,
    val lastAt: Long?,
) {
    companion object {
        const val MONTHS = 6

        fun of(txs: List<MerchantTx>, today: YearMonth, zone: ZoneId): MerchantStats {
            val charges = txs.filter { it.amountMinor < 0 }
            val net = -txs.sumOf { it.amountMinor }
            fun monthOf(t: MerchantTx) = YearMonth.from(Instant.ofEpochSecond(t.occurredAt).atZone(zone))
            val grouped = txs.groupBy(::monthOf).mapValues { (_, l) -> maxOf(0L, -l.sumOf { it.amountMinor }) }
            val months = (MONTHS - 1 downTo 0).map { today.minusMonths(it.toLong()) }
            return MerchantStats(
                visits = charges.size,
                totalSpentMinor = maxOf(0L, net),
                averageMinor = if (charges.isEmpty()) 0 else -charges.sumOf { it.amountMinor } / charges.size,
                thisYearMinor = maxOf(0L, -txs.filter { monthOf(it).year == today.year }.sumOf { it.amountMinor }),
                byMonth = months.map { it to (grouped[it] ?: 0L) },
                firstAt = txs.minOfOrNull { it.occurredAt },
                lastAt = txs.maxOfOrNull { it.occurredAt },
            )
        }
    }
}
