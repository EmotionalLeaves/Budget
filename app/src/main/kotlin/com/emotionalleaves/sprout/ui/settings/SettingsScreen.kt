package com.emotionalleaves.sprout.ui.settings

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Notifications
import com.emotionalleaves.sprout.domain.accounts.label
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import com.emotionalleaves.sprout.ui.common.readable
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
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
    fun setNotifyBills(on: Boolean) = viewModelScope.launch { c.settings.setNotifyBills(on) }
    fun setNotifyWeekly(on: Boolean) = viewModelScope.launch { c.settings.setNotifyWeekly(on) }
    fun setBeta(on: Boolean) = viewModelScope.launch { c.settings.setBetaUpdates(on); c.updates.check() }
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
    fun forceSync() = viewModelScope.launch { _messages.tryEmit(SyncActions.requestSync(c, force = true) ?: "Syncing now…") }
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
    /** null: the More tab. Otherwise one settings page: "notifications", "sync", "privacy" or "about". */
    section: String? = null,
    onBack: () -> Unit = {},
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
        if (section != null) item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                Text(settingsPages.firstOrNull { it.first == section }?.second ?: "Settings", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (section == null) {
        item { SectionTitle("Explore") }
        item { ExploreGrid(onRoute) }
        item { SectionTitle("Bank connections") }
        if (!state.connected) {
            item {
                com.emotionalleaves.sprout.ui.common.SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.emotionalleaves.sprout.ui.common.IconBadge(Icons.Rounded.AccountBalance, MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("No bank connected", style = MaterialTheme.typography.titleSmall)
                            Text("Connect with SimpleFIN to sync automatically. Sprout only reads; it can never move money.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
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

        item { SectionTitle("Categories") }
        item {
            NavGroup {
                NavRow(icon = Icons.Rounded.Category, title = "Categories", subtitle = "Add, rename, recolor or archive", onClick = onCategories)
                NavRow(icon = Icons.Rounded.Rule, title = "Category rules", subtitle = "Auto-sort, plus what you taught it", onClick = onRules, divider = false)
            }
        }

        item { SectionTitle("Your data") }
        item {
            NavGroup {
                NavRow(icon = Icons.Rounded.FileUpload, title = "Import CSV", subtitle = "From your bank's website", onClick = onImport)
                NavRow(icon = Icons.Rounded.FileDownload, title = "Export CSV", subtitle = "Transactions, budgets, rules or bills", onClick = onExport)
                NavRow(icon = Icons.Rounded.Backup, title = "Backup and restore", subtitle = "A password-protected file, for a new phone", onClick = onBackup, divider = false)
            }
        }
        item { NavGroup { NavRow(icon = Icons.Rounded.DeleteForever, title = "Delete all data", subtitle = "Erase everything Sprout stored on this phone", onClick = { deleteStep = 1 }, divider = false) } }

        // The rest of the settings live on their own pages, so More stays short.
        item { SectionTitle("Settings") }
        item {
            NavGroup {
                val icons = listOf(Icons.Rounded.Notifications, Icons.Rounded.Sync, Icons.Rounded.Lock, Icons.Rounded.Info)
                val notes = listOf(
                    if (!vm.canPost()) "Off for Sprout right now" else "Alerts, bill reminders, weekly summary",
                    state.settings.lastSuccessfulSyncAt?.let { "Last synced " + Format.ago(it, now) } ?: "History, force sync, accounts",
                    "App lock, theme, hide amounts",
                    "Sprout ${BuildConfig.VERSION_NAME}, updates, licenses",
                )
                settingsPages.forEachIndexed { i, (key, title) ->
                    NavRow(icon = icons[i], title = title, subtitle = notes[i], onClick = { onRoute("settings/$key") }, divider = i < settingsPages.lastIndex)
                }
            }
        }
        }

        if (section == "notifications") {
        item {
            SettingsGroup {
                if (!vm.canPost()) {
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Notifications are off for Sprout, so these can't reach you yet.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(start = 12.dp))
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                    ) { Text("Turn on notifications") }
                }
                ToggleRow("Budget alerts", state.settings.notifyBudget, "When a budget reaches 80% and 100%") { vm.setNotifyBudget(it) }
                ToggleRow("Large or unusual charges", state.settings.notifyUnusual, "Big amounts, and charges unlike your usual ones") { vm.setNotifyUnusual(it) }
                ToggleRow("Bill reminders", state.settings.notifyBills, "The day before a bill is due") { vm.setNotifyBills(it) }
                ToggleRow("Weekly summary", state.settings.notifyWeekly, "Sunday: what you spent this week vs last") { vm.setNotifyWeekly(it) }
                ToggleRow("Sync problems", state.settings.notifySyncProblems, "If your bank connection needs attention") { vm.setNotifySync(it) }
                ThresholdRow(state.settings.largeTxThresholdMinor) { vm.setThreshold(it) }
            }
        }

        }
        if (section == "sync") {
        item { SectionTitle("Sync") }
        item {
            SettingsGroup {
                val last = state.settings.lastSuccessfulSyncAt
                Text(if (last != null) "Last successful sync: ${Format.ago(last, now)}" else "Not synced yet",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Sprout syncs about every 6 hours and asks SimpleFIN at most ${SyncPolicy.DAILY_REQUEST_BUDGET} times a day.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.syncNow() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sync now") }
                    OutlinedButton(onClick = { vm.forceSync() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Force sync") }
                }
                Text(
                    "Force sync skips the 15-minute wait. It still stops before SimpleFIN's own limit of 24 a day, because going over can turn your access off.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow("Date by purchase day", state.settings.usePurchaseDate, "When the bank sends it, instead of the day it posted") { vm.setUsePurchaseDate(it) }
                Text("History to fetch", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Text(
                    historyStatus(state.settings, now),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SyncPolicy.ALLOWED_FIRST_SYNC_DAYS.forEach { days ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(state.settings.firstSyncDays == days, role = Role.RadioButton) { vm.setFirstSyncDays(days) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.settings.firstSyncDays == days, onClick = null)
                        Text(historyLabel(days), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (state.logs.isNotEmpty()) {
            item { SectionTitle("Last sync") }
            item {
                SettingsGroup {
                    val sc = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
                    // The latest sync is what matters day to day; the history is one tap away.
                    var allLogs by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                    state.logs.take(if (allLogs) 8 else 1).forEach { log ->
                        val (icon, tint) = when (log.result) {
                            com.emotionalleaves.sprout.data.db.SyncResult.SUCCESS -> Icons.Rounded.CheckCircle to sc.ok
                            com.emotionalleaves.sprout.data.db.SyncResult.PARTIAL -> Icons.Rounded.Warning to sc.warning
                            com.emotionalleaves.sprout.data.db.SyncResult.FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                        }
                        val how = when (log.trigger) {
                            com.emotionalleaves.sprout.data.db.SyncTrigger.PERIODIC -> "Automatic"
                            com.emotionalleaves.sprout.data.db.SyncTrigger.MANUAL -> "You asked"
                            com.emotionalleaves.sprout.data.db.SyncTrigger.ONBOARDING -> "First sync"
                            com.emotionalleaves.sprout.data.db.SyncTrigger.FORCED -> "Full sync"
                        }
                        Row(Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${Format.dateTime(log.startedAt)} · $how", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(log.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.logs.size > 1) {
                        TextButton(onClick = { allLogs = !allLogs }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (allLogs) "Show only the latest" else "Show the last ${minOf(8, state.logs.size)} syncs")
                        }
                    }
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
                item { SectionTitle("Accounts") }
            // One compact row per account; tap for name, type and whether it counts or shows.
            item {
                SettingsGroup {
                    state.accounts.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editingAccount = a }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = MaterialTheme.typography.titleSmall)
                                Text(a.type.label + " · " + Money.format(a.balanceMinor),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val status = when {
                                    a.hidden -> "Hidden everywhere"
                                    !a.includeInTotals -> "Not counted in your total"
                                    else -> null
                                }
                                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current.warning) }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Edit ${a.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        }
        if (section == "privacy") {
        item { SectionTitle("Privacy") }
        item {
            SettingsGroup {
                Text(
                    "No analytics. No servers. Data never leaves this phone except to talk to SimpleFIN. " +
                        "Sprout is read-only: it can never move money.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp),
                )
                val secure = com.emotionalleaves.sprout.ui.lock.DeviceLock.isSecure(context)
                ToggleRow("App lock", state.settings.appLock && secure, "Unlock Sprout with your screen lock or fingerprint") {
                    if (secure) vm.setAppLock(it)
                }
                if (!secure) {
                    Text("Set up a screen lock (PIN, pattern or fingerprint) in your phone's settings to use this.",
                        style = MaterialTheme.typography.bodySmall)
                }
                ToggleRow("Hide from screenshots", state.settings.hideInRecents, "Also blanks Sprout in the recent-apps view") { vm.setHideInRecents(it) }
            }
        }
        item { SectionTitle("Appearance") }
        item {
            SettingsGroup {
                Text("Theme", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Phone", "Light", "Dark", "Black").forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = state.settings.themeMode == i,
                            onClick = { vm.setThemeMode(i) },
                            shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 4),
                        ) { Text(label) }
                    }
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    ToggleRow("Wallpaper colors", state.settings.dynamicColor, "Match your phone's color theme") { vm.setDynamicColor(it) }
                }
                ToggleRow("Hide amounts on Home", state.settings.privacyMode, "Tap the eye on Home to show them") { vm.setPrivacyMode(it) }
            }
        }
        }
        if (section == "about") {
        item { SectionTitle("Updates") }
        item { SettingsGroup { com.emotionalleaves.sprout.ui.update.UpdateSettings(vm.updates, state.settings.betaUpdates) { vm.setBeta(it) } } }
        item { SectionTitle("About") }
        item {
            SettingsGroup {
                Text("Sprout ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { openUrl(context, "$REPO_URL/releases") }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Releases and updates")
                }
                TextButton(onClick = { openUrl(context, REPO_URL) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Source code") }
                TextButton(onClick = { showLicenses = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open-source licenses") }
            }
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
        var included by remember(a.id) { mutableStateOf(a.includeInTotals) }
        var hidden by remember(a.id) { mutableStateOf(a.hidden) }
        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text("Edit account") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AccountType.entries.forEach { t ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(type == t, role = Role.RadioButton) { type = t },
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == t, onClick = null)
                            Text(t.label, Modifier.padding(start = 8.dp))
                        }
                    }
                    ToggleRow("Include in total balance", included, "Counts toward net worth and your balance") { included = it }
                    ToggleRow("Hide this account", hidden, "Its transactions leave every list and total") { hidden = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(a, name); vm.setType(a, type)
                    if (included != a.includeInTotals) vm.setIncluded(a, included)
                    if (hidden != a.hidden) vm.setHidden(a, hidden)
                    editingAccount = null
                }) { Text("Save") }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { parsed?.let(onSave); editing = false }, enabled = parsed != null) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

private data class ExploreTile(val title: String, val subtitle: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Long)

private val EXPLORE = listOf(
    ExploreTile("Stats", "Patterns and top places", "stats", Icons.Rounded.ShowChart, 0xFF00897B),
    ExploreTile("Reports", "Cash flow, any range", "reports", Icons.Rounded.BarChart, 0xFF2E7D32),
    ExploreTile("Goals", "What you're saving for", "goals", Icons.Rounded.Savings, 0xFFF9A825),
    ExploreTile("Challenges", "Small spending dares", "challenges", Icons.Rounded.EmojiEvents, 0xFFEF6C00),
    ExploreTile("Year in review", "Your year in numbers", "year-review", Icons.Rounded.AutoAwesome, 0xFF8E24AA),
    ExploreTile("Net worth", "Month by month", "networth", Icons.Rounded.AccountBalance, 0xFF1565C0),
    ExploreTile("Year view", "Every category, every month", "year", Icons.Rounded.GridView, 0xFF3949AB),
    ExploreTile("Tags", "Trips and projects", "tags", Icons.Rounded.Tag, 0xFFD81B60),
    ExploreTile("Debt plan", "Your way to debt-free", "debt", Icons.Rounded.CreditCard, 0xFFC62828),
    ExploreTile("Sort purchases", "Uncategorized, one tap each", "review", Icons.Rounded.Category, 0xFF558B2F),
)

/** Explore: every tool as a coloured tile, two per row (one per row with large text). */
@Composable
private fun ExploreGrid(onRoute: (String) -> Unit) {
    // With large text, tall tiles one per row make a very long page: use compact rows instead.
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
        NavGroup {
            EXPLORE.forEachIndexed { i, t ->
                NavRow(icon = t.icon, title = t.title, subtitle = t.subtitle, onClick = { onRoute(t.route) }, divider = i < EXPLORE.lastIndex)
            }
        }
        return
    }
    val perRow = 2
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EXPLORE.chunked(perRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val c = androidx.compose.ui.graphics.Color(t.color).readable()
                    androidx.compose.material3.Surface(
                        onClick = { onRoute(t.route) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(
                            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.copy(alpha = 0.16f), c.copy(alpha = 0.03f))))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            com.emotionalleaves.sprout.ui.common.IconBadge(t.icon, c, 40.dp)
                            Column {
                                Text(t.title, style = MaterialTheme.typography.titleSmall)
                                Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (row.size < perRow) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Several navigation rows on one rounded card, like a settings group. */
@Composable
private fun NavGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Column(content = content) }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    divider: Boolean = true,
) {
    val danger = title.startsWith("Delete")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            com.emotionalleaves.sprout.ui.common.IconBadge(icon, if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, 36.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = if (icon != null) 66.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One settings section as a card, matching the navigation rows above it. */
@Composable
private fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) { Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 8.dp), content = content) }
}

private fun statusText(conn: ConnectionEntity): String = when (conn.status) {
    ConnectionStatus.OK -> "Working normally"
    ConnectionStatus.NEEDS_ATTENTION -> "Needs attention: " + (conn.lastErrorMessage ?: "open SimpleFIN Bridge to fix it.")
    ConnectionStatus.REVOKED -> "Access turned off. Paste a new setup token to reconnect."
}

/** "1 year", "Everything the bank has". */
private fun historyLabel(days: Int): String = when (days) {
    SyncPolicy.ALL_HISTORY_DAYS -> "Everything the bank has"
    in 0..179 -> "$days days"
    180 -> "6 months"
    365 -> "1 year"
    else -> "${days / 365} years"
}

/** Where history loading stands, in words. */
private fun historyStatus(s: com.emotionalleaves.sprout.data.settings.Settings, now: Long): String {
    val oldest = s.historyOldest
    val since = oldest?.let {
        java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
    }
    return when {
        s.firstSyncDays <= SyncPolicy.MAX_FIRST_SYNC_DAYS -> "SimpleFIN sends 90 days at a time; pick more and Sprout fetches older history in steps."
        s.historyDone && since != null -> "All the history your bank has is loaded (back to $since)."
        since != null -> "Loaded back to $since so far. Older history keeps loading, a few months each sync (Force sync speeds it up)."
        else -> "Older history loads in 90-day steps over the next few syncs."
    }
}

/** The settings pages behind More → Settings: route key and title. */
val settingsPages = listOf(
    "notifications" to "Notifications",
    "sync" to "Sync and accounts",
    "privacy" to "Privacy and appearance",
    "about" to "Updates and about",
)
