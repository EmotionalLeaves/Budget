package com.emotionalleaves.sprout.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.BRIDGE_URL
import com.emotionalleaves.sprout.ui.common.Banner
import com.emotionalleaves.sprout.ui.common.openUrl
import com.emotionalleaves.sprout.ui.common.sproutViewModel

/**
 * Onboarding and "Replace setup token". [onDone] runs after the user finishes (or skips).
 */
@Composable
fun ConnectScreen(startAtToken: Boolean, onDone: () -> Unit, onSkip: () -> Unit, onBack: (() -> Unit)? = null) {
    val vm = sproutViewModel { ConnectViewModel(it, startAtToken) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        when (state.step) {
            ConnectStep.WELCOME -> Welcome(
                onOpenBridge = { openUrl(context, BRIDGE_URL) },
                onHaveToken = vm::showToken,
                onSkip = onSkip,
            )
            ConnectStep.TOKEN, ConnectStep.CONNECTING -> TokenStep(
                token = state.token,
                error = state.error,
                offerNewToken = state.offerNewToken,
                connecting = state.step == ConnectStep.CONNECTING,
                onTokenChange = vm::onTokenChange,
                onConnect = vm::connect,
                onGetNewToken = { openUrl(context, BRIDGE_URL) },
                onSkip = onSkip,
            )
            ConnectStep.SYNCING -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.size(48.dp))
                CircularProgressIndicator()
                Spacer(Modifier.size(16.dp))
                Text("Connected! Fetching your accounts and recent transactions…", style = MaterialTheme.typography.titleMedium)
                Text("This can take up to a minute the first time.", style = MaterialTheme.typography.bodyMedium)
            }
            ConnectStep.ACCOUNTS -> {
                val accounts by vm.accounts.collectAsStateWithLifecycle()
                val log by vm.lastLog.collectAsStateWithLifecycle()
                Text("Accounts found", style = MaterialTheme.typography.headlineSmall)
                if (log?.result == SyncResult.FAILED) {
                    Banner("The first sync didn't finish: ${log?.message}. Sprout will keep trying in the background.")
                }
                if (accounts.isEmpty()) {
                    Text("No accounts yet. If you just connected banks in SimpleFIN Bridge, they'll appear after the next sync.")
                }
                Text("Tick the accounts to count in your total balance.", style = MaterialTheme.typography.bodyMedium)
                accounts.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Checkbox(checked = a.includeInTotals, onCheckedChange = { vm.setIncluded(a, it) })
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                a.type.name.lowercase().replaceFirstChar { it.uppercase() } + " · " +
                                    if (AccountTypes.isUsd(a.currency)) Money.format(a.balanceMinor) else AccountTypes.currencyLabel(a.currency),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Button(onClick = { vm.finish(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Welcome(onOpenBridge: () -> Unit, onHaveToken: () -> Unit, onSkip: () -> Unit) {
    Icon(Icons.Filled.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Text("Welcome to Sprout", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Sprout reads your bank data. It can never move money. Your data stays on this phone.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How it works", style = MaterialTheme.typography.titleMedium)
            Text("1. In SimpleFIN Bridge, connect your bank (a small paid service).")
            Text("2. Create a setup token there and copy it.")
            Text("3. Paste it here. Sprout syncs your accounts every few hours, read-only.")
        }
    }
    OutlinedButton(onClick = onOpenBridge, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open SimpleFIN Bridge") }
    Button(onClick = onHaveToken, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("I have a setup token") }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Skip — I'll import a CSV") }
}

@Composable
private fun TokenStep(
    token: String,
    error: String?,
    offerNewToken: Boolean,
    connecting: Boolean,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
    onGetNewToken: () -> Unit,
    onSkip: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Text("Paste your setup token", style = MaterialTheme.typography.headlineSmall)
    Text("Copy it from SimpleFIN Bridge. Each token works only once.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text("Setup token") },
        minLines = 4,
        isError = error != null,
        enabled = !connecting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.let(onTokenChange) },
            enabled = !connecting,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Paste")
        }
        Button(onClick = onConnect, enabled = !connecting, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            if (connecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Connecting…")
            } else {
                Text("Connect")
            }
        }
    }
    if (offerNewToken) {
        OutlinedButton(onClick = onGetNewToken, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Get a new token") }
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Skip — I'll import a CSV") }
}
