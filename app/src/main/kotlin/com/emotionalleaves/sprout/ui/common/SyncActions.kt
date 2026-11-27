package com.emotionalleaves.sprout.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.map
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

/**
 * Pull down to sync, on any screen. Shows the spinner while a sync runs and a short note when Sprout
 * answers instead of syncing ("Synced 3 min ago — pull again to sync anyway").
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
fun SyncPullBox(modifier: Modifier = Modifier, content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val c = appContainer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val syncing by androidx.compose.runtime.remember {
        androidx.work.WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWorkFlow(com.emotionalleaves.sprout.work.SyncScheduler.ONE_TIME_WORK)
            .map { list -> list.any { it.state == androidx.work.WorkInfo.State.RUNNING } }
    }.collectAsState(initial = false)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = syncing,
        onRefresh = {
            scope.launch {
                SyncActions.requestSync(c)?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
            }
        },
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}
