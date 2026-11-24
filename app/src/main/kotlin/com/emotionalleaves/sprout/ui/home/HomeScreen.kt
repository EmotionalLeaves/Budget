package com.emotionalleaves.sprout.ui.home

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
) {
    val vm = sproutViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
    }

    PullToRefreshBox(isRefreshing = syncing, onRefresh = vm::syncNow, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
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
                            "Connect your bank with SimpleFIN to see balances and transactions. CSV import is coming soon."
                        },
                        action = if (!state.connected) {
                            { Button(onClick = onConnect, modifier = Modifier.heightIn(min = 48.dp)) { Text("Connect a bank") } }
                        } else {
                            null
                        },
                    )
                }
                return@LazyColumn
            }

            item { BalanceCard(state) }

            budget?.let { m ->
                if (m.totalBudgetMinor > 0) item { SpentThisMonth(m) }
                item { SafeToSpendCard(m, onSetBudget = onSetBudgets) }
                val top = (m.budgeted + m.unbudgetedWithSpending).filter { it.spentMinor > 0 }.sortedByDescending { it.spentMinor }.take(5)
                if (top.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Top spending this month", Modifier.weight(1f))
                            TextButton(onClick = onSeeBudgets, modifier = Modifier.heightIn(min = 48.dp)) { Text("Budgets") }
                        }
                    }
                    items(top, key = { "top-" + it.category.id }) { line -> TopCategoryRow(line) { onCategory(line.category.id) } }
                }
            }

            if (state.accounts.isNotEmpty()) {
                item { SectionTitle("Accounts") }
                items(state.accounts, key = { "acct-" + it.id }) { AccountRow(it) }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent transactions", Modifier.weight(1f))
                    TextButton(onClick = onSeeAllTransactions, modifier = Modifier.heightIn(min = 48.dp)) { Text("See all") }
                }
            }
            items(state.recent, key = { "tx-" + it.id }) { row ->
                TransactionItem(row) { openId = row.id }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun SpentThisMonth(m: BudgetMonth) {
    val level = when {
        m.totalSpentBudgetedMinor * 100 >= m.totalBudgetMinor * 100L -> ProgressLevel.RED
        m.totalSpentBudgetedMinor * 100 >= m.totalBudgetMinor * 80L -> ProgressLevel.AMBER
        else -> ProgressLevel.GREEN
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Spent this month", style = MaterialTheme.typography.labelLarge)
        BudgetProgress(m.totalSpentBudgetedMinor, m.totalBudgetMinor, level)
        Text(
            "${Money.format(m.totalSpentBudgetedMinor)} of ${Money.format(m.totalBudgetMinor)} budgeted · ${leftOrOver(m.totalBudgetMinor - m.totalSpentBudgetedMinor)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TopCategoryRow(line: CategoryLine, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                Money.format(line.spentMinor) + (line.budgetMinor?.let { " of ${Money.formatWhole(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        line.budgetMinor?.let { b -> BudgetProgress(line.spentMinor, b, line.level, Modifier.padding(top = 4.dp)) }
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

@Composable
private fun BalanceCard(state: HomeState) {
    val t = state.totals
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total balance", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
            Text(Money.format(t.totalMinor), style = MaterialTheme.typography.displaySmall)
            Text(
                "Cash ${Money.format(t.cashMinor)} · Credit ${Money.format(t.creditMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (t.excludedNonUsd > 0) {
                Text("${t.excludedNonUsd} non-USD account(s) not included", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccountRow(a: AccountEntity) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                a.type.name.lowercase().replaceFirstChar { it.uppercase() } + if (!a.includeInTotals) " · not in total" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.lastErrorMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = "Account warning", tint = LocalSproutColors.current.warning)
                    Text(a.lastErrorMessage, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            if (AccountTypes.isUsd(a.currency)) Money.format(a.balanceMinor) else "${a.balanceMinor / 100} ${AccountTypes.currencyLabel(a.currency)}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
