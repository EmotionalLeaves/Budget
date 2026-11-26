package com.emotionalleaves.sprout.ui.common

import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.launch

object SyncActions {
    /** When the last "Synced 3 min ago" answer was given; asking again soon after forces a sync. */
    @Volatile private var debouncedAt = 0L

    /**
     * "Sync now". Debounced: within 15 minutes of a success it only says how fresh the data is, unless
     * [force] is set or you ask again within a minute ("pull again to sync anyway").
     * Returns a short message for a snackbar, or null when a sync was started.
     */
    suspend fun requestSync(c: AppContainer, force: Boolean = false): String? {
        if (!c.secureStore.hasAccessUrlNow()) return "No bank connected. Connect one in Settings."
        val s = c.settings.current()
        if (s.accessRevoked) return "SimpleFIN access was turned off. Paste a new setup token in Settings."
        val until = s.rateLimitedUntil
        if (until != null && until > c.clock.instant().epochSecond) {
            return "Paused after SimpleFIN asked Sprout to slow down. Syncing resumes in a few hours."
        }
        val last = s.lastSuccessfulSyncAt
        val now = c.clock.instant().epochSecond
        val again = now - debouncedAt in 0..60
        if (!force && !again && SyncPolicy.isDebounced(c.clock, last) && last != null) {
            debouncedAt = now
            return "Synced ${Format.ago(last, now)} · pull again to sync anyway"
        }
        debouncedAt = 0
        SyncScheduler.syncNow(c.appContext, if (force || again) SyncTrigger.FORCED else SyncTrigger.MANUAL)
        return null
    }
}

/** A snackbar message with an optional "Undo" action. */
fun kotlinx.coroutines.CoroutineScope.showUndoable(
    snackbar: androidx.compose.material3.SnackbarHostState,
    message: String,
    undo: (suspend () -> Unit)?,
) = launch {
    val result = snackbar.showSnackbar(
        message,
        actionLabel = if (undo != null) "Undo" else null,
        duration = if (undo != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
    )
    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) undo?.invoke()
}
