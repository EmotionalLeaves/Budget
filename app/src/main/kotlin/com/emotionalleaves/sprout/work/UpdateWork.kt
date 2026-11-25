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

/** Once a day on an unmetered-or-any network: is there a newer Sprout? Quiet on every failure. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SproutApp).container
        runCatching { c.updates.checkForNotification()?.let { c.notifier.showUpdate(it) } }
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
