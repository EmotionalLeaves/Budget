package com.emotionalleaves.sprout.ui.insights

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.merchant.MerchantStats
import com.emotionalleaves.sprout.domain.merchant.MerchantTx
import com.emotionalleaves.sprout.ui.common.BarChart
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.StatTile
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.common.money
import com.emotionalleaves.sprout.ui.common.showUndoable
import androidx.compose.runtime.setValue
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)

/** Everything you've spent at one merchant: totals, average, 6-month chart and every transaction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantScreen(merchantKey: String, snackbar: androidx.compose.material3.SnackbarHostState, onBack: () -> Unit) {
    val c = appContainer()
    var openId by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    openId?.let { id ->
        com.emotionalleaves.sprout.ui.transactions.TransactionDetailSheet(id, onDismiss = { openId = null },
            onMessage = { msg, undo -> scope.showUndoable(snackbar, msg, undo) })
    }
    val rows by remember(merchantKey) { c.db.insights().observeMerchant(merchantKey) }.collectAsStateWithLifecycle(emptyList())
    val zone = ZoneId.systemDefault()
    val stats = remember(rows) { MerchantStats.of(rows.map { MerchantTx(it.amountMinor, it.occurredAt) }, YearMonth.now(zone), zone) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), 
            title = { Text(com.emotionalleaves.sprout.ui.common.merchantName(merchantKey)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                var renaming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.material3.TextButton(onClick = { renaming = true }) { Text("Rename") }
                PinButton("m:$merchantKey")
                if (renaming) RenameMerchantDialog(merchantKey) { renaming = false }
            },
        )
        LazyColumn {
            item {
                com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    StatTile("This year", money(stats.thisYearMinor, whole = true), Modifier.tile())
                    StatTile("Per visit", money(stats.averageMinor), Modifier.tile())
                    StatTile("Visits", stats.visits.toString(), Modifier.tile())
                }
            }
            item {
                SoftCard {
                    Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                    BarChart(stats.byMonth.map { it.second }, stats.byMonth.map { it.first.format(shortMonth) }, MaterialTheme.colorScheme.primary)
                    stats.firstAt?.let {
                        Text("Since ${Format.dateTime(it).substringBefore(',')} · ${money(stats.totalSpentMinor)} in all",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (rows.isNotEmpty() && !com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer.isGeneric(merchantKey)) {
                item { MerchantCategoryCard(merchantKey, rows, snackbar) }
            }
            itemsIndexed(rows, key = { _, it -> it.id }) { i, r ->
                val first = i == 0
                val last = i == rows.lastIndex
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
                    bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp,
                )
                Column(Modifier.padding(horizontal = 12.dp).padding(top = if (first) 8.dp else 0.dp).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    TransactionItem(r, dateAsTitle = true) { openId = r.id }
                    if (!last) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}


/**
 * "Hy-Vee goes in Groceries · Change": the place's usual category, and one tap to move every purchase there
 * (and future ones) to another, with Undo. The fastest way to fix a place that's sorted wrong.
 */
@Composable
private fun MerchantCategoryCard(
    merchantKey: String,
    rows: List<com.emotionalleaves.sprout.data.db.TransactionRow>,
    snackbar: androidx.compose.material3.SnackbarHostState,
) {
    val c = appContainer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var picking by remember { androidx.compose.runtime.mutableStateOf(false) }
    val usual = rows.groupingBy { it.categoryName }.eachCount().maxByOrNull { it.value }?.key
    val name = com.emotionalleaves.sprout.ui.common.merchantName(merchantKey)
    SoftCard(onClick = { picking = true }) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(usual ?: "—", style = MaterialTheme.typography.titleMedium)
                Text("Change it here to move every purchase from $name, now and later.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.FilledTonalButton(onClick = { picking = true }) { Text("Change") }
        }
    }
    if (picking) {
        val categories by remember { c.db.categories().observeActive() }.collectAsStateWithLifecycle(emptyList())
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Everything from $name goes in…") },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.emotionalleaves.sprout.ui.categories.CategoryGrid(categories, null) { cat ->
                        picking = false
                        scope.launch {
                            val change = c.categoryRepository.recategorizeWithUndo(rows.first().id, cat.id, alwaysForMerchant = true)
                            c.alertRepository.evaluateBudgets()
                            scope.showUndoable(snackbar, "Moved ${change.others + 1} from $name to ${cat.name}", change.undo)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

/** Pin/unpin a category ("c:<id>") or merchant ("m:<key>") to the Home watchlist. */
@Composable
fun PinButton(item: String) {
    val c = appContainer()
    val pinned by remember { c.settings.settings }.collectAsStateWithLifecycle(com.emotionalleaves.sprout.data.settings.Settings())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val on = item in pinned.watchlist
    androidx.compose.material3.TextButton(onClick = { scope.launch { c.settings.toggleWatch(item) } }) {
        Text(if (on) "Pinned to Home" else "Pin to Home")
    }
}


/** Give a merchant your own name. Empty puts back Sprout's name. Rules and search keep using the bank's text. */
@Composable
fun RenameMerchantDialog(merchantKey: String, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val current = com.emotionalleaves.sprout.ui.common.merchantName(merchantKey)
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename merchant") },
        text = {
            Column {
                Text("Shown everywhere in Sprout instead of \"$merchantKey\".", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(text, { text = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val clean = com.emotionalleaves.sprout.domain.text.Sanitize.text(text, 60).trim()
                scope.launch {
                    if (clean.isEmpty() || clean == com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer.displayName(merchantKey)) c.db.merchantNames().delete(merchantKey)
                    else c.db.merchantNames().upsert(com.emotionalleaves.sprout.data.db.MerchantNameEntity(merchantKey, clean, System.currentTimeMillis() / 1000))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
