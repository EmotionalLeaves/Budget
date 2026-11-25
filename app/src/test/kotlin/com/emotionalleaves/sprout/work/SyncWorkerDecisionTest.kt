package com.emotionalleaves.sprout.work

import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.data.sync.SkipReason
import com.emotionalleaves.sprout.data.sync.SyncRunResult
import com.emotionalleaves.sprout.work.SyncWorker.WorkDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerDecisionTest {
    private fun d(r: SyncRunResult, attempt: Int = 0, chain: Int = 0) = SyncWorker.decide(r, attempt, chain)

    @Test fun doneIsSuccess() = assertEquals(WorkDecision.Success, d(SyncRunResult.Done(SyncResult.FAILED, 0, 0)))
    @Test fun skippedIsSuccess() = assertEquals(WorkDecision.Success, d(SyncRunResult.Skipped(SkipReason.DAILY_LIMIT)))
    @Test fun networkErrorRetriesWithBackoff() = assertEquals(WorkDecision.Retry, d(SyncRunResult.RetryLater(null)))
    @Test fun atMostThreeRetries() = assertEquals(WorkDecision.GiveUp, d(SyncRunResult.RetryLater(null), attempt = 3))
    @Test fun retryAfterIsHonored() = assertEquals(WorkDecision.RetryAt(3600), d(SyncRunResult.RetryLater(3600)))
    @Test fun retryAfterHasAFloor() = assertEquals(WorkDecision.RetryAt(900), d(SyncRunResult.RetryLater(5)))
    @Test fun retryAfterHasACeiling() = assertEquals(WorkDecision.RetryAt(21600), d(SyncRunResult.RetryLater(86_400)))
    @Test fun retryAfterChainStops() = assertEquals(WorkDecision.GiveUp, d(SyncRunResult.RetryLater(60), chain = 3))

    @Test fun batterySaverSkipsRecentScheduledSyncsOnly() {
        val now = 1_763_900_000L
        org.junit.Assert.assertTrue(SyncWorker.skipForBatterySaver(com.emotionalleaves.sprout.data.db.SyncTrigger.PERIODIC, true, now - 3600, now))
        org.junit.Assert.assertFalse(SyncWorker.skipForBatterySaver(com.emotionalleaves.sprout.data.db.SyncTrigger.PERIODIC, true, now - 25 * 3600, now))
        org.junit.Assert.assertFalse(SyncWorker.skipForBatterySaver(com.emotionalleaves.sprout.data.db.SyncTrigger.PERIODIC, true, null, now))
        org.junit.Assert.assertFalse(SyncWorker.skipForBatterySaver(com.emotionalleaves.sprout.data.db.SyncTrigger.PERIODIC, false, now - 3600, now))
        org.junit.Assert.assertFalse(SyncWorker.skipForBatterySaver(com.emotionalleaves.sprout.data.db.SyncTrigger.MANUAL, true, now - 3600, now))
    }
}
