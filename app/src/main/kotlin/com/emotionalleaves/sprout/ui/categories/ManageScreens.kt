package com.emotionalleaves.sprout.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.categories.CategoryRepository
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.RuleRow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageCategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val all: StateFlow<List<CategoryEntity>> =
        c.db.categories().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active: StateFlow<List<CategoryEntity>> =
        c.db.categories().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules: StateFlow<List<RuleRow>> =
        c.db.merchantRules().observeRows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, color: Int) = viewModelScope.launch { c.categoryRepository.addCategory(name, CategoryKind.EXPENSE, color) }
    fun update(id: Long, name: String, color: Int) = viewModelScope.launch { c.categoryRepository.updateCategory(id, name, color) }
    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { c.categoryRepository.setArchived(id, archived) }
    fun addKeywordRule(keyword: String, categoryId: Long) = viewModelScope.launch { c.categoryRepository.addContainsRule(keyword, categoryId) }
    fun changeRule(ruleId: Long, categoryId: Long) = viewModelScope.launch { c.categoryRepository.changeRuleCategory(ruleId, categoryId) }
    fun deleteRule(ruleId: Long) = viewModelScope.launch { c.categoryRepository.deleteRule(ruleId) }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Settings → Categories: add, rename, recolor, archive. */
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = sproutViewModel { ManageCategoriesViewModel(it) }
    val all by vm.all.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Categories", onBack) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add category") }
        }
        LazyColumn {
            items(all, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editing = c }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(c.colorArgb)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            kindLabel(c.kind) + if (c.archived) " · archived" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }

    if (adding) {
        CategoryDialog(title = "New category", initialName = "", initialColor = CategoryRepository.PALETTE.first(), archived = null,
            onDismiss = { adding = false }, onSave = { name, color, _ -> vm.add(name, color); adding = false })
    }
    editing?.let { c ->
        CategoryDialog(title = "Edit category", initialName = c.name, initialColor = c.colorArgb, archived = c.archived,
            onDismiss = { editing = null },
            onSave = { name, color, archived ->
                vm.update(c.id, name, color)
                if (archived != null && archived != c.archived) vm.setArchived(c.id, archived)
                editing = null
            })
    }
}

private fun kindLabel(kind: CategoryKind) = when (kind) {
    CategoryKind.INCOME -> "Income"
    CategoryKind.EXPENSE -> "Spending"
    CategoryKind.TRANSFER -> "Transfer (not counted as spending)"
}

@Composable
private fun CategoryDialog(
    title: String, initialName: String, initialColor: Int, archived: Boolean?,
    onDismiss: () -> Unit, onSave: (String, Int, Boolean?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    var isArchived by remember { mutableStateOf(archived) }
    val palette = (listOf(initialColor) + CategoryRepository.PALETTE).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                Labeled("Color") { ColorPicker(palette, color) { color = it } }
                if (isArchived != null) {
                    TextButton(onClick = { isArchived = !(isArchived ?: false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (isArchived == true) "Unarchive (show in pickers again)" else "Archive (hide from pickers)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color, isArchived) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Settings → Category rules: learned merchant rules and keyword rules. */
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val vm = sproutViewModel { ManageCategoriesViewModel(it) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val categories by vm.active.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RuleRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Category rules", onBack) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add keyword rule") }
        }
        if (rules.isEmpty()) {
            EmptyState(
                "No rules yet",
                "When you change a transaction's category with \"Always use this\" on, Sprout remembers it here. " +
                    "Tap + to add a keyword rule.",
            )
        }
        LazyColumn {
            items(rules, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { editing = r }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (r.matchType == RuleType.EXACT) MerchantNormalizer.displayName(r.pattern) else "Contains \"${r.pattern}\"",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text("→ ${r.categoryName}", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { vm.deleteRule(r.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete rule") }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }

    editing?.let { r ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Change category") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { CategoryGrid(categories, r.categoryId) { vm.changeRule(r.id, it.id); editing = null } } },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }
    if (adding) {
        var keyword by remember { mutableStateOf("") }
        var picked by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Keyword rule") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Any transaction whose description contains this word gets the category you pick.")
                    OutlinedTextField(value = keyword, onValueChange = { keyword = it.take(60) }, label = { Text("Keyword") }, singleLine = true)
                    CategoryGrid(categories, picked) { picked = it.id }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { picked?.let { vm.addKeywordRule(keyword, it) }; adding = false },
                    enabled = keyword.trim().length >= 2 && picked != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
