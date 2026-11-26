package com.emotionalleaves.sprout.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SyncPolicyTest {
    private val now = 1_764_000_000L
    private val clock = Clock.fixed(Instant.ofEpochSecond(now), ZoneId.of("America/Chicago"))
    private val day = 86_400L

    @Test fun firstSyncUsesChosenDays() {
        assertEquals(SyncWindow(now - 90 * day, now + day), SyncPolicy.window(clock, null, 90))
        assertEquals(now - 30 * day, SyncPolicy.window(clock, null, 30).startDate)
    }

    @Test fun firstSyncNeverExceeds90Days() = assertEquals(now - 90 * day, SyncPolicy.window(clock, null, 365).startDate)

    @Test fun laterSyncOverlapsTenDays() =
        assertEquals(now - 2 * day - 10 * day, SyncPolicy.window(clock, now - 2 * day, 90).startDate)

    @Test fun laterSyncCappedAt60DaysBack() =
        assertEquals(now - 60 * day, SyncPolicy.window(clock, now - 200 * day, 90).startDate)

    @Test fun endDateIsNowPlusOneDay() = assertEquals(now + day, SyncPolicy.window(clock, now - day, 90).endDate)

    @Test fun debounceWithin15Minutes() {
        assertTrue(SyncPolicy.isDebounced(clock, now - 14 * 60))
        assertFalse(SyncPolicy.isDebounced(clock, now - 15 * 60))
        assertFalse(SyncPolicy.isDebounced(clock, null))
    }

    @Test fun ids() {
        assertEquals("sf:CON-1:ACT-9", Ids.accountKey("CON-1", "ACT-9"))
        assertEquals("sf:CON-1:ACT-9:TX-5", Ids.transactionId("sf:CON-1:ACT-9", "TX-5"))
    }

    @Test fun historyStepsAre90DaysAndStopAtTheSetting() {
        assertEquals(SyncWindow(now - 180 * day, now - 90 * day), SyncPolicy.nextHistoryChunk(clock, now - 90 * day, 365))
        assertEquals(SyncWindow(now - 365 * day, now - 300 * day), SyncPolicy.nextHistoryChunk(clock, now - 300 * day, 365))
        assertEquals(null, SyncPolicy.nextHistoryChunk(clock, now - 365 * day, 365))
    }
}
