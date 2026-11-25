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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private val Context.reminderStore: DataStore<Preferences> by preferencesDataStore(name = "bill_reminders")

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
