package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.MerchantSum
import com.emotionalleaves.sprout.data.db.RuleRow
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.SectionTitle
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import com.emotionalleaves.sprout.ui.transactions.TransactionDetailSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CategoryDetailViewModel(private val c: AppContainer, val categoryId: Long) : ViewModel() {
    private val month: YearMonth = c.budgetRepository.currentMonth()
    private val window = MonthWindow.of(month, ZoneId.systemDefault())

    val category: StateFlow<CategoryEntity?> = c.db.categories().observeAll().map { l -> l.firstOrNull { it.id == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val budgetMonth: StateFlow<BudgetMonth?> = c.budgetRepository.observeMonth(month)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val merchants: StateFlow<List<MerchantSum>> = c.db.budgets().observeMerchantSums(categoryId, window.start, window.end)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions: StateFlow<List<TransactionRow>> =
        c.db.transactions().observeRows("", 200, categoryId, window.start, window.end)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules: StateFlow<List<RuleRow>> = c.db.merchantRules().observeRows().map { l -> l.filter { it.categoryId == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _history = MutableStateFlow<List<Pair<YearMonth, Long>>>(emptyList())
    val history: StateFlow<List<Pair<YearMonth, Long>>> = _history.asStateFlow()

    init {
        viewModelScope.launch { _history.value = c.budgetRepository.history(categoryId, 6) }
    }
}

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)

@Composable
fun CategoryDetailScreen(categoryId: Long, snackbar: SnackbarHostState, onBack: () -> Unit, onEditCategories: () -> Unit) {
    val vm = sproutViewModel(key = "cat-$categoryId") { CategoryDetailViewModel(it, categoryId) }
    val category by vm.category.collectAsStateWithLifecycle()
    val month by vm.budgetMonth.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    openId?.let { id -> TransactionDetailSheet(id, { openId = null }) { msg -> scope.launch { snackbar.showSnackbar(msg) } } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(category?.name ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onEditCategories, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
            }
        }
        val line = month?.let { m -> (m.budgeted + m.unbudgetedWithSpending).firstOrNull { it.category.id == categoryId } }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This month", style = MaterialTheme.typography.labelLarge)
                    val spent = line?.spentMinor ?: 0L
                    val budget = line?.budgetMinor
                    Text(Money.format(spent), style = MaterialTheme.typography.headlineMedium)
                    if (budget != null) {
                        BudgetProgress(spent, budget, line.level)
                        Text("of ${Money.format(budget)} · ${leftOrOver(budget - spent)}", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("No budget set", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("Last 6 months") }
            item { HistoryChart(history, category?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary) }
        }
        if (merchants.isNotEmpty()) {
            item { SectionTitle("By merchant this month") }
            items(merchants, key = { "m-" + it.merchantKey }) { m ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(MerchantNormalizer.displayName(m.merchantKey) + " (${m.count})", Modifier.weight(1f))
                    Text(Money.format(maxOf(0, -m.total)))
                }
            }
        }
        if (rules.isNotEmpty()) {
            item { SectionTitle("Rules for this category") }
            items(rules, key = { "r-" + it.id }) { r ->
                Text(
                    if (r.matchType == RuleType.EXACT) MerchantNormalizer.displayName(r.pattern) else "Contains \"${r.pattern}\"",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        item { SectionTitle("Transactions this month") }
        items(txs, key = { "t-" + it.id }) { row ->
            TransactionItem(row) { openId = row.id }
            HorizontalDivider(Modifier.padding(start = 16.dp))
        }
    }
}

/** A simple bar chart drawn with Canvas. TalkBack reads every month's amount. */
@Composable
private fun HistoryChart(history: List<Pair<YearMonth, Long>>, barColor: Color) {
    val max = history.maxOf { it.second }.coerceAtLeast(1)
    val description = history.joinToString("; ") { (m, v) -> "${m.format(shortMonth)} ${Money.formatWhole(v)}" }
    Column(Modifier.padding(horizontal = 16.dp).semantics(mergeDescendants = true) { contentDescription = "Spending by month: $description" }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = history.size
            val slot = size.width / n
            val barW = slot * 0.6f
            history.forEachIndexed { i, (_, v) ->
                val h = (v.toFloat() / max) * size.height
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            history.forEach { (m, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.format(shortMonth), style = MaterialTheme.typography.labelSmall)
                    Text(Money.formatWhole(v), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
