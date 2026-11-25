package com.emotionalleaves.sprout.ui.settings

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.verticalScroll
import com.emotionalleaves.sprout.domain.accounts.AccountType
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
    val updates get() = c.updates
    fun setThemeMode(m: Int) = viewModelScope.launch { c.settings.setThemeMode(m) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(on) }
    fun setPrivacyMode(on: Boolean) = viewModelScope.launch { c.settings.setPrivacyMode(on) }
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
    fun rename(a: AccountEntity, name: String) = viewModelScope.launch {
        c.db.accounts().rename(a.id, name.trim().take(60).ifBlank { null }?.takeIf { it != a.name })
    }
    fun setType(a: AccountEntity, t: AccountType) = viewModelScope.launch { c.db.accounts().setType(a.id, t) }
    fun disconnect(deleteData: Boolean) = viewModelScope.launch {
        c.dataManager.disconnect(deleteData)
        _messages.tryEmit(if (deleteData) "Disconnected and bank data deleted" else "Disconnected. Your data is kept.")
    }
    fun deleteAll() = viewModelScope.launch { c.dataManager.deleteAll() }
    fun setAppLock(on: Boolean) = viewModelScope.launch { c.settings.setAppLock(on) }
    fun setHideInRecents(on: Boolean) = viewModelScope.launch { c.settings.setHideInRecents(on) }
    fun setUsePurchaseDate(on: Boolean) = viewModelScope.launch {
        c.settings.setUsePurchaseDate(on)
        c.db.transactions().redatePosted(on)
    }
    fun setNotifyBudget(on: Boolean) = viewModelScope.launch { c.settings.setNotifyBudget(on) }
    fun setNotifyUnusual(on: Boolean) = viewModelScope.launch { c.settings.setNotifyUnusual(on) }
    fun setNotifySync(on: Boolean) = viewModelScope.launch { c.settings.setNotifySyncProblems(on) }
    fun setThreshold(minor: Long) = viewModelScope.launch { c.settings.setLargeTxThreshold(minor) }
}

@Composable
fun SettingsScreen(
    snackbar: SnackbarHostState,
    onReplaceToken: () -> Unit,
    onCategories: () -> Unit,
    onRules: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onRoute: (String) -> Unit = {},
) {
    val vm = sproutViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = vm.now()
    var confirmDisconnect by remember { mutableStateOf(false) }
    var deleteStep by remember { mutableStateOf(0) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("Explore") }
        item { NavRow("Net worth over time", "How your total has changed month by month", { onRoute("networth") }) }
        item { NavRow("Year view", "Every category, every month, at a glance", { onRoute("year") }) }
        item { NavRow("Tags", "Spending you've tagged, like a trip or a project", { onRoute("tags") }) }
        item { NavRow("Debt payoff plan", "When you'll be debt-free, and the fastest or cheapest way there", { onRoute("debt") }) }
        item { NavRow("Savings goals", "Track what you're saving for", { onRoute("goals") }) }
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
                if (state.connected) {
                    TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Disconnect") }
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Categories") }
        item { NavRow("Categories", "Add, rename, recolor or archive", onCategories) }
        item { NavRow("Category rules", "What Sprout learned from your changes, plus keyword rules", onRules) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Your data") }
        item { NavRow("Import CSV", "Add transactions from a bank's CSV export", onImport) }
        item { NavRow("Export CSV", "Transactions, accounts, categories, budgets, rules or bills", onExport) }
        item { NavRow("Backup and restore", "A password-protected file with all your data, for a new phone", onBackup) }
        item { NavRow("Delete all data", "Erase everything Sprout stored on this phone", { deleteStep = 1 }) }

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
                ToggleRow("Date transactions by purchase day (when the bank sends it)", state.settings.usePurchaseDate) { vm.setUsePurchaseDate(it) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(a.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { editingAccount = a }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                    }
                    ToggleRow("Include in total balance", a.includeInTotals) { vm.setIncluded(a, it) }
                    ToggleRow("Hide this account", a.hidden) { vm.setHidden(a, it) }
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Privacy") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "No analytics. No servers. Data never leaves this phone except to talk to SimpleFIN. " +
                        "Sprout is read-only: it can never move money.",
                )
                val secure = com.emotionalleaves.sprout.ui.lock.DeviceLock.isSecure(context)
                ToggleRow("Lock Sprout with your screen lock", state.settings.appLock && secure) {
                    if (secure) vm.setAppLock(it)
                }
                if (!secure) {
                    Text("Set up a screen lock (PIN, pattern or fingerprint) in your phone's settings to use this.",
                        style = MaterialTheme.typography.bodySmall)
                }
                ToggleRow("Hide in screenshots and recent apps", state.settings.hideInRecents) { vm.setHideInRecents(it) }
            }
        }
        item { SectionTitle("Appearance") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Phone", "Light", "Dark").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = state.settings.themeMode == i,
                            onClick = { vm.setThemeMode(i) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(label) }
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            item { ToggleRow("Use wallpaper colors", state.settings.dynamicColor) { vm.setDynamicColor(it) } }
        }
        item { ToggleRow("Hide amounts on Home (tap the eye to show)", state.settings.privacyMode) { vm.setPrivacyMode(it) } }
        item { SectionTitle("Updates") }
        item { Column(Modifier.padding(horizontal = 16.dp)) { com.emotionalleaves.sprout.ui.update.UpdateSettings(vm.updates) } }
        item { SectionTitle("About") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Sprout ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                TextButton(onClick = { openUrl(context, "$REPO_URL/releases") }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Releases and updates")
                }
                TextButton(onClick = { openUrl(context, REPO_URL) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Source code") }
                TextButton(onClick = { showLicenses = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open-source licenses") }
            }
        }
    }

    if (confirmDisconnect) {
        var alsoDelete by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect SimpleFIN?") },
            text = {
                Column {
                    Text("Sprout forgets your bank connection and stops syncing. Your transactions, budgets and rules stay unless you choose otherwise.")
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { alsoDelete = !alsoDelete },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoDelete, onCheckedChange = null)
                        Text("Also delete synced bank accounts and their transactions", Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.disconnect(alsoDelete); confirmDisconnect = false }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("Delete all data?") },
            text = { Text("This erases every transaction, budget, rule, bill, setting and your bank connection from this phone.") },
            confirmButton = { TextButton(onClick = { deleteStep = 2 }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Cancel") } },
        )
    }
    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { deleteStep = 0 },
            title = { Text("Are you sure?") },
            text = { Text("This can't be undone. Export anything you want to keep first.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); deleteStep = 0 }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteStep = 0 }) { Text("Keep my data") } },
        )
    }
    editingAccount?.let { a ->
        var name by remember(a.id) { mutableStateOf(a.label) }
        var type by remember(a.id) { mutableStateOf(a.type) }
        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text("Edit account") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                    AccountType.entries.forEach { t ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(type == t, role = Role.RadioButton) { type = t },
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == t, onClick = null)
                            Text(t.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(a, name); vm.setType(a, type); editingAccount = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingAccount = null }) { Text("Cancel") } },
        )
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-source licenses") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    LICENSES.forEach { (name, license) ->
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(license, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Close") } },
        )
    }
}

private val LICENSES = listOf(
    "Kotlin, kotlinx.coroutines, kotlinx.serialization (JetBrains)" to "Apache License 2.0",
    "AndroidX: Core, Activity, Lifecycle, Navigation, Compose, Material 3, Room, Paging, WorkManager, DataStore (Google)" to "Apache License 2.0",
    "OkHttp (Square)" to "Apache License 2.0",
    "Sprout" to "Your own app; source at github.com/EmotionalLeaves/Budget",
)

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
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
