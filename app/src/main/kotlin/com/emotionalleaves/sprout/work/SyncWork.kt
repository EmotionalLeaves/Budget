package com.emotionalleaves.sprout.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emotionalleaves.sprout.SproutApp
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.sync.SkipReason
import com.emotionalleaves.sprout.data.sync.SyncRunResult
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Runs one sync off the main thread. Retries temporary failures with backoff, at most 3 times per run. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SproutApp).container
        val trigger = inputData.getString(KEY_TRIGGER)?.let { name -> SyncTrigger.entries.firstOrNull { it.name == name } }
            ?: SyncTrigger.PERIODIC
        return when (val r = container.syncRepository.sync(trigger)) {
            is SyncRunResult.Done -> Result.success()
            is SyncRunResult.Skipped -> {
                if (r.reason == SkipReason.REVOKED || r.reason == SkipReason.NEEDS_RECONNECT) {
                    SyncScheduler.cancelPeriodic(applicationContext)
                }
                Result.success()
            }
            is SyncRunResult.RetryLater -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val MAX_RETRIES = 3
    }
}

object SyncScheduler {
    const val PERIODIC_WORK = "sprout-periodic-sync"
    const val ONE_TIME_WORK = "sprout-sync-now"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Every 6 hours. KEEP means an existing schedule (which WorkManager persists across reboots and app updates)
     * is never reset. A random initial delay spreads requests away from the top of the hour, as the Bridge asks.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(Random.nextLong(17, 6 * 60L), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to SyncTrigger.PERIODIC.name))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /** One-time sync now. KEEP: a second tap while one is queued or running does nothing. */
    fun syncNow(context: Context, trigger: SyncTrigger) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to trigger.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.KEEP, request)
    }
}
