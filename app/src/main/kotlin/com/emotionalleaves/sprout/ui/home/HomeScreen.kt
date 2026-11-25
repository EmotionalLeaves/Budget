package com.emotionalleaves.sprout.ui.home

import com.emotionalleaves.sprout.ui.common.showUndoable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.emotionalleaves.sprout.ui.transactions.TransactionDetailSheet
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.money.Money
import androidx.compose.foundation.clickable
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.budget.CategoryLine
import com.emotionalleaves.sprout.domain.budget.ProgressLevel
import com.emotionalleaves.sprout.ui.bills.dueText
import com.emotionalleaves.sprout.ui.budgets.BudgetProgress
import com.emotionalleaves.sprout.ui.budgets.SafeToSpendCard
import com.emotionalleaves.sprout.ui.budgets.leftOrOver
import com.emotionalleaves.sprout.ui.common.BRIDGE_URL
import com.emotionalleaves.sprout.ui.common.Banner
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.SectionTitle
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.openUrl
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    onConnect: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onSetBudgets: () -> Unit,
    onCategory: (Long) -> Unit,
    onSeeBudgets: () -> Unit,
    onSeeBills: () -> Unit,
    onImport: () -> Unit,
    onGoals: () -> Unit,
    onNetWorth: () -> Unit = {},
) {
    val vm = sproutViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val crash by vm.crash.collectAsStateWithLifecycle()
    val outlook by vm.outlook.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    com.emotionalleaves.sprout.ui.update.WhatsNewAfterUpdate(vm.updates)
    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg, undo -> scope.showUndoable(snackbar, msg, undo) })
    }

    val months by vm.months.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val hidden by vm.privacy.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(com.emotionalleaves.sprout.ui.common.LocalHideAmounts provides hidden) {
    PullToRefreshBox(isRefreshing = syncing, onRefresh = vm::syncNow, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { HomeHeader(hidden, vm::togglePrivacy) }
            crash?.let { report ->
                item {
                    CrashCard(
                        onShare = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Sprout crash report")
                                .putExtra(android.content.Intent.EXTRA_TEXT, report)
                            runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share crash details")) }
                            vm.dismissCrash()
                        },
                        onDismiss = vm::dismissCrash,
                    )
                }
            }
            item { com.emotionalleaves.sprout.ui.update.UpdateCard(vm.updates) }
            item { SyncStatusLine(state, vm.now()) }
            val partial = state.lastLog?.takeIf { it.result == SyncResult.PARTIAL }
            if (partial != null) {
                item { Banner(partial.message, warning = false) }
            }

            if (state.settings.accessRevoked) {
                item {
                    Banner("Your SimpleFIN access was turned off. Paste a new setup token to reconnect.", "Reconnect", onConnect)
                }
            }
            if (state.settings.paymentRequired) {
                item {
                    Banner("Your SimpleFIN Bridge subscription needs attention.", "Open Bridge", { openUrl(context, BRIDGE_URL) })
                }
            }
            items(state.attention, key = { "conn-" + it.connId }) { conn ->
                Banner("${conn.name}: ${conn.lastErrorMessage ?: "needs attention"}", "Fix in Bridge", { openUrl(context, BRIDGE_URL) })
            }

            if (state.loaded && state.accounts.isEmpty() && state.recent.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.connected) "Waiting for your first sync" else "No data yet",
                        body = if (state.connected) {
                            "Pull down to sync now. Your accounts and recent transactions will show up here."
                        } else {
                            "Connect your bank with SimpleFIN, or import a CSV from your bank's website."
                        },
                        action = if (!state.connected) {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onConnect, modifier = Modifier.heightIn(min = 48.dp)) { Text("Connect a bank") }
                                    TextButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import a CSV") }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                return@LazyColumn
            }

            val m = budget
            item { SafeToSpendHero(m, state.totals.totalMinor, onSetBudgets) }
            item { StatsRow(state.totals.totalMinor, outlook?.trend, outlook?.forecast?.endMinor, onNetWorth) }
            if (insights.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.common.SectionHeader("Insights") }
                item { com.emotionalleaves.sprout.ui.insights.InsightsStrip(insights, onCategory) }
            }
            outlook?.forecast?.let { f -> item { ForecastCard(f) } }
            if (m != null) item { WhereItWentCard(m, onCategory, onSeeBudgets) }
            item { MonthsCard(months) }

            if (bills.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.common.SectionHeader("Coming up", "All bills", onSeeBills) }
                item { BillsStrip(bills, onSeeBills) }
            }

            if (goals.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.common.SectionHeader("Savings goals", "All goals", onGoals) }
                items(goals, key = { "goal-" + it.goal.id }) { g -> com.emotionalleaves.sprout.ui.goals.GoalProgressRow(g, onGoals) }
            }

            if (state.accounts.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.common.SectionHeader("Accounts") }
                item { AccountsCard(state.accounts, state.settings.lastSuccessfulSyncAt) }
            }

            item { com.emotionalleaves.sprout.ui.common.SectionHeader("Recent", "See all", onSeeAllTransactions) }
            items(state.recent, key = { "tx-" + it.id }) { row ->
                TransactionItem(row) { openId = row.id }
            }
            item { BottomSpace() }
        }
    }
    }
}

@Composable
private fun ForecastCard(f: com.emotionalleaves.sprout.domain.forecast.Forecast) {
    var details by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val monthEnd = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
    val end = java.time.LocalDate.now().let { it.withDayOfMonth(it.lengthOfMonth()) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Show forecast details") { details = true }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Cash by ${end.format(monthEnd)}", style = MaterialTheme.typography.labelLarge)
            Text(
                "≈ ${Money.format(f.endMinor)}",
                style = MaterialTheme.typography.headlineSmall,
                color = if (f.endMinor < 0) LocalSproutColors.current.danger else MaterialTheme.colorScheme.onSurface,
            )
            if (f.lowMinor < 0 && f.lowMinor < f.endMinor) {
                Text("Could dip to ${Money.format(f.lowMinor)} around ${f.lowDate.format(monthEnd)} before payday",
                    style = MaterialTheme.typography.bodySmall, color = LocalSproutColors.current.warning)
            }
            f.nextIncome?.let { Text("Next paycheck: ${it.date.format(monthEnd)} · ${Money.format(it.amountMinor)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (details) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("How it's worked out") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cash now: ${Money.format(f.startMinor)}")
                    Text("+ expected paychecks: ${Money.format(f.incomeMinor)}")
                    Text("− bills still due: ${Money.format(f.billsMinor)}")
                    Text("− everyday spending: ${Money.format(f.spendingMinor)}" +
                        if (f.spendingFromBudgets) " (budget left)" else " (your last 30 days' pace)")
                    Text("= ≈ ${Money.format(f.endMinor)} at month end", style = MaterialTheme.typography.titleSmall)
                    Text("Cash means checking, savings and cash accounts counted in your total. It's an estimate.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun CrashCard(onShare: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sprout closed unexpectedly last time", style = MaterialTheme.typography.titleSmall)
            Text(
                "A short technical report was saved on this phone. It has no amounts, descriptions or bank details. " +
                    "You can share it (for example in a GitHub issue) to help fix the problem.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("Share details") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun SyncStatusLine(state: HomeState, now: Long) {
    val log = state.lastLog
    val last = state.settings.lastSuccessfulSyncAt
    val text = when {
        !state.connected -> "No bank connected"
        !state.online && last != null -> "Offline – showing data from ${Format.ago(last, now)}"
        !state.online -> "Offline – will sync when you're back online"
        last == null && log == null -> "Not synced yet"
        log != null && log.result == SyncResult.FAILED && last != null -> "Sync problem – showing data from ${Format.ago(last, now)}"
        log != null && log.result == SyncResult.FAILED -> "Last sync failed: ${log.message}"
        last != null -> "Updated ${Format.ago(last, now)}" + if (log?.result == SyncResult.PARTIAL) " (some data incomplete)" else ""
        else -> ""
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (log?.result == SyncResult.FAILED || log?.result == SyncResult.PARTIAL) {
            Icon(Icons.Filled.Warning, contentDescription = "Sync warning", tint = LocalSproutColors.current.warning)
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp))
    }
}

