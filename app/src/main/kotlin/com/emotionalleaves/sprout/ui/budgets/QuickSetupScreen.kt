package com.emotionalleaves.sprout.ui.budgets

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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Quick budget setup", style = MaterialTheme.typography.titleLarge)
        }
        val sugg = suggestions ?: return@Column
        Text(
            if (sugg.isEmpty()) "Not enough history for suggestions yet. You can still type amounts below."
            else "Suggested from your average spending over the last 3 full months, rounded up to the next \$10. Edit anything, then save.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        fun valueFor(id: Long): String = edits[id] ?: (current[id] ?: sugg[id]?.suggestedMinor)?.let(::minorToText) ?: ""

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        sugg[c.id]?.let { Text("Suggested ${Money.formatWhole(it.suggestedMinor)}", style = MaterialTheme.typography.labelSmall) }
                    }
                    OutlinedTextField(
                        value = valueFor(c.id),
                        onValueChange = { edits[c.id] = it.take(10) },
                        prefix = { Text("$") },
                        singleLine = true,
                        isError = valueFor(c.id).isNotBlank() && Money.parseUserInput(valueFor(c.id)) == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(130.dp),
                    )
                }
            }
        }
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
