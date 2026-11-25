package com.emotionalleaves.sprout.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Notifications
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        } else if (state.step == ConnectStep.TOKEN && !startAtToken) {
            // From the welcome screen: a way back that isn't only the system gesture.
            IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            androidx.activity.compose.BackHandler(onBack = vm::back)
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
                Button(onClick = { vm.accountsDone(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            }
            ConnectStep.QUICK_BUDGET -> {
                Text("Start with these budgets?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Based on your average spending over the last few months, rounded up to the next \$10. " +
                        "You can change any of them later in Budgets.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.suggestions.forEach { s ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f))
                        Text("${Money.formatWhole(s.suggestedMinor)}/month")
                    }
                }
                Button(onClick = { vm.useSuggestions(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use these budgets") }
                TextButton(onClick = { vm.skipBudgets(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not now") }
            }
            ConnectStep.NOTIFY -> {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.finish(onDone) }
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Get helpful alerts?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Sprout can tell you when a category reaches 80% or 100% of its budget, when there's a large or unusual " +
                        "charge, and when a bank connection needs attention. Amounts are hidden on your lock screen. " +
                        "Everything works without notifications too.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Turn on alerts") }
                TextButton(onClick = { vm.finish(onDone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun Welcome(onOpenBridge: () -> Unit, onHaveToken: () -> Unit, onSkip: () -> Unit) {
    val c = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
    com.emotionalleaves.sprout.ui.common.HeroCard(c.hero, Modifier.padding(top = 8.dp), inset = 0.dp) {
        Icon(Icons.Filled.Eco, contentDescription = null, tint = c.onHero, modifier = Modifier.size(52.dp))
        Text("Welcome to Sprout", style = MaterialTheme.typography.displaySmall, color = c.onHero)
        Text("See what's safe to spend, every day. Read-only: Sprout can never move money, and your data stays on this phone.",
            style = MaterialTheme.typography.bodyLarge, color = c.onHero.copy(alpha = 0.9f))
    }
    listOf(
        "Connect" to "In SimpleFIN Bridge, link your bank (a small paid service) and copy a setup token.",
        "Paste" to "Paste it here once. Sprout syncs every few hours, read-only.",
        "Budget" to "Sprout suggests budgets from your spending and shows what's safe to spend.",
    ).forEachIndexed { i, (title, body) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Button(onClick = onHaveToken, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("I have a setup token") }
    OutlinedButton(onClick = onOpenBridge, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open SimpleFIN Bridge") }
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
    com.emotionalleaves.sprout.ui.common.SoftCard(inset = 0.dp) {
        Text("What happens next", style = MaterialTheme.typography.titleSmall)
        listOf(
            "Sprout swaps the token for a read-only link to your accounts. The token can't be used again.",
            "The link is stored encrypted on this phone. Sprout only ever reads balances and transactions.",
            "Your first sync can take up to a minute.",
        ).forEach { line ->
            Row { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(line, style = MaterialTheme.typography.bodySmall) }
        }
    }
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
        Button(onClick = onConnect, enabled = !connecting && token.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
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
