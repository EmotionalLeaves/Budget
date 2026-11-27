package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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

    val mode: StateFlow<Int> = c.settings.settings.map { it.budgetMode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun suggestedFlex(): Long? = c.flexRepository.suggestedFlex()
    suspend fun suggestionFor(categoryId: Long): Long? = c.budgetRepository.suggestions().firstOrNull { it.categoryId == categoryId }?.suggestedMinor
    val flex: StateFlow<com.emotionalleaves.sprout.data.budget.FlexState?> = _month.flatMapLatest { c.flexRepository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun today() = c.budgetRepository.today()
    fun setMode(m: Int) = viewModelScope.launch { c.settings.setBudgetMode(m) }
    fun setFlex(v: Long) = viewModelScope.launch { c.settings.setFlexBudget(v) }
    fun setFixed(ids: Set<Long>) = viewModelScope.launch { c.settings.setFixedCategories(ids) }

    fun prev() = _month.update { it.minusMonths(1) }
    fun next() = _month.update { it.plusMonths(1) }
    fun isCurrent() = _month.value == c.budgetRepository.currentMonth()

    fun save(categoryId: Long, amount: Long, thisMonthOnly: Boolean, rollover: Boolean, wasOn: Boolean) =
        viewModelScope.launch {
            c.budgetRepository.setBudget(categoryId, amount, thisMonthOnly, _month.value)
            if (rollover != wasOn) c.budgetRepository.setRollover(categoryId, rollover, _month.value)
        }

    fun remove(categoryId: Long, thisMonthOnly: Boolean) =
        viewModelScope.launch { c.budgetRepository.removeBudget(categoryId, thisMonthOnly, _month.value) }
}

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
fun BudgetsScreen(onQuickSetup: () -> Unit, onCategory: (Long) -> Unit, onGoals: () -> Unit) {
    val vm = sproutViewModel { BudgetsViewModel(it) }
    val month by vm.month.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryLine?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val mode by vm.mode.collectAsStateWithLifecycle()
    val flex by vm.flex.collectAsStateWithLifecycle()
    val today = vm.today()

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
        val m = data ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf("Per category", "Flex").forEachIndexed { i, label ->
                SegmentedButton(mode == i, { vm.setMode(i) }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        val isCurrent = java.time.YearMonth.from(today) == month
        if (mode == 1) {
            val fs = flex ?: return@Column
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    val sc = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
                    com.emotionalleaves.sprout.ui.common.HeroCard(sc.hero) {
                        val safe = fs.month.safeMinor
                        when {
                            fs.month.flexBudgetMinor <= 0 -> {
                                Text("Flex budgeting", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                                Text("One number for everything", style = MaterialTheme.typography.headlineSmall, color = sc.onHero)
                                Text("Set your flex amount below, and this shows what's safe to spend.",
                                    style = MaterialTheme.typography.bodyMedium, color = sc.onHero.copy(alpha = 0.85f))
                            }
                            safe < 0 -> {
                                Text("Over your flex amount by", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                                Text(com.emotionalleaves.sprout.ui.common.money(-safe), style = MaterialTheme.typography.displayMedium,
                                    color = androidx.compose.ui.graphics.Color(0xFFFFB4A9))
                                Text("It happens. Next month starts fresh.", style = MaterialTheme.typography.bodyMedium, color = sc.onHero.copy(alpha = 0.85f))
                            }
                            else -> {
                                Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                                Text(com.emotionalleaves.sprout.ui.common.animatedMoney(safe), style = MaterialTheme.typography.displayMedium, color = sc.onHero)
                                fs.month.perDayMinor?.let { Text("≈ ${com.emotionalleaves.sprout.ui.common.money(it)} a day", style = MaterialTheme.typography.titleMedium, color = sc.onHero) }
                            }
                        }
                    }
                }
                item { FlexSection(fs, today, isCurrent, vm::setFlex, vm::setFixed, suggest = vm::suggestedFlex) }
                item {
                    Text("Flex mode: no cap per category. Switch back to \"Per category\" any time; your category budgets are kept.",
                        Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { com.emotionalleaves.sprout.ui.home.SafeToSpendHero(m.takeIf { it.safeToSpend != null }, 0, onQuickSetup) }
            if (m.totalBudgetMinor > 0) item {
                val bills = m.safeToSpend?.upcomingUnbudgeted?.sumOf { it.amountMinor } ?: 0L
                com.emotionalleaves.sprout.ui.common.SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.emotionalleaves.sprout.ui.common.IconBadge(androidx.compose.material.icons.Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Your month costs ${Money.formatWhole(m.totalBudgetMinor + bills)}", style = MaterialTheme.typography.titleSmall)
                            Text("Budgets ${Money.formatWhole(m.totalBudgetMinor)}" + (if (bills > 0) " + bills outside budgets ${Money.formatWhole(bills)}" else "") +
                                " — what a normal month takes.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (bills > 0) {
                        // Budgets vs bills, as one split bar.
                        val f = m.totalBudgetMinor.toFloat() / (m.totalBudgetMinor + bills)
                        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                            androidx.compose.foundation.layout.Box(Modifier.weight(f.coerceAtLeast(0.02f)).fillMaxHeight()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
                            androidx.compose.foundation.layout.Box(Modifier.weight((1f - f).coerceAtLeast(0.02f)).fillMaxHeight()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary))
                        }
                    }
                }
            }
            item {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    SectionTitle("Budgets", Modifier.align(Alignment.CenterVertically), inRow = true)
                    Row {
                        TextButton(onClick = onGoals, modifier = Modifier.heightIn(min = 48.dp)) { Text("Goals") }
                        TextButton(onClick = onQuickSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quick setup") }
                    }
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
            if (m.budgeted.isNotEmpty()) item {
                com.emotionalleaves.sprout.ui.common.OneTimeTip("budgets-tap",
                    "Tap a budget to see where its money went. The pencil changes the amount, for every month or just this one.")
            }
            items(m.budgeted, key = { "b-" + it.category.id }) { line ->
                CategoryBudgetRow(line, onEdit = { editing = line }, pace = paceNote(line.spentMinor, line.budgetMinor, today, isCurrent)) { onCategory(line.category.id) }
            }
            if (m.unbudgetedWithSpending.isNotEmpty()) {
                item { SectionTitle("Not budgeted") }
                items(m.unbudgetedWithSpending, key = { "u-" + it.category.id }) { line ->
                    UnbudgetedRow(line.category, "Spent ${Money.format(line.spentMinor)} this month", onOpen = { onCategory(line.category.id) }) { editing = line }
                }
            }
            val idle = m.allLines.filter { it.budgetMinor == null && it.spentMinor == 0L }
            if (idle.isNotEmpty()) {
                item {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                        Text(if (showAll) "Hide categories without spending" else "Show all categories (${idle.size} more)")
                    }
                }
                if (showAll) {
                    items(idle, key = { "i-" + it.category.id }) { line ->
                        UnbudgetedRow(line.category, "Nothing spent this month", onOpen = { onCategory(line.category.id) }) { editing = line }
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
            initialMinor = line.baseBudgetMinor,
            hasBudget = line.budgetMinor != null,
            rolloverOn = line.rolloverOn,
            onDismiss = { editing = null },
            onSave = { amount, thisMonth, roll -> vm.save(line.category.id, amount, thisMonth, roll, line.rolloverOn); editing = null },
            onRemove = { thisMonth -> vm.remove(line.category.id, thisMonth); editing = null },
            suggest = { vm.suggestionFor(line.category.id) },
        )
    }
}

/** A category without a budget: its icon, what it cost this month, and a quiet "Set budget". */
@Composable
private fun UnbudgetedRow(category: com.emotionalleaves.sprout.domain.budget.BudgetCategory, note: String, onOpen: () -> Unit, onSet: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = "Open ${category.name}", onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.emotionalleaves.sprout.ui.common.IconBadge(
            com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(category.iconKey), androidx.compose.ui.graphics.Color(category.colorArgb), 38.dp,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onSet, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set budget") }
    }
}
