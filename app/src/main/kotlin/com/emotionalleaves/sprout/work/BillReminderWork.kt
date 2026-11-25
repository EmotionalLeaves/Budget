package com.emotionalleaves.sprout.work

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emotionalleaves.sprout.SproutApp
import com.emotionalleaves.sprout.domain.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val Context.reminderStore: DataStore<Preferences> by preferencesDataStore(name = "bill_reminders")

/** "Remind me to cancel": subscriptions you plan to cancel, reminded 3 days before they renew. */
object CancelReminders {
    private val IDS = stringSetPreferencesKey("cancel_ids")
    const val DAYS_BEFORE = 3L

    fun observe(context: Context): kotlinx.coroutines.flow.Flow<Set<Long>> =
        context.reminderStore.data.map { p -> (p[IDS] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet() }

    suspend fun set(context: Context, recurringId: Long, on: Boolean) {
        context.reminderStore.edit { p ->
            val cur = p[IDS] ?: emptySet()
            p[IDS] = if (on) cur + recurringId.toString() else cur - recurringId.toString()
        }
    }

    /** Which planned cancellations to remind about today: renewing in exactly [DAYS_BEFORE] days (or sooner, if missed). */
    fun due(bills: List<Triple<Long, String, Long>>, ids: Set<Long>, today: LocalDate, zone: ZoneId, already: Set<String>): List<Pair<String, String>> =
        bills.mapNotNull { (id, name, dueAt) ->
            if (id !in ids) return@mapNotNull null
            val date = Instant.ofEpochSecond(dueAt).atZone(zone).toLocalDate()
            val key = "cancel:$id@$date"
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
            if (days in 0..DAYS_BEFORE && key !in already) key to "Cancel $name before it renews on ${date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}" else null
        }
}

/** Picks which bills to remind about: due tomorrow and not reminded yet. Pure, for tests. */
object BillReminders {
    data class Due(val key: String, val name: String, val amountMinor: Long)

    fun dueTomorrow(bills: List<Triple<Long, String, Pair<Long, Long>>>, today: LocalDate, zone: ZoneId, already: Set<String>): List<Due> =
        bills.mapNotNull { (id, name, amountAndDue) ->
            val (amount, dueAt) = amountAndDue
            val date = Instant.ofEpochSecond(dueAt).atZone(zone).toLocalDate()
            val key = "$id@$date"
            if (date == today.plusDays(1) && key !in already) Due(key, name, amount) else null
        }
}

/** Once a day (around the morning): a heads-up for bills due tomorrow, if you turned reminders on. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SproutApp).container
        runCatching {
            // Planned cancellations are reminded whether or not bill reminders are on: you asked for these one by one.
            val zone = ZoneId.systemDefault()
            val store = applicationContext.reminderStore
            val ids = CancelReminders.observe(applicationContext).first()
            if (ids.isNotEmpty()) {
                val already = store.data.first()[SENT] ?: emptySet()
                val bills = c.recurringRepository.observeUpcoming(10).first().map { Triple(it.charge.id, it.charge.displayName, it.dueAt) }
                val due = CancelReminders.due(bills, ids, LocalDate.now(zone), zone, already)
                due.forEach { (key, text) -> c.notifier.showCancelReminder(key, text) }
                if (due.isNotEmpty()) store.edit { it[SENT] = (already + due.map { d -> d.first }).toList().takeLast(200).toSet() }
            }
        }
        runCatching {
            // Sunday: "You spent $412 this week, $88 less than last week."
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            if (!c.settings.current().notifyWeekly || today.dayOfWeek != java.time.DayOfWeek.SUNDAY) return@runCatching
            val store = applicationContext.reminderStore
            val key = "week:$today"
            val already = store.data.first()[SENT] ?: emptySet()
            if (key in already) return@runCatching
            val end = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val start = today.minusDays(6).atStartOfDay(zone).toEpochSecond()
            val prevStart = start - 7 * 86_400
            val thisWeek = c.db.insights().spendingByAccount(start, end).sumOf { it.total }
            val lastWeek = c.db.insights().spendingByAccount(prevStart, start).sumOf { it.total }
            if (thisWeek <= 0) return@runCatching
            val big = c.db.insights().biggestCharge(start, end)
            val text = com.emotionalleaves.sprout.domain.insights.WeekSummary.text(
                thisWeek, lastWeek, big?.let { com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer.displayName(it.merchantKey) }, big?.let { -it.amountMinor },
            )
            if (c.notifier.showWeekly(text)) store.edit { it[SENT] = (already + key).toList().takeLast(200).toSet() }
        }
        runCatching {
            if (!c.settings.current().notifyBills) return@runCatching
            val zone = ZoneId.systemDefault()
            val store = applicationContext.reminderStore
            val already = store.data.first()[SENT] ?: emptySet()
            val bills = c.recurringRepository.observeUpcoming(3).first()
                .map { Triple(it.charge.id, it.charge.displayName, it.charge.typicalAmountMinor to it.dueAt) }
            val due = BillReminders.dueTomorrow(bills, LocalDate.now(zone), zone, already)
            due.forEach { c.notifier.showBillReminder(it.key, it.name, Money.format(kotlin.math.abs(it.amountMinor))) }
            if (due.isNotEmpty()) {
                // Keep the last 200 keys so the set never grows without bound.
                store.edit { it[SENT] = (already + due.map { d -> d.key }).toList().takeLast(200).toSet() }
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "sprout-bill-reminders"
        private val SENT = stringSetPreferencesKey("sent")

        /** Daily, first run near 9 a.m. local time. KEEP: an existing schedule is never reset. */
        fun schedule(context: Context) {
            val now = java.time.ZonedDateTime.now()
            var next = now.with(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
