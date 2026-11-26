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
    /** SimpleFIN Bridge limits each request to 90 days of history, so longer history is fetched in 90-day steps. */
    const val MAX_FIRST_SYNC_DAYS = 90
    const val CHUNK_DAYS = 90
    /** "Everything": about 10 years; in practice the bank stops earlier and Sprout notices (empty steps). */
    const val ALL_HISTORY_DAYS = 3650
    val ALLOWED_FIRST_SYNC_DAYS = listOf(30, 90, 180, 365, 730, ALL_HISTORY_DAYS)
    /** Two empty 90-day steps in a row means the bank has nothing older. */
    const val EMPTY_CHUNKS_TO_STOP = 2
    /** History steps per sync, so one sync stays quick; the rest continue on the next syncs. */
    const val MAX_HISTORY_CHUNKS_PER_SYNC = 4
    /** History never uses the last few of the day's requests: regular syncs keep working. */
    const val HISTORY_REQUEST_BUDGET = 12
    /**
     * A sync you force skips the 15-minute wait and Sprout's own daily cap, but still stops short of the Bridge's
     * 24-a-day limit (past it, SimpleFIN can turn your access token off).
     */
    const val FORCED_REQUEST_BUDGET = 22
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

    /**
     * The next older 90-day step of history to fetch, or null when done. [oldestFetched] is the start of the
     * oldest window fetched so far; [historyDays] how far back you asked for.
     */
    fun nextHistoryChunk(clock: Clock, oldestFetched: Long, historyDays: Int): SyncWindow? {
        val now = clock.instant().epochSecond
        val target = now - Duration.ofDays(historyDays.toLong()).seconds
        if (oldestFetched <= target) return null
        val start = maxOf(target, oldestFetched - Duration.ofDays(CHUNK_DAYS.toLong()).seconds)
        return SyncWindow(start, oldestFetched)
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
