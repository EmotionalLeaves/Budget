package com.emotionalleaves.sprout.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.R

/**
 * Posts notifications. Amounts never show on the lock screen: every notification is VISIBILITY_PRIVATE with a
 * redacted public version. Taps open the related category or transaction (immutable PendingIntents).
 */
class AndroidNotifier(private val context: Context) : Notifier {

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "When a category reaches 80% or 100% of its budget" },
                NotificationChannel(CH_UNUSUAL, "Unusual transactions", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Large charges and charges unusual for a merchant" },
                NotificationChannel(CH_SYNC, "Sync problems", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "When a bank connection needs your attention" },
                NotificationChannel(CH_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "The day before a bill is due" },
                NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "When a new version of Sprout is ready" },
            ),
        )
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun show(alert: SproutAlert): Boolean {
        if (!canPost()) return false
        val (channel, publicTitle, id) = when (alert) {
            is SproutAlert.Budget -> Triple(CH_BUDGET, "Sprout: new budget alert", "b:${alert.categoryId}:${alert.yearMonth}:${alert.threshold}".hashCode())
            is SproutAlert.Transaction -> Triple(CH_UNUSUAL, "Sprout: new transaction alert", "t:${alert.transactionId}".hashCode())
            is SproutAlert.SyncTrouble -> Triple(CH_SYNC, "Sprout: a connection needs attention", "s:${alert.key}".hashCode())
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (alert) {
                is SproutAlert.Budget -> putExtra(EXTRA_CATEGORY, alert.categoryId)
                is SproutAlert.Transaction -> putExtra(EXTRA_TRANSACTION, alert.transactionId)
                is SproutAlert.SyncTrouble -> putExtra(EXTRA_SETTINGS, true)
            }
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle(publicTitle)
            .build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** "Sprout X is ready": one quiet notification per version. Tapping opens Home, where the update card is. */
    fun showUpdate(version: String, ready: Boolean = false): Boolean {
        if (!canPost()) return false
        val id = "u:$version".hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Sprout $version is ready")
            .setContentText(if (ready) "Downloaded. Open Sprout and tap Install. Your data stays." else "Open Sprout and tap Update. Your data stays.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** "Cancel Netflix before it renews on Dec 3" — only for subscriptions you marked. Opens Sprout. */
    fun showCancelReminder(key: String, text: String): Boolean {
        if (!canPost()) return false
        val id = key.hashCode()
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CH_BILLS)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Planned cancellation")
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try { NotificationManagerCompat.from(context).notify(id, n); true } catch (e: SecurityException) { false }
    }

    /** A quiet, silent progress bar while an update downloads in the background. Pass null to clear it. */
    fun showUpdateProgress(version: String, percent: Int?) {
        val id = "u-progress".hashCode()
        if (percent == null) { runCatching { NotificationManagerCompat.from(context).cancel(id) }; return }
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Downloading Sprout $version")
            .setContentText("$percent% · your data stays")
            .setProgress(100, percent, percent <= 0)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    /** "Netflix is due tomorrow · $15.49". Amount hidden on the lock screen. */
    fun showBillReminder(key: String, name: String, amountText: String): Boolean {
        if (!canPost()) return false
        val id = "bill:$key".hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, CH_BILLS).setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Sprout: a bill is due tomorrow").build()
        val n = NotificationCompat.Builder(context, CH_BILLS)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("$name is due tomorrow")
            .setContentText(amountText)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** "Tap to finish updating Sprout": wraps Android's install confirmation for when it can't open by itself. */
    fun showFinishInstall(confirm: Intent): Boolean {
        if (!canPost()) return false
        val id = "finish-install".hashCode()
        val pending = PendingIntent.getActivity(context, id, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Tap to finish updating Sprout")
            .setContentText("The new version is downloaded. One tap to install; your data stays.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** "Sprout updated to X · tap to open" right after an in-app update installs. */
    fun showUpdated(version: String): Boolean {
        if (!canPost()) return false
        val id = "updated".hashCode()
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle("Sprout updated to $version")
            .setContentText("Tap to open. Your data is all there.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CH_BILLS = "bill_reminders"
        const val CH_UPDATES = "app_updates"
        const val CH_BUDGET = "budget_alerts"
        const val CH_UNUSUAL = "unusual_transactions"
        const val CH_SYNC = "sync_problems"
        const val EXTRA_CATEGORY = "com.emotionalleaves.sprout.OPEN_CATEGORY"
        const val EXTRA_TRANSACTION = "com.emotionalleaves.sprout.OPEN_TRANSACTION"
        const val EXTRA_SETTINGS = "com.emotionalleaves.sprout.OPEN_SETTINGS"
    }
}
