package com.emotionalleaves.sprout.ui.transactions

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.common.showUndoable
import kotlinx.coroutines.launch

/**
 * The review inbox: new transactions since you last looked. Check each one (fix its category if needed), then
 * swipe it away or tap ✓; "All reviewed" clears the list. Undo is one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(snackbar: SnackbarHostState, onBack: () -> Unit) {
    val c = appContainer()
    val rows by remember { c.db.transactions().observeInbox() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    fun now() = System.currentTimeMillis() / 1000
    fun markSeen(r: List<TransactionRow>) = scope.launch {
        val ids = r.map { it.id }
        c.db.transactions().markReviewed(ids, now())
        scope.showUndoable(snackbar, if (ids.size == 1) "Marked as reviewed" else "${ids.size} marked as reviewed") {
            c.db.transactions().markUnreviewed(ids)
        }
    }
    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg, undo -> scope.showUndoable(snackbar, msg, undo) })
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), 
            title = { Text("To review") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                if (rows.isNotEmpty()) TextButton(onClick = { markSeen(rows) }) { Icon(Icons.Rounded.DoneAll, null); Text(" All reviewed") }
            },
        )
        if (rows.isEmpty()) {
            CaughtUp()
            return@Column
        }
        LazyColumn {
            item {
                SoftCard {
                    Text("${rows.size} new · ${Money.format(-rows.filter { it.amountMinor < 0 }.sumOf { it.amountMinor })} spent",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Tap one to change its category or split it. Swipe right (or tap ✓) when it looks right.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(rows, key = { it.id }) { r ->
                val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                    if (v == SwipeToDismissBoxValue.StartToEnd) { markSeen(listOf(r)); true } else false
                })
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromEndToStart = false,
                    backgroundContent = {
                        // Only while swiping: otherwise the label would show through the row.
                        if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Row(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("  Reviewed", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                ) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { TransactionItem(r, showDate = true) { openId = r.id } }
                        IconButton(onClick = { markSeen(listOf(r)) }) { Icon(Icons.Rounded.Check, contentDescription = "Mark ${r.description} reviewed") }
                    }
                }
            }
        }
    }
}

/** Home card: "5 new transactions to review". */
@Composable
fun InboxCard(count: Int, onOpen: () -> Unit) {
    if (count <= 0) return
    SoftCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.emotionalleaves.sprout.ui.common.IconBadge(Icons.Rounded.DoneAll, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("$count new to review", style = MaterialTheme.typography.titleSmall)
                Text("Check categories in one place", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onOpen, modifier = Modifier.heightIn(min = 44.dp)) { Text("Review") }
        }
    }
}

/** A small, quiet celebration when the inbox hits zero: the check pops in, then the message. */
@Composable
private fun CaughtUp() {
    val scale = remember { androidx.compose.animation.core.Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(96.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp)) }
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        Text("You're all caught up", style = MaterialTheme.typography.headlineSmall)
        Text("New transactions show up here after each sync, so you can check their categories in one place.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
