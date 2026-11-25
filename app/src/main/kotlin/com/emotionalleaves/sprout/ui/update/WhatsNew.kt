package com.emotionalleaves.sprout.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.BuildConfig
import com.emotionalleaves.sprout.data.update.UpdateRepository
import com.emotionalleaves.sprout.update.Changelog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the bundled changelog section for this version. Empty when it can't. */
suspend fun bundledNotes(context: android.content.Context, version: String = BuildConfig.VERSION_NAME): List<Changelog.Group> =
    withContext(Dispatchers.IO) {
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }
            .map { Changelog.section(it, version) }.getOrDefault(emptyList())
    }

/** After an update: a one-time sheet with what's new in this version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewAfterUpdate(updates: UpdateRepository) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf<List<Changelog.Group>>(emptyList()) }
    LaunchedEffect(Unit) {
        val from = updates.consumeJustUpdatedFrom() ?: return@LaunchedEffect
        val md = withContext(Dispatchers.IO) { runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrDefault("") }
        val all = Changelog.between(md, from, BuildConfig.VERSION_NAME)
        // Several versions at once: each version's notes under its own heading.
        notes = if (all.size <= 1) bundledNotes(context) else all.flatMap { (v, groups) -> listOf(Changelog.Group("Version $v", emptyList())) + groups }
    }
    if (notes.isEmpty()) return
    ModalBottomSheet(onDismissRequest = { notes = emptyList() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("What's new in ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            }
            WhatsNewList(notes)
            Button(onClick = { notes = emptyList() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 16.dp)) { Text("Got it") }
        }
    }
}

@Composable
fun WhatsNewList(notes: List<Changelog.Group>) {
    notes.forEach { g ->
        if (g.heading.isNotBlank()) Text(g.heading,
            style = if (g.heading.startsWith("Version ")) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        g.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
