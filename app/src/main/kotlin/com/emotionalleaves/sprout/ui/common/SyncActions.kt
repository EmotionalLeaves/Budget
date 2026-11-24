package com.emotionalleaves.sprout.ui.common

import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import com.emotionalleaves.sprout.work.SyncScheduler

object SyncActions {
    /**
     * "Sync now". Debounced: within 15 minutes of a success it only says how fresh the data is.
     * Returns a short message for a snackbar, or null when a sync was started.
     */
    suspend fun requestSync(c: AppContainer): String? {
        if (!c.secureStore.hasAccessUrlNow()) return "No bank connected. Connect one in Settings."
        val s = c.settings.current()
        if (s.accessRevoked) return "SimpleFIN access was turned off. Paste a new setup token in Settings."
        val until = s.rateLimitedUntil
        if (until != null && until > c.clock.instant().epochSecond) {
            return "Paused after SimpleFIN asked Sprout to slow down. Syncing resumes in a few hours."
        }
        val last = s.lastSuccessfulSyncAt
        if (SyncPolicy.isDebounced(c.clock, last) && last != null) {
            return "Synced ${Format.ago(last, c.clock.instant().epochSecond)}"
        }
        SyncScheduler.syncNow(c.appContext, SyncTrigger.MANUAL)
        return null
    }
}
