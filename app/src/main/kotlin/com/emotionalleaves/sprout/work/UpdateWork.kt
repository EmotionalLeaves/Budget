package com.emotionalleaves.sprout.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emotionalleaves.sprout.SproutApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Once a day on an unmetered-or-any network: is there a newer Sprout? Quiet on every failure. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SproutApp).container
        runCatching {
            val v = c.updates.checkForNotification() ?: return@runCatching
            val cm = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
            val unmetered = cm != null && !cm.isActiveNetworkMetered
            val s = c.updates.state.value
            if (unmetered && c.updates.hasKey && c.updates.autoDownload.first() && s is com.emotionalleaves.sprout.data.update.UpdateState.Available) {
                downloadWithProgress(c, s.release)
            }
            val ready = c.updates.state.value as? com.emotionalleaves.sprout.data.update.UpdateState.ReadyToInstall
            if (ready != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && c.updates.autoInstall.first() && c.updates.canInstall()) {
                // Hands-free: Android installs it without asking when Sprout installed the current version itself;
                // otherwise the receiver shows a "tap to finish" notification.
                if (com.emotionalleaves.sprout.data.update.UpdateInstaller.install(applicationContext, ready.file)) return@runCatching
            }
            c.notifier.showUpdate(v, ready = ready != null)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "sprout-update-check"

        fun schedule(context: Context) {
            // Twice a day, so a fix reaches the phone the same day it ships. UPDATE keeps the existing schedule's timing.
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(3, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

/**
 * "Update while charging": the next time the phone is plugged in and online, download (if needed) and install.
 * On Android 12+ that happens without a tap; older phones get a "tap to finish" notification.
 */
class UpdateWhenChargingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SproutApp).container
        runCatching {
            if (c.updates.state.value !is com.emotionalleaves.sprout.data.update.UpdateState.ReadyToInstall) {
                c.updates.check(quiet = true)
                (c.updates.state.value as? com.emotionalleaves.sprout.data.update.UpdateState.Available)?.let { downloadWithProgress(c, it.release) }
            }
            val ready = c.updates.state.value as? com.emotionalleaves.sprout.data.update.UpdateState.ReadyToInstall ?: return@runCatching
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && c.updates.canInstall() &&
                com.emotionalleaves.sprout.data.update.UpdateInstaller.install(applicationContext, ready.file)
            ) return@runCatching
            c.notifier.showUpdate(ready.release.version, ready = true)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "sprout-update-when-charging"

        fun schedule(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<UpdateWhenChargingWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }

        /** True while a "while charging" update is waiting (survives restarts: WorkManager keeps it). */
        fun observeQueued(context: Context): kotlinx.coroutines.flow.Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NAME)
                .let { f -> kotlinx.coroutines.flow.flow { f.collect { list -> emit(list.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.BLOCKED }) } } }
    }
}

/** Background download with a silent progress notification (updated every 5%), cleared when done. */
internal suspend fun downloadWithProgress(c: com.emotionalleaves.sprout.AppContainer, release: com.emotionalleaves.sprout.update.ReleaseInfo) =
    kotlinx.coroutines.coroutineScope {
        val watcher = launch {
            var last = -5
            c.updates.state.collect { st ->
                if (st is com.emotionalleaves.sprout.data.update.UpdateState.Downloading && st.percent >= last + 5) {
                    last = st.percent
                    c.notifier.showUpdateProgress(release.version, st.percent)
                }
            }
        }
        try { c.updates.download(release) } finally {
            watcher.cancel()
            c.notifier.showUpdateProgress(release.version, null)
        }
    }
