package com.emotionalleaves.sprout.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.emotionalleaves.sprout.domain.money.Money
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.BuildConfig
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.ConnectionEntity
import com.emotionalleaves.sprout.data.db.ConnectionStatus
import com.emotionalleaves.sprout.data.db.SyncLogEntity
import com.emotionalleaves.sprout.data.settings.Settings
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import com.emotionalleaves.sprout.ui.common.BRIDGE_URL
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.REPO_URL
import com.emotionalleaves.sprout.ui.common.SectionTitle
import com.emotionalleaves.sprout.ui.common.SyncActions
import com.emotionalleaves.sprout.ui.common.openUrl
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val settings: Settings = Settings(),
    val connections: List<ConnectionEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val logs: List<SyncLogEntity> = emptyList(),
    val connected: Boolean = false,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsState> = combine(
        c.settings.settings,
        c.db.connections().observeAll(),
        c.db.accounts().observeAll(),
        c.db.syncLog().observeRecent(20),
        c.secureStore.hasAccessUrl,
    ) { s, conns, accts, logs, connected -> SettingsState(s, conns, accts, logs, connected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun now() = c.clock.instant().epochSecond
    fun syncNow() = viewModelScope.launch {
        _messages.tryEmit(SyncActions.requestSync(c) ?: "Syncing…")
    }
    fun setFirstSyncDays(days: Int) = viewModelScope.launch { c.settings.setFirstSyncDays(days) }
    fun setIncluded(a: AccountEntity, include: Boolean) = viewModelScope.launch { c.db.accounts().setIncludeInTotals(a.id, include) }
    fun setHidden(a: AccountEntity, hidden: Boolean) = viewModelScope.launch { c.db.accounts().setHidden(a.id, hidden) }
    fun canPost() = c.notifier.canPost()
    fun setNotifyBudget(on: Boolean) = viewModelScope.launch { c.settings.setNotifyBudget(on) }
    fun setNotifyUnusual(on: Boolean) = viewModelScope.launch { c.settings.setNotifyUnusual(on) }
    fun setNotifySync(on: Boolean) = viewModelScope.launch { c.settings.setNotifySyncProblems(on) }
    fun setThreshold(minor: Long) = viewModelScope.launch { c.settings.setLargeTxThreshold(minor) }
}

@Composable
fun SettingsScreen(snackbar: SnackbarHostState, onReplaceToken: () -> Unit, onCategories: () -> Unit, onRules: () -> Unit) {
    val vm = sproutViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = vm.now()
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("Bank connections") }
        if (!state.connected) {
            item { Text("No bank connected.", Modifier.padding(horizontal = 16.dp)) }
        }
        items(state.connections, key = { "c-" + it.connId }) { conn ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(conn.name, style = MaterialTheme.typography.bodyLarge)
                Text(statusText(conn), style = MaterialTheme.typography.bodySmall)
                if (conn.status != ConnectionStatus.OK) {
                    TextButton(onClick = { openUrl(context, BRIDGE_URL) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Fix in SimpleFIN Bridge")
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReplaceToken, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.connected) "Replace setup token" else "Connect a bank")
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Categories") }
        item { NavRow("Categories", "Add, rename, recolor or archive", onCategories) }
        item { NavRow("Category rules", "What Sprout learned from your changes, plus keyword rules", onRules) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Notifications") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (!vm.canPost()) {
                    Text("Notifications are off for Sprout.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Turn on in system settings") }
                }
                ToggleRow("Budget alerts (80% and 100%)", state.settings.notifyBudget) { vm.setNotifyBudget(it) }
                ToggleRow("Large or unusual charges", state.settings.notifyUnusual) { vm.setNotifyUnusual(it) }
                ToggleRow("Sync problems", state.settings.notifySyncProblems) { vm.setNotifySync(it) }
                ThresholdRow(state.settings.largeTxThresholdMinor) { vm.setThreshold(it) }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Sync") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val last = state.settings.lastSuccessfulSyncAt
                Text(if (last != null) "Last successful sync: ${Format.ago(last, now)}" else "Not synced yet")
                Text(
                    "Sprout syncs about every 6 hours and asks SimpleFIN at most ${SyncPolicy.DAILY_REQUEST_BUDGET} times a day.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { vm.syncNow() }, modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 48.dp)) { Text("Sync now") }
                Text("History to fetch on first sync", style = MaterialTheme.typography.labelLarge)
                SyncPolicy.ALLOWED_FIRST_SYNC_DAYS.forEach { days ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(state.settings.firstSyncDays == days, role = Role.RadioButton) { vm.setFirstSyncDays(days) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.settings.firstSyncDays == days, onClick = null)
                        Text("$days days", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (state.logs.isNotEmpty()) {
            item { SectionTitle("Sync log") }
            items(state.logs, key = { "l-" + it.id }) { log ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("${Format.dateTime(log.startedAt)} · ${log.trigger.name.lowercase()} · ${log.result.name.lowercase()}",
                        style = MaterialTheme.typography.labelMedium)
                    Text(log.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Accounts") }
            items(state.accounts, key = { "a-" + it.id }) { a ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge)
                    ToggleRow("Include in total balance", a.includeInTotals) { vm.setIncluded(a, it) }
                    ToggleRow("Hide this account", a.hidden) { vm.setHidden(a, it) }
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Privacy") }
        item {
            Text(
                "No analytics. No servers. Data never leaves this phone except to talk to SimpleFIN. " +
                    "Sprout is read-only: it can never move money.",
                Modifier.padding(horizontal = 16.dp),
            )
        }
        item { SectionTitle("About") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Sprout ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                TextButton(onClick = { openUrl(context, "$REPO_URL/releases") }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Releases and updates")
                }
            }
        }
    }
}

@Composable
private fun ThresholdRow(currentMinor: Long, onSave: (Long) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editing = true }.padding(vertical = 8.dp),
    ) {
        Text("Large charge amount", style = MaterialTheme.typography.bodyLarge)
        Text("Alert for any charge of ${Money.formatWhole(currentMinor)} or more", style = MaterialTheme.typography.bodySmall)
    }
    if (editing) {
        var text by remember { mutableStateOf((currentMinor / 100).toString()) }
        val parsed = Money.parseUserInput(text)?.takeIf { it >= 100 }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Large charge amount") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(8) }, prefix = { Text("$") }, singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = { TextButton(onClick = { parsed?.let(onSave); editing = false }, enabled = parsed != null) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun statusText(conn: ConnectionEntity): String = when (conn.status) {
    ConnectionStatus.OK -> "Working normally"
    ConnectionStatus.NEEDS_ATTENTION -> "Needs attention: " + (conn.lastErrorMessage ?: "open SimpleFIN Bridge to fix it.")
    ConnectionStatus.REVOKED -> "Access turned off. Paste a new setup token to reconnect."
}
