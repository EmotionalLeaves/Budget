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
) {
    val vm = sproutViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    PullToRefreshBox(isRefreshing = syncing, onRefresh = vm::syncNow, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { SyncStatusLine(state, vm.now()) }

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
                TransactionItem(row)
                HorizontalDivider(Modifier.padding(start = 16.dp))
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
        last == null && log == null -> "Not synced yet"
        log != null && log.result == SyncResult.FAILED && last != null -> "Offline or sync problem – showing data from ${Format.ago(last, now)}"
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
