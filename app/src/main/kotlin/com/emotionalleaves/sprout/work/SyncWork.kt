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
        val chain = inputData.getInt(KEY_CHAIN, 0)
        val result = container.syncRepository.sync(trigger)
        if (result is SyncRunResult.Skipped &&
            (result.reason == SkipReason.REVOKED || result.reason == SkipReason.NEEDS_RECONNECT)
        ) {
            // Stop periodic attempts until a new setup token is saved.
            SyncScheduler.cancelPeriodic(applicationContext)
        }
        return when (val d = decide(result, runAttemptCount, chain)) {
            WorkDecision.Success -> Result.success()
            WorkDecision.Retry -> Result.retry()
            WorkDecision.GiveUp -> Result.failure()
            is WorkDecision.RetryAt -> {
                SyncScheduler.retryAfter(applicationContext, trigger, d.delaySeconds, chain + 1)
                Result.success()
            }
        }
    }

    sealed interface WorkDecision {
        data object Success : WorkDecision
        /** WorkManager's exponential backoff (15 min, 30 min, 60 min). */
        data object Retry : WorkDecision
        data object GiveUp : WorkDecision
        /** The server sent Retry-After: schedule exactly then (at least 15 minutes). */
        data class RetryAt(val delaySeconds: Long) : WorkDecision
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val KEY_CHAIN = "retry_chain"
        /** At most 3 retries per run, whichever way they are scheduled. Never loop. */
        const val MAX_RETRIES = 3
        private const val MIN_RETRY_SECONDS = 15 * 60L
        private const val MAX_RETRY_SECONDS = 6 * 3600L

        fun decide(result: SyncRunResult, runAttemptCount: Int, chain: Int): WorkDecision = when (result) {
            is SyncRunResult.Done, is SyncRunResult.Skipped -> WorkDecision.Success
            is SyncRunResult.RetryLater -> when {
                runAttemptCount + chain >= MAX_RETRIES -> WorkDecision.GiveUp
                result.retryAfterSeconds != null ->
                    WorkDecision.RetryAt(result.retryAfterSeconds.coerceIn(MIN_RETRY_SECONDS, MAX_RETRY_SECONDS))
                else -> WorkDecision.Retry
            }
        }
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

    const val RETRY_WORK = "sprout-sync-retry"

    /** A single delayed retry honoring the server's Retry-After. */
    fun retryAfter(context: Context, trigger: SyncTrigger, delaySeconds: Long, chain: Int) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to trigger.name, SyncWorker.KEY_CHAIN to chain))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RETRY_WORK, ExistingWorkPolicy.KEEP, request)
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
