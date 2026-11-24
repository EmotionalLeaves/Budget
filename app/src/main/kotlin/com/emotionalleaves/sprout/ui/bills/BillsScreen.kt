package com.emotionalleaves.sprout.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.RecurringRow
import com.emotionalleaves.sprout.data.db.RecurringStatus
import com.emotionalleaves.sprout.data.recurring.UpcomingBill
import com.emotionalleaves.sprout.domain.budget.ExpectedCharge
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.domain.recurring.Cadence
import com.emotionalleaves.sprout.ui.common.CategoryChip
import com.emotionalleaves.sprout.ui.categories.CategoryGrid
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.SectionTitle
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class BillsViewModel(private val c: AppContainer) : ViewModel() {
    val upcoming: StateFlow<List<UpcomingBill>?> = c.recurringRepository.observeUpcoming(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val all: StateFlow<List<RecurringRow>> = c.recurringRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val thisMonth: StateFlow<List<ExpectedCharge>> = c.recurringRepository.expectedCharges(c.budgetRepository.currentMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = c.db.categories().observeActive()
        .map { l -> l.filter { it.kind == CategoryKind.EXPENSE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirm(id: Long) = viewModelScope.launch { c.recurringRepository.confirm(id) }
    fun dismiss(id: Long) = viewModelScope.launch { c.recurringRepository.dismiss(id) }
    fun edit(id: Long, amount: Long, cadence: Cadence, next: LocalDate, category: Long) =
        viewModelScope.launch { c.recurringRepository.edit(id, amount, cadence, next, category) }
    fun add(name: String, amount: Long, cadence: Cadence, next: LocalDate, category: Long) =
        viewModelScope.launch { c.recurringRepository.addManual(name, amount, cadence, next, category) }
    fun today(): LocalDate = LocalDate.now(c.clock.withZone(ZoneId.systemDefault()))

    private val _calMonth = kotlinx.coroutines.flow.MutableStateFlow(java.time.YearMonth.from(today()))
    val calMonth: StateFlow<java.time.YearMonth> = _calMonth
    fun shiftMonth(by: Long) { _calMonth.value = _calMonth.value.plusMonths(by) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val calendar: StateFlow<List<com.emotionalleaves.sprout.data.recurring.CalendarBill>> =
        _calMonth.flatMapLatest { c.recurringRepository.observeCalendar(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val dueFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
fun dueText(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate().format(dueFormat)
fun cadenceLabel(c: Cadence) = when (c) {
    Cadence.WEEKLY -> "Weekly"
    Cadence.BIWEEKLY -> "Every 2 weeks"
    Cadence.MONTHLY -> "Monthly"
    Cadence.QUARTERLY -> "Every 3 months"
    Cadence.YEARLY -> "Yearly"
}

@Composable
fun BillsScreen() {
    val vm = sproutViewModel { BillsViewModel(it) }
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    val thisMonth by vm.thisMonth.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RecurringRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    var calendarView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val list = upcoming
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(!calendarView, { calendarView = false }, label = { Text("List") },
                        modifier = Modifier.heightIn(min = 48.dp))
                    androidx.compose.material3.FilterChip(calendarView, { calendarView = true }, label = { Text("Calendar") },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (calendarView) {
                item { BillCalendar(vm) }
                return@LazyColumn
            }
            if (list != null && list.isEmpty() && all.isEmpty()) {
                item {
                    EmptyState(
                        "No bills found yet",
                        "Sprout spots subscriptions and bills after a few months of history. You can also add one with the + button.",
                    )
                }
            }
            if (!list.isNullOrEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Next 30 days", style = MaterialTheme.typography.labelLarge)
                            Text(Money.format(list.sumOf { it.charge.typicalAmountMinor }), style = MaterialTheme.typography.headlineMedium)
                            val remaining = thisMonth.filter { !it.matchedThisMonth }.sumOf { it.amountMinor }
                            Text("Still due this month: ${Money.format(remaining)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { SectionTitle("Upcoming (next 30 days)") }
                items(list, key = { "u-${it.charge.id}-${it.dueAt}" }) { u ->
                    BillRow(u.charge, dueText(u.dueAt)) { editing = u.charge }
                }
            }
            val paid = thisMonth.filter { it.matchedThisMonth }
            if (paid.isNotEmpty()) {
                item { SectionTitle("Paid this month") }
                items(paid, key = { "p-${it.name}-${it.dueAt}" }) { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Paid", tint = LocalSproutColors.current.ok)
                        Text("${p.name} · Paid ✓", Modifier.weight(1f).padding(start = 8.dp))
                        Text(Money.format(p.amountMinor))
                    }
                }
            }
            if (all.isNotEmpty()) {
                item { SectionTitle("All recurring") }
                items(all, key = { "a-" + it.id }) { r ->
                    Column(Modifier.fillMaxWidth()) {
                        BillRow(r, "${cadenceLabel(r.cadence)} · next ${dueText(r.nextDueAt)}") { editing = r }
                        if (r.status == RecurringStatus.DETECTED) {
                            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.confirm(r.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Confirm") }
                                TextButton(onClick = { vm.dismiss(r.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add bill") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { r ->
        BillDialog(
            title = "Edit ${r.displayName}",
            initialName = null,
            initialAmount = r.typicalAmountMinor,
            initialCadence = r.cadence,
            initialNext = Instant.ofEpochSecond(r.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            initialCategory = r.categoryId,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { _, amount, cadence, next, cat -> vm.edit(r.id, amount, cadence, next, cat); editing = null },
            onRemove = { vm.dismiss(r.id); editing = null },
        )
    }
    if (adding) {
        BillDialog(
            title = "Add a bill",
            initialName = "",
            initialAmount = null,
            initialCadence = Cadence.MONTHLY,
            initialNext = vm.today().plusDays(1),
            initialCategory = categories.firstOrNull { it.key == "subscriptions" }?.id ?: categories.firstOrNull()?.id,
            categories = categories,
            onDismiss = { adding = false },
            onSave = { name, amount, cadence, next, cat -> vm.add(name ?: "Bill", amount, cadence, next, cat); adding = false },
            onRemove = null,
        )
    }
}

@Composable
private fun BillCalendar(vm: BillsViewModel) {
    val month by vm.calMonth.collectAsStateWithLifecycle()
    val bills by vm.calendar.collectAsStateWithLifecycle()
    var selected by remember(month) { mutableStateOf<LocalDate?>(null) }
    val byDay = bills.groupBy { it.date }
    val today = vm.today()
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftMonth(-1) }) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { vm.shiftMonth(1) }) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        com.emotionalleaves.sprout.domain.recurring.CalendarGrid.cells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val dayBills = day?.let { byDay[it] }.orEmpty()
                    val total = dayBills.sumOf { it.amountMinor }
                    val label = day?.let { d ->
                        "${d.format(DateTimeFormatter.ofPattern("MMMM d", Locale.US))}" +
                            if (dayBills.isEmpty()) ", no bills" else ", ${dayBills.size} bill${if (dayBills.size == 1) "" else "s"}, ${Money.format(total)}"
                    } ?: ""
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).padding(2.dp)
                            .then(if (day != null) Modifier.clickable { selected = day } else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (day != null) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (dayBills.isNotEmpty()) {
                                Text(Money.formatWhole(total), style = MaterialTheme.typography.labelSmall,
                                    color = if (dayBills.all { it.paid }) LocalSproutColors.current.ok else MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
        val shown = selected?.let { byDay[it].orEmpty() } ?: bills
        Text(
            selected?.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)) ?: "All bills this month",
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
        if (shown.isEmpty()) Text("No bills.", Modifier.padding(8.dp))
        shown.forEach { b ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name + if (b.paid) " · Paid ✓" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(b.date.format(dueFormat), style = MaterialTheme.typography.labelSmall)
                }
                Text(Money.format(b.amountMinor))
            }
        }
        Text("Total this month: ${Money.format(bills.sumOf { it.amountMinor })}", Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BillRow(r: RecurringRow, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.displayName, style = MaterialTheme.typography.bodyLarge)
                if (r.status == RecurringStatus.DETECTED) {
                    Text("Suggested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CategoryChip(r.categoryName, r.categoryColor)
        }
        Text(Money.format(r.typicalAmountMinor), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillDialog(
    title: String,
    initialName: String?,
    initialAmount: Long?,
    initialCadence: Cadence,
    initialNext: LocalDate,
    initialCategory: Long?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String?, amount: Long, cadence: Cadence, next: LocalDate, categoryId: Long) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember { mutableStateOf(initialAmount?.let { Money.format(it).removePrefix("$").replace(",", "") } ?: "") }
    var cadence by remember { mutableStateOf(initialCadence) }
    var next by remember { mutableStateOf(initialNext) }
    var category by remember { mutableStateOf(initialCategory) }
    var picking by remember { mutableStateOf(false) }
    val amount = Money.parseUserInput(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (name != null) {
                    OutlinedTextField(value = name ?: "", onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.take(10) }, label = { Text("Amount") }, prefix = { Text("$") },
                    singleLine = true, isError = amountText.isNotBlank() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("How often", style = MaterialTheme.typography.labelLarge)
                Cadence.entries.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(cadence == c, role = Role.RadioButton) { cadence = c },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = cadence == c, onClick = null)
                        Text(cadenceLabel(c), Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Next date: ${next.format(dueFormat)}")
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                CategoryGrid(categories, category) { category = it.id }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not a bill — dismiss") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { val cat = category; if (amount != null && cat != null) onSave(name, amount, cadence, next, cat) },
                enabled = amount != null && category != null && (name == null || name!!.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = next.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { next = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
