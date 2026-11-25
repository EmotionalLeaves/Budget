package com.emotionalleaves.sprout.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.SproutApp
import java.io.File

/**
 * Installs an update without leaving Sprout: the APK is streamed into an Android PackageInstaller session.
 * Android shows its own short confirmation on top of Sprout (and, on Android 12+, skips it once Sprout itself
 * installed the previous update). Android checks the signature: an APK not signed with Sprout's key is refused.
 */
object UpdateInstaller {
    const val ACTION_STATUS = "com.emotionalleaves.sprout.UPDATE_INSTALL_STATUS"

    /** Starts the install. Returns false if the session couldn't be created (then fall back to the viewer intent). */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("sprout.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Explicit intent to our own non-exported receiver; mutable because the system adds the status extras.
            val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
        true
    }.getOrDefault(false)
}

/** Install results. "Needs your OK" opens Android's confirmation over Sprout; failures show on the update card. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        val updates = (context.applicationContext as SproutApp).container.updates
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { c ->
                    // In the foreground this opens Android's confirmation over Sprout; from the background Android
                    // may block it, so a notification lets you finish with one tap.
                    runCatching { context.startActivity(c) }
                    (context.applicationContext as SproutApp).container.notifier.showFinishInstall(c)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts Sprout's process; MyPackageReplacedReceiver takes over.
            else -> updates.installFailed()
        }
    }
}

/** After an update installs, offer to reopen Sprout with one tap (Android closes the app while it updates). */
class MyPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val c = (context.applicationContext as SproutApp).container
        c.notifier.showUpdated(com.emotionalleaves.sprout.BuildConfig.VERSION_NAME)
    }
}
