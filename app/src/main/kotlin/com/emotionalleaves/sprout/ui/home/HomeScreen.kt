package com.emotionalleaves.sprout.ui.home

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Savings
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
import androidx.compose.material.icons.rounded.Insights
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
    val calendar by vm.calendar.collectAsStateWithLifecycle()
    val recap by vm.recap.collectAsStateWithLifecycle()
    val inbox by vm.inboxCount.collectAsStateWithLifecycle()
    val flex by vm.flex.collectAsStateWithLifecycle()
    val age by vm.ageOfMoney.collectAsStateWithLifecycle()
    val watch by vm.watch.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val weekPace by vm.weekPace.collectAsStateWithLifecycle()
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
    val hidden by vm.privacy.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(com.emotionalleaves.sprout.ui.common.LocalHideAmounts provides hidden) {
    var homeOrderRaw by com.emotionalleaves.sprout.ui.common.rememberViewPref("home-order", "")
    val homeOrder = androidx.compose.runtime.remember(homeOrderRaw) { orderedHomeCards(homeOrderRaw) }
    PullToRefreshBox(isRefreshing = syncing, onRefresh = vm::syncNow, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { HomeHeader(hidden, vm::togglePrivacy) }
            if (!state.loaded) {
                item { com.emotionalleaves.sprout.ui.common.SkeletonBlock(190.dp) }
                item { com.emotionalleaves.sprout.ui.common.SkeletonBlock(84.dp) }
                item { com.emotionalleaves.sprout.ui.common.SkeletonBlock(170.dp) }
                return@LazyColumn
            }
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
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(onClick = onConnect, modifier = Modifier.heightIn(min = 48.dp)) { Text("Connect a bank") }
                                    TextButton(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import a CSV") }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (!state.connected) item { WhatYouGet() }
                return@LazyColumn
            }

            val m = budget
            val fx = flex
            if (fx != null && fx.month.flexBudgetMinor > 0) item { FlexHero(fx) } else item { SafeToSpendHero(m, state.totals.totalMinor, onSetBudgets) }
            if (inbox > 0) item { com.emotionalleaves.sprout.ui.transactions.InboxCard(inbox) { navigate("inbox") } }
            recap?.let { r -> item { com.emotionalleaves.sprout.ui.insights.RecapCard(r, vm::dismissRecap) } }
            val off = state.settings.homeHidden
            fun on(k: String) = k !in off
            // Cards in your order (Customize Home), skipping hidden ones.
            homeOrder.forEach { key ->
                // Housekeeping sits below the first money cards: it matters, but it's never the first thing to see.
                if (key == "where" && state.backupDue) item(key = "card-backup") {
                    BackupNudge(state.settings.lastBackupAt, onBackup = { navigate("backup") }, onLater = vm::snoozeBackup)
                }
                if (!on(key)) return@forEach
                when (key) {
                    "stats" -> item(key = "card-stats") {
                        var forecastOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
                        StatsRow(state.totals.totalMinor, outlook?.trend, outlook?.forecast?.endMinor, onNetWorth, onMonthEnd = { forecastOpen = true })
                        outlook?.forecast?.let { f -> if (forecastOpen) ForecastSheet(f) { forecastOpen = false } }
                    }
                    "week" -> weekPace?.takeIf { it.soFarMinor > 0 || it.usualByNowMinor != null }?.let { w -> item(key = "card-week") { WeekPaceCard(w) } }
                    "health" -> health?.let { h -> item(key = "card-health") { com.emotionalleaves.sprout.ui.challenge.HealthCard(h) } }
                    "challenge" -> challenges.firstOrNull { it.status == com.emotionalleaves.sprout.domain.challenge.ChallengeStatus.ON_TRACK }?.let { p ->
                        item(key = "card-challenge") { com.emotionalleaves.sprout.ui.challenge.ChallengeCard(p, onClick = { navigate("challenges") }) }
                    }
                    "age" -> age?.let { a -> item(key = "card-age") { AgeOfMoneyRow(a) } }
                    "watch" -> if (watch.isNotEmpty()) {
                        item(key = "card-watch-h") { com.emotionalleaves.sprout.ui.common.SectionHeader("Watching") }
                        item(key = "card-watch") { WatchStrip(watch, navigate) }
                    }
                    "links" -> item(key = "card-links") { QuickLinks(navigate) }
                    "insights" -> if (insights.isNotEmpty()) {
                        item(key = "card-insights-h") { com.emotionalleaves.sprout.ui.common.SectionHeader("Insights") }
                        item(key = "card-insights") { com.emotionalleaves.sprout.ui.insights.InsightsStrip(insights, onCategory) }
                    }
                    "payday" -> outlook?.payday?.let { p -> item(key = "card-payday") { PaydayCard(p, vm::setPaydaySource) } }
                    "forecast" -> outlook?.forecast?.let { f -> item(key = "card-forecast") { ForecastCard(f) } }
                    "where" -> if (m != null) item(key = "card-where") { WhereItWentCard(m, onCategory, onSeeBudgets) }
                    "months" -> item(key = "card-months") { MonthsCard(months) }
                    "calendar" -> calendar?.let { cal -> item(key = "card-calendar") { com.emotionalleaves.sprout.ui.insights.SpendCalendarCard(cal) } }
                    "bills" -> if (bills.isNotEmpty()) {
                        item(key = "card-bills-h") { com.emotionalleaves.sprout.ui.common.SectionHeader("Coming up", "All bills", onSeeBills) }
                        item(key = "card-bills") { BillsStrip(bills, onSeeBills) }
                    }
                    "goals" -> if (goals.isNotEmpty()) {
                        item(key = "card-goals-h") { com.emotionalleaves.sprout.ui.common.SectionHeader("Savings goals", "All goals", onGoals) }
                        items(goals, key = { "goal-" + it.goal.id }) { g -> com.emotionalleaves.sprout.ui.goals.GoalProgressRow(g, onClick = onGoals) }
                    }
                    "accounts" -> if (state.accounts.isNotEmpty()) {
                        item(key = "card-accounts-h") { com.emotionalleaves.sprout.ui.common.SectionHeader("Accounts") }
                        item(key = "card-accounts") { AccountsCard(state.accounts, state.settings.lastSuccessfulSyncAt) }
                    }
                    else -> {}
                }
                Unit
            }

            item { com.emotionalleaves.sprout.ui.common.SectionHeader("Recent", "See all", onSeeAllTransactions) }
            items(state.recent, key = { "tx-" + it.id }) { row ->
                TransactionItem(row, showDate = true) { openId = row.id }
            }
            item {
                var customizing by androidx.compose.runtime.remember { mutableStateOf(false) }
                TextButton(onClick = { customizing = true }, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) { Text("Customize Home") }
                if (customizing) CustomizeHomeDialog(off, homeOrder, onToggle = vm::setHomeCard, onMove = { k, d -> homeOrderRaw = moveCard(homeOrder, k, d).joinToString(",") }) { customizing = false }
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
    com.emotionalleaves.sprout.ui.common.SoftCard(onClick = { details = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cash by ${end.format(monthEnd)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "≈ ${com.emotionalleaves.sprout.ui.common.money(f.endMinor, whole = true)}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (f.endMinor < 0) LocalSproutColors.current.danger else MaterialTheme.colorScheme.onSurface,
                )
                if (f.lowMinor < 0 && f.lowMinor < f.endMinor) {
                    Text("Could dip to ${Money.format(f.lowMinor)} around ${f.lowDate.format(monthEnd)} before payday",
                        style = MaterialTheme.typography.bodySmall, color = LocalSproutColors.current.warning)
                }
                f.nextIncome?.let {
                    Text("Next paycheck ${it.date.format(monthEnd)} · ${com.emotionalleaves.sprout.ui.common.money(it.amountMinor, whole = true)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            com.emotionalleaves.sprout.ui.common.IconBadge(
                androidx.compose.material.icons.Icons.Rounded.Insights,
                if (f.endMinor < 0) LocalSproutColors.current.danger else MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (details) ForecastSheet(f) { details = false }
}

/** Day by day to month end: the projected balance (drag to read any day), what's coming, and how it's worked out. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ForecastSheet(f: com.emotionalleaves.sprout.domain.forecast.Forecast, onDismiss: () -> Unit) {
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)
    val sc = LocalSproutColors.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cash to month end", style = MaterialTheme.typography.headlineSmall)
            var picked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
            val shown = picked?.let { f.daily.getOrNull(it) } ?: f.daily.lastOrNull()
            shown?.let { (d, v) ->
                Text("${d.format(dayFmt)} · ≈ ${com.emotionalleaves.sprout.ui.common.money(v, whole = true)}", style = MaterialTheme.typography.titleMedium,
                    color = if (v < 0) sc.danger else MaterialTheme.colorScheme.onSurface)
            }
            if (f.daily.size >= 2) {
                com.emotionalleaves.sprout.ui.common.LineChart(f.daily.map { it.second }, if (f.lowMinor < 0) sc.warning else MaterialTheme.colorScheme.primary,
                    onScrub = { picked = it })
                // The line is scaled to its own range, so say what that range is.
                val hi = f.daily.maxOf { it.second }; val lo = f.daily.minOf { it.second }
                Text("Between ${com.emotionalleaves.sprout.ui.common.money(lo, whole = true)} and ${com.emotionalleaves.sprout.ui.common.money(hi, whole = true)} · drag along the line to see any day.",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (f.events.isNotEmpty()) {
                Text("Coming up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                f.events.forEach { (e, income) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.date.format(dayFmt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp))
                        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text((if (income) "+" else "−") + com.emotionalleaves.sprout.ui.common.money(e.amountMinor, whole = true),
                            style = MaterialTheme.typography.titleSmall, color = if (income) sc.ok else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Text("How it's worked out", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Cash now: ${Money.format(f.startMinor)}", style = MaterialTheme.typography.bodyMedium)
            Text("+ expected paychecks: ${Money.format(f.incomeMinor)}", style = MaterialTheme.typography.bodyMedium)
            Text("− bills still due: ${Money.format(f.billsMinor)}", style = MaterialTheme.typography.bodyMedium)
            Text("− everyday spending: ${Money.format(f.spendingMinor)}" +
                if (f.spendingFromBudgets) " (budget left, spread evenly)" else " (your last 30 days' pace)", style = MaterialTheme.typography.bodyMedium)
            Text("= ≈ ${Money.format(f.endMinor)} at month end", style = MaterialTheme.typography.titleSmall)
            Text("Cash means checking, savings and cash accounts counted in your total. It's an estimate.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        !state.connected && state.accounts.isNotEmpty() ->
            "${state.accounts.count { !it.hidden }} account${if (state.accounts.count { !it.hidden } == 1) "" else "s"} · added by hand or imported"
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

/** On an empty Home: what Sprout will do once your data's in, so a first look isn't just a blank page. */
@Composable
private fun WhatYouGet() {
    val sc = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
    com.emotionalleaves.sprout.ui.common.SoftCard {
        Text("Once your money's in", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        listOf(
            Triple(androidx.compose.material.icons.Icons.Rounded.Savings, "What's safe to spend", "Today, after bills and budgets are covered."),
            Triple(androidx.compose.material.icons.Icons.Rounded.Event, "Bills found for you", "Subscriptions and repeating charges, with what's due next."),
            Triple(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, "Purchases sorted for you", "Thousands of stores and Lincoln places known, and it learns your picks."),
        ).forEach { (icon, title, body) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.emotionalleaves.sprout.ui.common.IconBadge(icon, sc.ok, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    Text(body, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
