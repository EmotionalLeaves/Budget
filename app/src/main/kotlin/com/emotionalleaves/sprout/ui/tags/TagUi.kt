package com.emotionalleaves.sprout.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.TagEntity
import com.emotionalleaves.sprout.data.db.TagSummary
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.IconBadge
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tag chips for the transaction sheet, with "Add tag" (pick an existing tag or type a new one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionTags(c: AppContainer, transactionId: String) {
    val mine by remember(transactionId) { c.db.tags().observeFor(transactionId) }.collectAsStateWithLifecycle(emptyList())
    val all by remember { c.db.tags().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    Text("Tags", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        mine.forEach { t ->
            InputChip(
                selected = true,
                onClick = { scope.launch { c.tagRepository.detach(transactionId, t.id) } },
                label = { Text("#" + t.name) },
                trailingIcon = { Text("✕") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        AssistChip(onClick = { adding = true }, label = { Text("Add tag") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    if (adding) {
        AddTagDialog(
            existing = all.filter { a -> mine.none { it.id == a.id } },
            onPick = { id -> scope.launch { c.tagRepository.attach(transactionId, id) }; adding = false },
            onCreate = { name -> scope.launch { c.tagRepository.getOrCreate(name)?.let { c.tagRepository.attach(transactionId, it) } }; adding = false },
            onDismiss = { adding = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTagDialog(existing: List<TagEntity>, onPick: (Long) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val matches = existing.filter { text.isBlank() || it.name.contains(text.trim().removePrefix("#"), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                    label = { Text("New tag, like \"Chicago trip\"") }, modifier = Modifier.fillMaxWidth(),
                )
                if (matches.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.take(20).forEach { t ->
                            FilterChip(false, onClick = { onPick(t.id) }, label = { Text("#" + t.name) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(text) }, enabled = text.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class TagsViewModel(private val c: AppContainer) : ViewModel() {
    val tags: StateFlow<List<TagSummary>> = c.db.tags().observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun rename(id: Long, name: String) = viewModelScope.launch { c.tagRepository.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { c.tagRepository.delete(id) }
}

/** Every tag with its total; tap to see its transactions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = sproutViewModel { TagsViewModel(it) }
    val tags by vm.tags.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TagSummary?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tags") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        if (tags.isEmpty()) {
            EmptyState("No tags yet", "Open any transaction and tap \"Add tag\" to group spending across categories, like a trip or a project.")
            return@Column
        }
        LazyColumn {
            items(tags, key = { it.id }) { t ->
                SoftCard(onClick = { onOpen(t.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Tag, Color(t.colorArgb))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("#" + t.name, style = MaterialTheme.typography.titleSmall)
                            Text("${t.count} transaction${if (t.count == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(-t.total), style = MaterialTheme.typography.titleSmall)
                            Text("Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { editing = t }.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
    editing?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit tag") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.rename(t.id, name); editing = null }) { Text("Save") } },
            dismissButton = {
                TextButton(onClick = { vm.delete(t.id); editing = null }) { Text("Delete tag", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}
