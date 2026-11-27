package com.emotionalleaves.sprout.data.recurring

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.RecurringChargeEntity
import com.emotionalleaves.sprout.data.db.RecurringRow
import com.emotionalleaves.sprout.data.db.RecurringStatus
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.budget.ExpectedCharge
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.recurring.Cadence
import com.emotionalleaves.sprout.domain.recurring.ChargeTx
import com.emotionalleaves.sprout.domain.recurring.RecurringDetector
import com.emotionalleaves.sprout.domain.recurring.Schedule
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One expected charge on a date. */
data class UpcomingBill(val charge: RecurringRow, val dueAt: Long)

/** A bill on a calendar day: already paid this month, or still expected. */
data class CalendarBill(val date: LocalDate, val name: String, val amountMinor: Long, val paid: Boolean)

class RecurringRepository(
    private val db: SproutDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private fun now() = clock.instant().epochSecond
    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone()))
    private fun date(epoch: Long): LocalDate = Instant.ofEpochSecond(epoch).atZone(zone()).toLocalDate()
    private fun epoch(d: LocalDate): Long = d.atStartOfDay(zone()).plusHours(12).toEpochSecond()

    /**
     * Re-runs detection over the last 400 days. Never resurrects dismissed charges and never overwrites what the
     * user edited (only the last-charge date and next due date move forward).
     */
    suspend fun detect() = withContext(Dispatchers.IO) {
        val now = now()
        val rows = db.recurring().outflowsSince(now - RecurringDetector.LOOKBACK_DAYS * 86_400)
        val found = RecurringDetector.detect(
            rows.map { ChargeTx(it.merchantKey, it.amountMinor, it.occurredAt, it.categoryId, it.accountId) }, now, zone(),
        )
        db.withTransaction {
            val existing = db.recurring().getAll().groupBy { it.merchantKey }.mapValues { it.value.first() }
            val foundKeys = found.map { it.merchantKey }.toSet()
            for (d in found) {
                val e = existing[d.merchantKey]
                val id = when {
                    e == null -> db.recurring().insert(
                        RecurringChargeEntity(
                            merchantKey = d.merchantKey, displayName = MerchantNormalizer.displayName(d.merchantKey),
                            categoryId = d.categoryId, accountId = d.accountId, cadence = d.cadence,
                            typicalAmountMinor = d.typicalAmountMinor, lastChargeAt = d.lastChargeAt, nextDueAt = d.nextDueAt,
                            status = RecurringStatus.DETECTED, userEdited = false, confidence = d.confidence,
                            occurrences = d.occurrences, updatedAt = now,
                        ),
                    )
                    e.status == RecurringStatus.DISMISSED -> continue
                    e.userEdited || e.status == RecurringStatus.MANUAL -> {
                        val next = if (d.lastChargeAt > e.lastChargeAt) epoch(Schedule.next(date(d.lastChargeAt), e.cadence)) else e.nextDueAt
                        db.recurring().update(e.copy(lastChargeAt = maxOf(e.lastChargeAt, d.lastChargeAt), nextDueAt = next,
                            occurrences = d.occurrences, updatedAt = now))
                        e.id
                    }
                    else -> {
                        db.recurring().update(e.copy(cadence = d.cadence, typicalAmountMinor = d.typicalAmountMinor,
                            lastChargeAt = d.lastChargeAt, nextDueAt = d.nextDueAt, confidence = d.confidence,
                            occurrences = d.occurrences, categoryId = d.categoryId, accountId = d.accountId, updatedAt = now))
                        e.id
                    }
                }
                db.recurring().linkTransactions(id, d.merchantKey)
            }
            // Suggestions that no longer hold up disappear; confirmed and manual ones stay and keep rolling forward.
            val stale = existing.values.filter { it.status == RecurringStatus.DETECTED && it.merchantKey !in foundKeys }.map { it.id }
            if (stale.isNotEmpty()) {
                db.recurring().unlink(stale)
                db.recurring().delete(stale)
            }
            val graceStart = today().minusDays(GRACE_DAYS)
            for (e in existing.values) {
                if (e.status != RecurringStatus.CONFIRMED && e.status != RecurringStatus.MANUAL) continue
                var next = date(e.nextDueAt)
                var guard = 0
                while (next.isBefore(graceStart) && guard++ < 200) next = Schedule.next(next, e.cadence)
                if (epoch(next) != e.nextDueAt) db.recurring().update(e.copy(nextDueAt = epoch(next), updatedAt = now))
            }
        }
    }

    fun observeActive(): Flow<List<RecurringRow>> = db.recurring().observeActive()

    /** Every due date in the next [days] days, soonest first. A charge that's a few days late still shows, as today. */
    fun observeUpcoming(days: Long = 30): Flow<List<UpcomingBill>> = observeActive().map { list ->
        val today = today()
        val to = today.plusDays(days)
        list.flatMap { r ->
            val first = date(r.nextDueAt).let { if (it.isBefore(today) && !it.isBefore(today.minusDays(GRACE_DAYS))) today else it }
            Schedule.occurrences(first, r.cadence, today, to, date(r.nextDueAt).dayOfMonth).map { UpcomingBill(r, epoch(it)) }
        }.sortedBy { it.dueAt }
    }

    /** Expected charges for safe-to-spend in [month]: what's still due (unmatched) plus what's already paid. */
    fun expectedCharges(month: YearMonth): Flow<List<ExpectedCharge>> = observeActive().map { list ->
        val window = MonthWindow.of(month, zone())
        val today = today()
        val from = maxOf(month.atDay(1), today)
        val to = month.plusMonths(1).atDay(1)
        list.flatMap { r ->
            val paid = if (r.lastChargeAt in window) listOf(ExpectedCharge(r.displayName, r.categoryId, r.typicalAmountMinor, r.lastChargeAt, true)) else emptyList()
            val first = date(r.nextDueAt).let { if (it.isBefore(today) && !it.isBefore(today.minusDays(GRACE_DAYS))) today else it }
            paid + Schedule.occurrences(first, r.cadence, from, to, date(r.nextDueAt).dayOfMonth)
                .map { ExpectedCharge(r.displayName, r.categoryId, r.typicalAmountMinor, epoch(it), false) }
        }
    }

    /** Bills for a month view: paid ones on the day they posted, expected ones on their due dates. */
    fun observeCalendar(month: YearMonth): Flow<List<CalendarBill>> = observeActive().map { list ->
        val window = MonthWindow.of(month, zone())
        val today = today()
        val from = month.atDay(1)
        val to = month.plusMonths(1).atDay(1)
        list.flatMap { r ->
            val paid = if (r.lastChargeAt in window) listOf(CalendarBill(date(r.lastChargeAt), r.displayName, r.typicalAmountMinor, true)) else emptyList()
            val next = date(r.nextDueAt)
            val expected = Schedule.occurrences(next, r.cadence, maxOf(from, if (month == YearMonth.from(today)) minOf(today, next) else from), to, next.dayOfMonth)
                .map { CalendarBill(it, r.displayName, r.typicalAmountMinor, false) }
            paid + expected
        }.sortedBy { it.date }
    }

    suspend fun confirm(id: Long) = setStatus(id, RecurringStatus.CONFIRMED)

    /** Dismissed charges never come back for that merchant. Returns an undo that restores the bill and its links. */
    suspend fun dismiss(id: Long): suspend () -> Unit = withContext(Dispatchers.IO) {
        val before = db.recurring().getById(id)
        val linked = db.recurring().linkedIds(id)
        db.recurring().unlink(listOf(id))
        setStatus(id, RecurringStatus.DISMISSED)
        val undo: suspend () -> Unit = {
            withContext(Dispatchers.IO) {
                before?.let { b ->
                    db.recurring().update(b.copy(updatedAt = now()))
                    linked.chunked(500).forEach { db.recurring().relink(id, it) }
                }
            }
        }
        undo
    }

    suspend fun edit(id: Long, amountMinor: Long, cadence: Cadence, nextDue: LocalDate, categoryId: Long) = withContext(Dispatchers.IO) {
        val e = db.recurring().getById(id) ?: return@withContext
        db.recurring().update(e.copy(typicalAmountMinor = amountMinor.coerceAtLeast(0), cadence = cadence, nextDueAt = epoch(nextDue),
            categoryId = categoryId, userEdited = true,
            status = if (e.status == RecurringStatus.DETECTED) RecurringStatus.CONFIRMED else e.status, updatedAt = now()))
    }

    suspend fun addManual(name: String, amountMinor: Long, cadence: Cadence, nextDue: LocalDate, categoryId: Long): Long = withContext(Dispatchers.IO) {
        val clean = Sanitize.text(name, 60).ifBlank { "Bill" }
        val key = MerchantNormalizer.normalize(clean)
        db.withTransaction {
            val existing = db.recurring().getAll().firstOrNull { it.merchantKey == key }
            val entity = RecurringChargeEntity(
                id = existing?.id ?: 0, merchantKey = key, displayName = clean, categoryId = categoryId, accountId = null,
                cadence = cadence, typicalAmountMinor = amountMinor.coerceAtLeast(0), lastChargeAt = existing?.lastChargeAt ?: 0,
                nextDueAt = epoch(nextDue), status = RecurringStatus.MANUAL, userEdited = true, confidence = 1f,
                occurrences = existing?.occurrences ?: 0, updatedAt = now(),
            )
            if (existing != null) { db.recurring().update(entity); existing.id } else db.recurring().insert(entity)
        }
    }

    private suspend fun setStatus(id: Long, status: RecurringStatus) = withContext(Dispatchers.IO) {
        val e = db.recurring().getById(id) ?: return@withContext
        db.recurring().update(e.copy(status = status, updatedAt = now()))
    }

    companion object {
        /** A bill this many days late is still treated as due (banks post late). */
        const val GRACE_DAYS = 5L
    }
}
