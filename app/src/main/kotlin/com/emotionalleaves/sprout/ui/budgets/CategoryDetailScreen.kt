package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import com.emotionalleaves.sprout.ui.common.readable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import com.emotionalleaves.sprout.ui.common.showUndoable
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CategoryDetailViewModel(private val c: AppContainer, val categoryId: Long) : ViewModel() {
    private val _month = MutableStateFlow(c.budgetRepository.currentMonth())
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    private fun window(m: YearMonth) = MonthWindow.of(m, ZoneId.systemDefault())

    fun prev() { _month.value = _month.value.minusMonths(1) }
    fun next() { _month.value = _month.value.plusMonths(1) }

    val category: StateFlow<CategoryEntity?> = c.db.categories().observeAll().map { l -> l.firstOrNull { it.id == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val budgetMonth: StateFlow<BudgetMonth?> = _month.flatMapLatest { c.budgetRepository.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val merchants: StateFlow<List<MerchantSum>> = _month.flatMapLatest { m ->
        val w = window(m)
        c.db.budgets().observeMerchantSums(categoryId, w.start, w.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions: StateFlow<List<TransactionRow>> = _month.flatMapLatest { m ->
        val w = window(m)
        c.db.transactions().observeRows("", 200, categoryId, w.start, w.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules: StateFlow<List<RuleRow>> = c.db.merchantRules().observeRows().map { l -> l.filter { it.categoryId == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _history = MutableStateFlow<List<Pair<YearMonth, Long>>>(emptyList())
    val history: StateFlow<List<Pair<YearMonth, Long>>> = _history.asStateFlow()

    init {
        viewModelScope.launch { _history.value = c.budgetRepository.history(categoryId, 6) }
    }
}

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
fun CategoryDetailScreen(categoryId: Long, snackbar: SnackbarHostState, onBack: () -> Unit, onEditCategories: () -> Unit) {
    val vm = sproutViewModel(key = "cat-$categoryId") { CategoryDetailViewModel(it, categoryId) }
    val category by vm.category.collectAsStateWithLifecycle()
    val budgetMonth by vm.budgetMonth.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    openId?.let { id -> TransactionDetailSheet(id, { openId = null }) { msg, undo -> scope.showUndoable(snackbar, msg, undo) } }

    // Swipe sideways to change month, same as Budgets.
    val swipe = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    LazyColumn(Modifier.fillMaxSize().pointerInput(Unit) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragEnd = { if (dx > swipe) vm.prev() else if (dx < -swipe) vm.next() },
            onHorizontalDrag = { _, amount -> dx += amount },
        )
    }) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(category?.name ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                com.emotionalleaves.sprout.ui.insights.PinButton("c:$categoryId")
                TextButton(onClick = onEditCategories, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
            }
        }
        val line = budgetMonth?.let { m -> m.allLines.firstOrNull { it.category.id == categoryId } }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::prev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
                Text(month.format(monthTitle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
            }
        }
        item {
            com.emotionalleaves.sprout.ui.common.SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val spent = line?.spentMinor ?: 0L
                    val budget = line?.budgetMinor
                    val tint = category?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(spent), style = MaterialTheme.typography.displaySmall)
                        }
                        if (budget != null) {
                            com.emotionalleaves.sprout.ui.common.ProgressRing(
                                if (budget <= 0) 1f else spent.toFloat() / budget, levelColor(line.level), MaterialTheme.colorScheme.surfaceVariant,
                                Modifier.size(84.dp), stroke = 9.dp,
                            ) {
                                com.emotionalleaves.sprout.ui.common.IconBadge(
                                    com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(category?.iconKey), tint, 40.dp,
                                )
                            }
                        } else {
                            com.emotionalleaves.sprout.ui.common.IconBadge(com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(category?.iconKey), tint, 56.dp)
                        }
                    }
                    if (budget != null) {
                        Text("of ${Money.format(budget)} · ${leftOrOver(budget - spent)}", style = MaterialTheme.typography.bodyMedium)
                        val base = line.baseBudgetMinor
                        if (line.rolloverOn && base != null) {
                            val carry = line.carryMinor
                            Text(
                                "Set ${Money.format(base)} · " +
                                    (if (carry >= 0) "rolled over +${Money.format(carry)}" else "overspent before −${Money.format(-carry)}") +
                                    " · available ${Money.format(budget)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text("No budget set", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("Last 6 months") }
            item {
                val col = category?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                val spent = history.map { it.second }.filter { it > 0 }
                com.emotionalleaves.sprout.ui.common.SoftCard {
                    Text(if (spent.isEmpty()) "Nothing yet" else "Usually ${Money.formatWhole(spent.sum() / spent.size)} a month",
                        style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    com.emotionalleaves.sprout.ui.common.BarChart(
                        values = history.map { it.second }, labels = history.map { it.first.format(shortMonth) },
                        color = col.copy(alpha = 0.45f), highlight = col,
                        average = if (spent.isEmpty()) null else spent.sum() / spent.size,
                    )
                }
            }
        }
        if (merchants.isNotEmpty()) {
            item { SectionTitle("By merchant") }
            item {
                val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
                val readable = (category?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary).readable()
                val biggest = merchants.maxOf { maxOf(0, -it.total) }.coerceAtLeast(1)
                val total = merchants.sumOf { maxOf(0, -it.total) }.coerceAtLeast(1)
                com.emotionalleaves.sprout.ui.common.SoftCard {
                    merchants.forEach { m ->
                        val amt = maxOf(0, -m.total)
                        Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { navigate("merchant/" + android.net.Uri.encode(m.merchantKey)) }.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(com.emotionalleaves.sprout.ui.common.merchantName(m.merchantKey), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(Money.format(amt), style = MaterialTheme.typography.titleSmall)
                            }
                            Text("${m.count} ${if (m.count == 1) "visit" else "visits"} · ${amt * 100 / total}% of this category",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(amt.toFloat() / biggest).height(6.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(readable))
                            }
                        }
                    }
                }
            }
        }
        if (rules.isNotEmpty()) {
            item { SectionTitle("Rules for this category") }
            items(rules, key = { "r-" + it.id }) { r ->
                Text(
                    if (r.matchType == RuleType.EXACT) com.emotionalleaves.sprout.ui.common.merchantName(r.pattern) else "Contains \"${r.pattern}\"",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        item { SectionTitle("Transactions") }
        itemsIndexed(txs, key = { _, it -> "t-" + it.id }) { i, row ->
            val first = i == 0
            val last = i == txs.lastIndex
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
                bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp,
            )
            Column(Modifier.padding(horizontal = 12.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                TransactionItem(row, showDate = true) { openId = row.id }
                if (!last) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

/** A simple bar chart drawn with Canvas. TalkBack reads every month's amount. */
