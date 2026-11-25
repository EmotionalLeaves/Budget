package com.emotionalleaves.sprout.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.sync.SkipReason
import com.emotionalleaves.sprout.data.sync.SyncRunResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real worker against a fake sync and a test WorkManager, checking what it returns and schedules. */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var wm: WorkManager
    private val triggers = mutableListOf<SyncTrigger>()

    @Before fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        wm = WorkManager.getInstance(context)
    }

    private fun run(result: SyncRunResult, attempt: Int = 0, chain: Int = 0, trigger: SyncTrigger = SyncTrigger.PERIODIC): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                SyncWorker(appContext, workerParameters) { t -> triggers += t; result }
        }
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .setInputData(workDataOf(SyncWorker.KEY_TRIGGER to trigger.name, SyncWorker.KEY_CHAIN to chain))
            .build()
        return runBlocking { worker.doWork() }
    }

    private fun infos(name: String): List<WorkInfo> = wm.getWorkInfosForUniqueWork(name).get()

    @Test fun successfulSyncSucceedsAndPassesTheTrigger() {
        assertEquals(ListenableWorker.Result.success(), run(SyncRunResult.Done(SyncResult.SUCCESS, 3, 1), trigger = SyncTrigger.MANUAL))
        assertEquals(listOf(SyncTrigger.MANUAL), triggers)
    }

    @Test fun temporaryFailureUsesBackoffThenGivesUp() {
        assertEquals(ListenableWorker.Result.retry(), run(SyncRunResult.RetryLater(null), attempt = 0))
        assertEquals(ListenableWorker.Result.retry(), run(SyncRunResult.RetryLater(null), attempt = 2))
        assertEquals(ListenableWorker.Result.failure(), run(SyncRunResult.RetryLater(null), attempt = 3))
    }

    @Test fun retryAfterSchedulesOneDelayedRetryWithTheChainCount() {
        assertEquals(ListenableWorker.Result.success(), run(SyncRunResult.RetryLater(1800), chain = 1, trigger = SyncTrigger.MANUAL))
        val retry = infos(SyncScheduler.RETRY_WORK)
        assertEquals(1, retry.size)
        assertEquals(WorkInfo.State.ENQUEUED, retry[0].state)
        // A second Retry-After while one is queued keeps the first (no pile-up).
        run(SyncRunResult.RetryLater(1800), chain = 1)
        assertEquals(1, infos(SyncScheduler.RETRY_WORK).size)
    }

    @Test fun retryAfterChainEndsAfterThree() {
        assertEquals(ListenableWorker.Result.failure(), run(SyncRunResult.RetryLater(1800), chain = 3))
        assertTrue(infos(SyncScheduler.RETRY_WORK).isEmpty())
    }

    @Test fun revokedAccessCancelsPeriodicSync() {
        SyncScheduler.schedulePeriodic(context)
        assertEquals(WorkInfo.State.ENQUEUED, infos(SyncScheduler.PERIODIC_WORK).single().state)
        assertEquals(ListenableWorker.Result.success(), run(SyncRunResult.Skipped(SkipReason.REVOKED)))
        assertEquals(WorkInfo.State.CANCELLED, infos(SyncScheduler.PERIODIC_WORK).single().state)
    }

    @Test fun dailyLimitSkipKeepsPeriodicSync() {
        SyncScheduler.schedulePeriodic(context)
        run(SyncRunResult.Skipped(SkipReason.DAILY_LIMIT))
        assertEquals(WorkInfo.State.ENQUEUED, infos(SyncScheduler.PERIODIC_WORK).single().state)
    }

    @Test fun schedulingTwiceKeepsTheExistingSchedule() {
        SyncScheduler.schedulePeriodic(context)
        val id = infos(SyncScheduler.PERIODIC_WORK).single().id
        SyncScheduler.schedulePeriodic(context)
        assertEquals(id, infos(SyncScheduler.PERIODIC_WORK).single().id)
        SyncScheduler.syncNow(context, SyncTrigger.MANUAL)
        SyncScheduler.syncNow(context, SyncTrigger.MANUAL)
        assertEquals(1, infos(SyncScheduler.ONE_TIME_WORK).size)
    }
}
