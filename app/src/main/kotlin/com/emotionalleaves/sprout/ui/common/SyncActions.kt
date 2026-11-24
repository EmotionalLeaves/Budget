package com.emotionalleaves.sprout.ui.common

import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.launch

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
