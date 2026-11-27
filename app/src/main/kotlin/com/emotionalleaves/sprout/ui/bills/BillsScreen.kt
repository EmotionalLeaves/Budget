package com.emotionalleaves.sprout.ui.bills

import androidx.compose.material3.SegmentedButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.emotionalleaves.sprout.ui.common.readable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
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
    val cancelIds = com.emotionalleaves.sprout.work.CancelReminders.observe(c.appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    fun setCancel(id: Long, on: Boolean) = viewModelScope.launch { com.emotionalleaves.sprout.work.CancelReminders.set(c.appContext, id, on) }

    /** Writes every active bill as a repeating calendar event and opens the share sheet ("Add to calendar"). */
    fun exportCalendar(context: android.content.Context) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val bills = all.value.filter { it.status != RecurringStatus.DISMISSED }.map {
            com.emotionalleaves.sprout.domain.calendar.BillIcs.Bill(it.id, it.displayName,
                Money.format(kotlin.math.abs(it.typicalAmountMinor)), Instant.ofEpochSecond(it.nextDueAt).atZone(zone).toLocalDate(), it.cadence)
        }
        if (bills.isEmpty()) return@launch
        val stamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "reports").apply { mkdirs() }
            java.io.File(dir, "sprout-bills.ics").apply { writeText(com.emotionalleaves.sprout.domain.calendar.BillIcs.build(bills, stamp)) }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/calendar")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = android.content.Intent.createChooser(send, "Add bills to a calendar")
            .putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
        runCatching { context.startActivity(chooser) }
    }

    private val _calMonth = kotlinx.coroutines.flow.MutableStateFlow(java.time.YearMonth.from(today()))
    val calMonth: StateFlow<java.time.YearMonth> = _calMonth
    fun shiftMonth(by: Long) { _calMonth.value = _calMonth.value.plusMonths(by) }
    @OptIn(ExperimentalCoroutinesApi::class)
    val paydays: StateFlow<List<com.emotionalleaves.sprout.domain.forecast.Expected>> =
        _calMonth.mapLatest { runCatching { c.forecastRepository.paydays(it) }.getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val cancelIds by vm.cancelIds.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        val list = upcoming
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bills", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (all.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { vm.exportCalendar(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Add to my calendar", maxLines = 1)
                        }
                    }
                }
            }
            item {
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    listOf("List" to false, "Calendar" to true).forEachIndexed { i, (label, cal) ->
                        SegmentedButton(calendarView == cal, { calendarView = cal },
                            androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
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
            if (list != null && list.isEmpty() && all.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.insights.SubscriptionsSummary() }
            }
            if (!list.isNullOrEmpty()) {
                item {
                    com.emotionalleaves.sprout.ui.common.HeroCard(LocalSproutColors.current.hero) {
                        val white = LocalSproutColors.current.onHero
                        Text("Bills in the next 30 days", style = MaterialTheme.typography.labelLarge, color = white.copy(alpha = 0.8f))
                        Text(com.emotionalleaves.sprout.ui.common.animatedMoney(list.sumOf { it.charge.typicalAmountMinor }),
                            style = MaterialTheme.typography.displaySmall, color = white)
                        val remaining = thisMonth.filter { !it.matchedThisMonth }.sumOf { it.amountMinor }
                        Text(
                            if (remaining > 0) "${com.emotionalleaves.sprout.ui.common.money(remaining)} still due this month" else "Nothing else due this month",
                            style = MaterialTheme.typography.bodyMedium, color = white.copy(alpha = 0.85f),
                        )
                        BillTimeline(list, vm.today(), white)
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.emotionalleaves.sprout.ui.common.HeroPill("${list.size} bill${if (list.size == 1) "" else "s"}", white, Icons.Filled.Event)
                            list.firstOrNull()?.let { com.emotionalleaves.sprout.ui.common.HeroPill("Next: ${it.charge.displayName} · ${dueText(it.dueAt).substringAfter(", ")}", white.copy(alpha = 0.9f)) }
                        }
                    }
                }
                item { com.emotionalleaves.sprout.ui.insights.SubscriptionsSummary() }
                item { SectionTitle("Upcoming") }
                items(list, key = { "u-${it.charge.id}-${it.dueAt}" }) { u ->
                    BillRow(u.charge, dueText(u.dueAt) + if (u.charge.id in cancelIds) " · cancel reminder on" else "") { editing = u.charge }
                }
            }
            val paid = thisMonth.filter { it.matchedThisMonth }
            if (paid.isNotEmpty()) {
                item { SectionTitle("Paid this month") }
                item {
                    com.emotionalleaves.sprout.ui.common.SoftCard {
                        paid.forEach { p ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Paid", tint = LocalSproutColors.current.ok)
                                Text("${p.name} · Paid ✓", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                                Text(Money.format(p.amountMinor), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
            cancelReminder = r.id in cancelIds,
            onCancelReminder = { vm.setCancel(r.id, it) },
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
    val pays by vm.paydays.collectAsStateWithLifecycle()
    val payByDay = pays.groupBy { it.date }
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
        Column(Modifier.padding(horizontal = 4.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) {
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
                            val isToday = day == today
                            val isSel = day == selected
                            Box(
                                Modifier.size(32.dp).background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primary
                                        isSel -> MaterialTheme.colorScheme.primaryContainer
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        isSel -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            if (dayBills.isNotEmpty()) {
                                val c = if (dayBills.all { it.paid }) LocalSproutColors.current.ok else MaterialTheme.colorScheme.tertiary
                                Text(Money.formatWhole(total), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                        .background(c.copy(alpha = 0.14f)).padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                            if (payByDay[day] != null) {
                                Text("+" + Money.formatWhole(payByDay.getValue(day).sumOf { it.amountMinor }), style = MaterialTheme.typography.labelSmall,
                                    color = LocalSproutColors.current.income)
                            }
                        }
                    }
                }
            }
        }
        }
        val shown = selected?.let { byDay[it].orEmpty() } ?: bills
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                selected?.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)) ?: "All bills this month",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            )
            val paid = shown.count { it.paid }
            Text("$paid paid · ${shown.size - paid} due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (shown.isEmpty()) Text("No bills.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (shown.isNotEmpty()) Column(Modifier.padding(horizontal = 4.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 4.dp)) {
        shown.forEach { b ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (b.paid) Icons.Filled.CheckCircle else Icons.Filled.Schedule, contentDescription = if (b.paid) "Paid" else "Due",
                    tint = if (b.paid) LocalSproutColors.current.ok else MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(b.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text((if (b.paid) "Paid · " else "Due ") + b.date.format(dueFormat), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(b.amountMinor), color = if (b.paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        }
        Text("Total this month: ${Money.format(bills.sumOf { it.amountMinor })}", Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge)
        if (pays.isNotEmpty()) {
            Text("Expected paydays", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
            pays.forEach { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.name} · ${p.date.format(dueFormat)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("+" + Money.format(p.amountMinor), color = LocalSproutColors.current.income)
                }
            }
        }
    }
}

@Composable
private fun BillRow(r: RecurringRow, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = com.emotionalleaves.sprout.ui.common.cardBorder(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateBadge(r.nextDueAt, Color(r.categoryColor))
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.CenterVertically))
                    if (r.status == RecurringStatus.DETECTED) {
                        Text("Suggested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CategoryChip(r.categoryName, r.categoryColor)
            }
            Text(Money.format(r.typicalAmountMinor), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A calendar-page badge: month over day number, tinted with the bill's category color. */
@Composable
fun DateBadge(epoch: Long, tint: Color = MaterialTheme.colorScheme.primary) {
    val due = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
    val t = if (tint.alpha == 0f) MaterialTheme.colorScheme.primary else tint.readable()
    Column(
        Modifier.widthIn(min = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(t.copy(alpha = 0.20f), t.copy(alpha = 0.08f))))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(due.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = t, maxLines = 1)
        Text(due.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

/**
 * The next 30 days as a line, with a dot for each bill: bigger dots cost more. Decorative; the list below has the details.
 */
@Composable
private fun BillTimeline(bills: List<UpcomingBill>, today: LocalDate, color: Color) {
    val zone = ZoneId.systemDefault()
    val days = bills.map { java.time.temporal.ChronoUnit.DAYS.between(today, Instant.ofEpochSecond(it.dueAt).atZone(zone).toLocalDate()).coerceIn(0, 30) }
    val max = bills.maxOfOrNull { kotlin.math.abs(it.charge.typicalAmountMinor) }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp).clearAndSetSemantics { }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            drawLine(color.copy(alpha = 0.25f), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            // Week ticks.
            for (k in 0..4) {
                val x = pad + w * (k * 7f / 30f)
                drawLine(color.copy(alpha = 0.3f), Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            bills.forEachIndexed { i, b ->
                val x = pad + w * days[i] / 30f
                val r = (3.5f + 6.5f * kotlin.math.sqrt(kotlin.math.abs(b.charge.typicalAmountMinor).toFloat() / max)).dp.toPx()
                drawCircle(color.copy(alpha = 0.18f), r + 3.dp.toPx(), Offset(x, y))
                drawCircle(color, r, Offset(x, y))
            }
            drawCircle(color, 4.dp.toPx(), Offset(pad, y), style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
            Text("30 days", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
        }
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
    cancelReminder: Boolean? = null,
    onCancelReminder: (Boolean) -> Unit = {},
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
                    OutlinedTextField(value = name ?: "", onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.take(10) }, label = { Text("Amount") }, prefix = { Text("$") },
                    singleLine = true, isError = amountText.isNotBlank() && amount == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
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
                    if (cancelReminder != null) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onCancelReminder(!cancelReminder) }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Remind me to cancel", style = MaterialTheme.typography.bodyLarge)
                                Text("A notification 3 days before it renews. Sprout can't cancel for you — it reminds you to do it.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Switch(checked = cancelReminder, onCheckedChange = null)
                        }
                    }
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
