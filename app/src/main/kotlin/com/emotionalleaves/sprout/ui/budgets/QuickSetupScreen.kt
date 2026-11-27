package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.budget.Suggestion
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickSetupViewModel(private val c: AppContainer) : ViewModel() {
    private val _suggestions = MutableStateFlow<Map<Long, Suggestion>?>(null)
    val suggestions: StateFlow<Map<Long, Suggestion>?> = _suggestions.asStateFlow()
    private val _current = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val current: StateFlow<Map<Long, Long>> = _current.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = c.db.categories().observeActive()
        .map { list -> list.filter { it.kind == CategoryKind.EXPENSE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _current.value = c.db.budgets().observeDefaults().first().associate { it.categoryId to it.amountMinor }
            _suggestions.value = c.budgetRepository.suggestions().associateBy { it.categoryId }
        }
    }

    private val _template = MutableStateFlow<Pair<Long, Map<Long, Long>>?>(null)
    val template: StateFlow<Pair<Long, Map<Long, Long>>?> = _template.asStateFlow()
    fun loadTemplate() = viewModelScope.launch { _template.value = c.budgetRepository.fiftyThirtyTwenty() }

    fun apply(amounts: Map<Long, Long>, done: () -> Unit) = viewModelScope.launch {
        c.budgetRepository.applySuggestions(amounts)
        done()
    }
}

private fun minorToText(v: Long): String = if (v % 100 == 0L) (v / 100).toString() else Money.format(v).removePrefix("$").replace(",", "")

/**
 * Suggests a monthly budget per category from the last 3 full months of spending (rounded up to $10).
 * Every field can be edited; empty fields are left without a budget.
 */
@Composable
fun QuickSetupScreen(onBack: () -> Unit) {
    val vm = sproutViewModel { QuickSetupViewModel(it) }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val edits = remember { mutableStateMapOf<Long, String>() }
    val template by vm.template.collectAsStateWithLifecycle()
    var useTemplate by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Quick budget setup", style = MaterialTheme.typography.titleLarge)
        }
        val sugg = suggestions ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        Text(
            if (sugg.isEmpty()) "Not enough history for suggestions yet. You can still type amounts below."
            else "Suggested from your average spending over the last 3 full months, rounded up to the next \$10. Edit anything, then save.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.FilterChip(!useTemplate, { useTemplate = false; edits.clear() }, label = { Text("From my spending") },
                modifier = Modifier.heightIn(min = 48.dp))
            androidx.compose.material3.FilterChip(useTemplate, { useTemplate = true; edits.clear(); vm.loadTemplate() }, label = { Text("50/30/20 of income") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        if (useTemplate) {
            val t = template
            Text(
                when {
                    t == null -> "Working it out…"
                    t.first <= 0 -> "No income found in the last 3 months, so there's nothing to split yet."
                    else -> "Your income averages ${Money.formatWhole(t.first)}/month: 50% for needs (rent, groceries, bills…), " +
                        "30% for wants, and 20% (${Money.formatWhole(t.first / 5)}) left for savings and debt."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val templateAmounts = if (useTemplate) template?.second.orEmpty() else emptyMap()
        fun valueFor(id: Long): String = edits[id]
            ?: (if (useTemplate) templateAmounts[id] else (current[id] ?: sugg[id]?.suggestedMinor))?.let(::minorToText) ?: ""

        // Categories with something to suggest first; the rest tucked away until you want them.
        val (main, others) = categories.partition { c ->
            // Stable while you type: a field you've touched never jumps into the hidden group.
            sugg[c.id] != null || current[c.id] != null || templateAmounts[c.id] != null || c.id in edits
        }
        var showOthers by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(main + if (showOthers) others else emptyList(), key = { it.id }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.emotionalleaves.sprout.ui.common.IconBadge(com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(c.iconKey),
                        androidx.compose.ui.graphics.Color(c.colorArgb), 36.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        val s = sugg[c.id]
                        val cur = current[c.id]
                        val note = when {
                            s == null -> null
                            !useTemplate && cur != null && cur != s.suggestedMinor -> "You usually spend about ${Money.formatWhole(s.suggestedMinor)}"
                            else -> "Usually about ${Money.formatWhole(s.suggestedMinor)}"
                        }
                        note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    OutlinedTextField(
                        value = valueFor(c.id),
                        onValueChange = { edits[c.id] = it.take(10) },
                        prefix = { Text("$") },
                        singleLine = true,
                        isError = valueFor(c.id).isNotBlank() && Money.parseUserInput(valueFor(c.id)) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
            if (others.isNotEmpty()) item {
                androidx.compose.material3.TextButton(onClick = { showOthers = !showOthers }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showOthers) "Hide categories without spending" else "More categories (${others.size})")
                }
            }
        }
        val total = categories.sumOf { c -> valueFor(c.id).takeIf { it.isNotBlank() }?.let { Money.parseUserInput(it) }?.coerceAtLeast(0) ?: 0L }
        Text("Total ${Money.formatWhole(total)} a month", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
        Button(
            onClick = {
                val amounts = categories.mapNotNull { c ->
                    val text = valueFor(c.id)
                    if (text.isBlank()) null else Money.parseUserInput(text)?.takeIf { it >= 0 }?.let { c.id to it }
                }.toMap()
                vm.apply(amounts, onBack)
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
        ) { Text("Save budgets") }
    }
}
