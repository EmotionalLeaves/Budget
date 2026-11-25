package com.emotionalleaves.sprout.domain.insights

import java.time.LocalDate
import java.time.YearMonth

/**
 * Age of Money (as YNAB defines it): how many days passed between money arriving and being spent, averaged over
 * your last [WINDOW] outflows. Money in is used first-in, first-out. Higher means more breathing room.
 * Returns null when there isn't enough history (no inflow before the outflows, or too few outflows).
 */
object AgeOfMoney {
    const val WINDOW = 10

    /** [moves] are (signed cents, epoch seconds) for cash accounts, any order. */
    fun days(moves: List<Pair<Long, Long>>): Int? {
        val sorted = moves.sortedBy { it.second }
        val buckets = ArrayDeque<LongArray>() // [amountLeft, time]
        val ages = mutableListOf<Double>()
        for ((amount, time) in sorted) {
            if (amount > 0) { buckets.addLast(longArrayOf(amount, time)); continue }
            var need = -amount
            var weighted = 0.0
            var covered = 0L
            while (need > 0 && buckets.isNotEmpty()) {
                val b = buckets.first()
                val take = minOf(need, b[0])
                weighted += take.toDouble() * (time - b[1]) / 86_400.0
                covered += take
                b[0] -= take
                need -= take
                if (b[0] == 0L) buckets.removeFirst()
            }
            if (covered > 0) ages += weighted / covered
        }
        if (ages.size < 3) return null
        return ages.takeLast(WINDOW).average().toInt()
    }
}

/**
 * A planned (non-monthly) expense: e.g. "Car registration, $240, due July". How much to set aside each month,
 * counting the current month, so it's covered by the due month.
 */
object PlannedExpense {
    fun monthlySetAside(targetMinor: Long, savedMinor: Long, due: LocalDate, today: LocalDate): Long {
        val remaining = maxOf(0L, targetMinor - savedMinor)
        if (remaining == 0L) return 0L
        val months = (YearMonth.from(due).let { d -> (d.year - today.year) * 12 + (d.monthValue - today.monthValue) } + 1).coerceAtLeast(1)
        return (remaining + months - 1) / months
    }
}

/** A repeating charge whose price changed: "Netflix went up from $15.49 to $17.99". */
data class PriceChange(val name: String, val fromMinor: Long, val toMinor: Long) {
    val up: Boolean get() = toMinor > fromMinor

    companion object {
        /**
         * Compares the two latest charges of a repeating bill (amounts as positive cents, newest last).
         * Changes under 3% or 50¢ are ignored (tax rounding, exchange rates).
         */
        fun detect(name: String, recentAmounts: List<Long>): PriceChange? {
            if (recentAmounts.size < 2) return null
            val prev = recentAmounts[recentAmounts.size - 2]
            val last = recentAmounts.last()
            val diff = kotlin.math.abs(last - prev)
            if (prev <= 0 || diff < 50 || diff * 100 < prev * 3) return null
            return PriceChange(name, prev, last)
        }
    }
}
