package com.emotionalleaves.sprout.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * When to ask for the phone's screen lock. Pure so it can be tested.
 * Locks on a fresh start, and after Sprout has been in the background for [GRACE_SECONDS] or more.
 */
object AppLockPolicy {
    const val GRACE_SECONDS = 60L

    fun shouldLock(enabled: Boolean, deviceSecure: Boolean, unlockedThisProcess: Boolean, backgroundedAt: Long?, now: Long): Boolean {
        if (!enabled || !deviceSecure) return false
        if (!unlockedThisProcess) return true
        val since = backgroundedAt ?: return false
        return now - since >= GRACE_SECONDS
    }
}

/**
 * Uses Android's own screen-lock confirmation (PIN, pattern, password or fingerprint, whatever the phone uses).
 * No extra permission is needed, which keeps Sprout's permission list minimal.
 */
object DeviceLock {
    fun isSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    @Suppress("DEPRECATION") // Still supported; BiometricPrompt would need an extra permission.
    fun confirmIntent(context: Context): Intent? =
        context.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent("Unlock Sprout", "Use your screen lock to open Sprout")
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Sprout is locked", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp)) { Text("Unlock") }
    }
}
