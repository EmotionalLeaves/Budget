package com.emotionalleaves.sprout.domain.recurring

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Cadence(val nominalDays: Int, val minDays: Int, val maxDays: Int) {
    WEEKLY(7, 5, 9),
    BIWEEKLY(14, 11, 17),
    MONTHLY(30, 28, 33),
    QUARTERLY(91, 84, 98),
    YEARLY(365, 350, 380),
    ;

    fun fits(days: Long) = days in minDays..maxDays
}

/** One past outflow, as detection sees it. [amountMinor] is negative (money out). */
data class ChargeTx(
    val merchantKey: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val categoryId: Long,
    val accountId: String?,
)

data class DetectedRecurring(
    val merchantKey: String,
    val cadence: Cadence,
    /** Positive: the expected charge, taken from the latest one so price increases follow through. */
    val typicalAmountMinor: Long,
    val lastChargeAt: Long,
    val nextDueAt: Long,
    /** 0..1 from how regular the intervals and amounts are. */
    val confidence: Float,
    val occurrences: Int,
    val categoryId: Long,
    val accountId: String?,
)

/**
 * Finds subscriptions and bills in the last 400 days of outflows. Pure and deterministic.
 *
 * Rules: ≥3 charges per merchant (2 is enough for YEARLY when 330–400 days apart); ≥75% of intervals fit one
 * cadence; every amount within ±15% or ±$3 of the median; charges that stopped (no charge for over two cycles)
 * are dropped.
 */
object RecurringDetector {
    const val LOOKBACK_DAYS = 400L
    const val AMOUNT_TOLERANCE_PERCENT = 15L
    const val AMOUNT_TOLERANCE_MINOR = 300L
    private const val MIN_FIT_PERCENT = 75

    /**
     * Regular income (paychecks): the same rules applied to inflows. Returned amounts are positive, like bills.
     */
    fun detectIncome(inflows: List<ChargeTx>, now: Long, zone: ZoneId): List<DetectedRecurring> =
        detect(inflows.filter { it.amountMinor > 0 }.map { it.copy(amountMinor = -it.amountMinor) }, now, zone)

    fun detect(outflows: List<ChargeTx>, now: Long, zone: ZoneId): List<DetectedRecurring> {
        val from = now - LOOKBACK_DAYS * 86_400
        return outflows
            .filter { it.amountMinor < 0 && it.occurredAt in from..now }
            .groupBy { it.merchantKey }
            .mapNotNull { (_, list) -> detectOne(list.sortedBy { it.occurredAt }, now, zone) }
            .sortedBy { it.nextDueAt }
    }

    private fun detectOne(charges: List<ChargeTx>, now: Long, zone: ZoneId): DetectedRecurring? {
        // Several charges on one day (split payments, retries) count once for timing.
        val byDay = charges.groupBy { day(it.occurredAt, zone) }.toSortedMap()
        val days = byDay.keys.toList()
        if (days.size < 2) return null
        val intervals = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }

        val cadence: Cadence
        val fitCount: Int
        if (days.size == 2) {
            if (intervals.single() !in 330L..400L) return null
            cadence = Cadence.YEARLY
            fitCount = 1
        } else {
            val best = Cadence.entries.map { c -> c to intervals.count { c.fits(it) } }.maxByOrNull { it.second } ?: return null
            if (best.second * 100 < intervals.size * MIN_FIT_PERCENT) return null
            cadence = best.first
            fitCount = best.second
        }

        val amounts = byDay.values.map { list -> abs(list.sumOf { it.amountMinor }) }
        val median = median(amounts)
        if (amounts.any { !withinTolerance(it, median) }) return null

        val lastDay = days.last()
        val nowDay = day(now, zone)
        // Stopped: more than two full cycles with no charge.
        if (ChronoUnit.DAYS.between(lastDay, nowDay) > cadence.maxDays * 2L) return null

        val anchorDay = anchorDayOfMonth(days.takeLast(3))
        val next = Schedule.next(lastDay, cadence, anchorDay)
        val latest = byDay.getValue(lastDay)
        val intervalScore = fitCount.toFloat() / intervals.size
        val amountScore = if (median == 0L) 0f else
            1f - (amounts.map { abs(it - median).toFloat() / median }.average().toFloat() / (AMOUNT_TOLERANCE_PERCENT / 100f)).coerceIn(0f, 1f)
        return DetectedRecurring(
            merchantKey = charges.first().merchantKey,
            cadence = cadence,
            typicalAmountMinor = amounts.last(),
            lastChargeAt = charges.last().occurredAt,
            nextDueAt = next.atStartOfDay(zone).plusHours(12).toEpochSecond(),
            confidence = (0.6f * intervalScore + 0.4f * amountScore).coerceIn(0f, 1f),
            occurrences = days.size,
            categoryId = latest.last().categoryId,
            accountId = latest.last().accountId,
        )
    }

    fun withinTolerance(amount: Long, median: Long): Boolean {
        val diff = abs(amount - median)
        return diff <= AMOUNT_TOLERANCE_MINOR || diff * 100 <= median * AMOUNT_TOLERANCE_PERCENT
    }

    /** Charges near the end of a month (e.g. Jan 31, Feb 28, Mar 31) keep their end-of-month anchor. */
    fun anchorDayOfMonth(recent: List<LocalDate>): Int {
        val last = recent.last().dayOfMonth
        return if (recent.any { it.dayOfMonth >= 28 }) maxOf(last, recent.maxOf { it.dayOfMonth }) else last
    }

    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun day(epoch: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
}

object Schedule {
    /**
     * The next due date after [last]. Monthly, quarterly and yearly keep [anchorDay] and clamp it to the month's
     * length (Jan 31 → Feb 28, or Feb 29 in a leap year).
     */
    fun next(last: LocalDate, cadence: Cadence, anchorDay: Int = last.dayOfMonth): LocalDate = when (cadence) {
        Cadence.WEEKLY -> last.plusDays(7)
        Cadence.BIWEEKLY -> last.plusDays(14)
        Cadence.MONTHLY -> clamp(last.withDayOfMonth(1).plusMonths(1), anchorDay)
        Cadence.QUARTERLY -> clamp(last.withDayOfMonth(1).plusMonths(3), anchorDay)
        Cadence.YEARLY -> clamp(last.withDayOfMonth(1).plusYears(1), anchorDay)
    }

    private fun clamp(firstOfMonth: LocalDate, day: Int): LocalDate = firstOfMonth.withDayOfMonth(minOf(day, firstOfMonth.lengthOfMonth()))

    /** Due dates from [firstDue] onward that fall in [from, to). */
    fun occurrences(firstDue: LocalDate, cadence: Cadence, from: LocalDate, to: LocalDate, anchorDay: Int = firstDue.dayOfMonth): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = firstDue
        var guard = 0
        while (d.isBefore(to) && guard++ < 400) {
            if (!d.isBefore(from)) out += d
            d = next(d, cadence, anchorDay)
        }
        return out
    }
}
