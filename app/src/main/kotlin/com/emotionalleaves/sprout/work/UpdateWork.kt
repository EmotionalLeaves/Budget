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
                c.updates.download(s.release)
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
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(3, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
