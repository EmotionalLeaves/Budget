package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.budget.CategoryLine
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.SectionTitle
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class BudgetsViewModel(private val c: AppContainer) : ViewModel() {
    private val _month = MutableStateFlow(c.budgetRepository.currentMonth())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<BudgetMonth?> = _month.flatMapLatest { c.budgetRepository.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun prev() = _month.update { it.minusMonths(1) }
    fun next() = _month.update { it.plusMonths(1) }
    fun isCurrent() = _month.value == c.budgetRepository.currentMonth()

    fun save(categoryId: Long, amount: Long, thisMonthOnly: Boolean) =
        viewModelScope.launch { c.budgetRepository.setBudget(categoryId, amount, thisMonthOnly, _month.value) }

    fun remove(categoryId: Long, thisMonthOnly: Boolean) =
        viewModelScope.launch { c.budgetRepository.removeBudget(categoryId, thisMonthOnly, _month.value) }
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
fun BudgetsScreen(onQuickSetup: () -> Unit, onCategory: (Long) -> Unit) {
    val vm = sproutViewModel { BudgetsViewModel(it) }
    val month by vm.month.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryLine?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::prev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
            Text(
                month.format(monthFormat),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
        }
        val m = data ?: return@Column
        LazyColumn(Modifier.fillMaxSize()) {
            item { SafeToSpendCard(m, onSetBudget = onQuickSetup) }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Budgets", Modifier.weight(1f))
                    TextButton(onClick = onQuickSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quick setup") }
                }
            }
            if (m.budgeted.isEmpty()) {
                item {
                    Text(
                        "No budgets yet. Use Quick setup to start from your recent spending, or tap a category below.",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(m.budgeted, key = { "b-" + it.category.id }) { line ->
                CategoryBudgetRow(line) { onCategory(line.category.id) }
                Row(Modifier.padding(start = 8.dp)) {
                    TextButton(onClick = { editing = line }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit budget") }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
            if (m.unbudgetedWithSpending.isNotEmpty()) {
                item { SectionTitle("Not budgeted") }
                items(m.unbudgetedWithSpending, key = { "u-" + it.category.id }) { line ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { onCategory(line.category.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(line.category.name, style = MaterialTheme.typography.bodyLarge)
                            }
                            Text("Spent ${Money.format(line.spentMinor)}", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 12.dp))
                        }
                        TextButton(onClick = { editing = line }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add budget") }
                    }
                }
            }
            item {
                Text(
                    "Total spending this month: ${Money.format(m.totalSpentMinor)} (transfers and income never count)",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { line ->
        EditBudgetDialog(
            categoryName = line.category.name,
            initialMinor = line.budgetMinor,
            hasBudget = line.budgetMinor != null,
            onDismiss = { editing = null },
            onSave = { amount, thisMonth -> vm.save(line.category.id, amount, thisMonth); editing = null },
            onRemove = { thisMonth -> vm.remove(line.category.id, thisMonth); editing = null },
        )
    }
}
