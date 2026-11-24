package com.emotionalleaves.sprout.domain.sync

import java.time.Clock
import java.time.Duration

data class SyncWindow(val startDate: Long, val endDate: Long)

object SyncPolicy {
    /** Later syncs re-read this much before the last success, to catch late-posting and edited transactions. */
    val OVERLAP: Duration = Duration.ofDays(10)
    /** Later syncs never look back further than this. */
    val MAX_INCREMENTAL_LOOKBACK: Duration = Duration.ofDays(60)
    /** end-date is exclusive on the server, so ask one day past now to include everything up to this moment. */
    val END_PADDING: Duration = Duration.ofDays(1)
    /** SimpleFIN Bridge limits each request to 90 days of history. */
    const val MAX_FIRST_SYNC_DAYS = 90
    val ALLOWED_FIRST_SYNC_DAYS = listOf(30, 60, 90)
    /** Manual "Sync now" within this time of a success shows "Synced X min ago" instead of calling the server. */
    val MANUAL_DEBOUNCE: Duration = Duration.ofMinutes(15)
    /**
     * The Bridge expects at most 24 requests per day. We stop at 16 in any rolling 24 hours (periodic sync uses 4),
     * leaving room for a v1 fallback request and staying well clear of the limit that disables access tokens.
     */
    const val DAILY_REQUEST_BUDGET = 16

    fun window(clock: Clock, lastSuccessfulSync: Long?, firstSyncDays: Int): SyncWindow {
        val now = clock.instant().epochSecond
        val end = now + END_PADDING.seconds
        val start = if (lastSuccessfulSync == null || lastSuccessfulSync <= 0) {
            val days = firstSyncDays.coerceIn(1, MAX_FIRST_SYNC_DAYS)
            now - Duration.ofDays(days.toLong()).seconds
        } else {
            val wanted = minOf(lastSuccessfulSync, now) - OVERLAP.seconds
            maxOf(wanted, now - MAX_INCREMENTAL_LOOKBACK.seconds)
        }
        return SyncWindow(start, end)
    }

    /** True when a manual sync should be skipped because the last success is recent. */
    fun isDebounced(clock: Clock, lastSuccessfulSync: Long?): Boolean {
        if (lastSuccessfulSync == null) return false
        val age = clock.instant().epochSecond - lastSuccessfulSync
        return age in 0 until MANUAL_DEBOUNCE.seconds
    }
}

object Ids {
    fun accountKey(connId: String, accountId: String): String = "sf:$connId:$accountId"
    fun transactionId(accountKey: String, remoteTxId: String): String = "$accountKey:$remoteTxId"
}
