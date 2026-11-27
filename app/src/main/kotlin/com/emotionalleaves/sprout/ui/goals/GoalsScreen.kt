package com.emotionalleaves.sprout.ui.goals

import com.emotionalleaves.sprout.ui.common.showUndoable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.goals.GoalView
import com.emotionalleaves.sprout.domain.budget.ProgressLevel
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.budgets.BudgetProgress
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class GoalsViewModel(private val c: AppContainer) : ViewModel() {
    val goals: StateFlow<List<GoalView>?> = c.goalRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val accounts: StateFlow<List<AccountEntity>> = c.db.accounts().observeVisible().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, target: Long, date: LocalDate?, accountId: String?, saved: Long, kind: String) =
        viewModelScope.launch { c.goalRepository.save(id, name, target, date, accountId, saved, kind) }
    fun addMoney(id: Long, amount: Long) = viewModelScope.launch { c.goalRepository.addMoney(id, amount) }
    suspend fun delete(id: Long): suspend () -> Unit = c.goalRepository.delete(id)
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

@Composable
// onClick comes before footer on purpose: a plain tap action passed positionally must never land in the composable slot.
fun GoalProgressRow(v: GoalView, onClick: (() -> Unit)? = null, footer: (@Composable () -> Unit)? = null) {
    val p = v.progress
    val desc = "${v.goal.name}: ${Money.format(p.savedMinor)} of ${Money.format(p.targetMinor)}, ${p.percent} percent"
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = com.emotionalleaves.sprout.ui.common.cardBorder(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) { Column {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp).semantics(mergeDescendants = true) { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ringColor = if (p.reached) com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current.ok else MaterialTheme.colorScheme.primary
            com.emotionalleaves.sprout.ui.common.ProgressRing(
                p.percent / 100f, ringColor, MaterialTheme.colorScheme.surfaceVariant,
                Modifier.size(56.dp), stroke = 7.dp,
            ) {
                if (p.reached) Icon(Icons.Filled.Check, contentDescription = null, tint = ringColor, modifier = Modifier.size(24.dp))
                else Text("${p.percent}%", style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.goal.name, style = MaterialTheme.typography.titleSmall)
                Text(if (p.reached) "Reached!" else "${Money.format(p.remainingMinor)} to go", style = MaterialTheme.typography.labelMedium,
                    color = if (p.reached) com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current.ok else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Past the target, "$6,420 of $1,200" reads like a mistake; say what actually happened instead.
        val acct = v.account
        val detail = when {
            p.reached && p.savedMinor > p.targetMinor && acct != null ->
                "${Money.format(p.targetMinor)} target · ${acct.label} holds ${Money.format(p.savedMinor)}"
            p.reached && p.savedMinor > p.targetMinor ->
                "${Money.format(p.savedMinor)} saved · ${Money.format(p.savedMinor - p.targetMinor)} past the ${Money.format(p.targetMinor)} target"
            else -> "${Money.format(p.savedMinor)} of ${Money.format(p.targetMinor)}" + (acct?.let { " · from ${it.label}" } ?: "")
        }
        Text(detail,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            p.overdue -> Text("Target date passed — ${Money.format(p.remainingMinor)} to go", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
            p.perMonthMinor != null -> Text("Save ${Money.format(p.perMonthMinor ?: 0)}/month to reach it by " +
                (v.goal.targetDate?.let { LocalDate.parse(it).format(dateFmt) } ?: ""), style = MaterialTheme.typography.labelSmall)
        }
    }
    footer?.let { Box(Modifier.padding(start = 8.dp, bottom = 6.dp)) { it() } }
    } }
}

/** All goals at a glance: what's saved so far, and one ring for how far along they are together. */
@Composable
private fun GoalsHero(list: List<GoalView>) {
    val sc = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
    val target = list.sumOf { it.progress.targetMinor }
    // A goal saved past its target counts as done, not as extra progress on the others.
    val toward = list.sumOf { minOf(it.progress.savedMinor, it.progress.targetMinor).coerceAtLeast(0) }
    val overall = if (target > 0) toward.toFloat() / target else 0f
    val reached = list.count { it.progress.reached }
    com.emotionalleaves.sprout.ui.common.HeroCard(sc.hero) {
        com.emotionalleaves.sprout.ui.home.HeroRow(ring = { ringSize ->
            com.emotionalleaves.sprout.ui.common.ProgressRing(
                overall, sc.onHero, sc.onHero.copy(alpha = 0.18f), Modifier.size(ringSize), stroke = 11.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(overall * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = sc.onHero)
                    Text("overall", style = MaterialTheme.typography.labelSmall, color = sc.onHero.copy(alpha = 0.8f))
                }
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Saved toward goals", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                Text(com.emotionalleaves.sprout.ui.common.animatedMoney(toward, whole = true), style = MaterialTheme.typography.displaySmall,
                    color = sc.onHero, maxLines = 1)
                Text("of ${com.emotionalleaves.sprout.ui.common.money(target, whole = true)} in targets", style = MaterialTheme.typography.bodyMedium,
                    color = sc.onHero.copy(alpha = 0.85f))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.emotionalleaves.sprout.ui.common.HeroPill("${list.size} goal${if (list.size == 1) "" else "s"}", sc.onHero)
                    if (reached > 0) com.emotionalleaves.sprout.ui.common.HeroPill("$reached reached", sc.onHero, Icons.Filled.Check)
                }
            }
        }
    }
}

@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = sproutViewModel { GoalsViewModel(it) }
    val goals by vm.goals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalView?>(null) }
    var adding by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<GoalView?>(null) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Savings goals", style = MaterialTheme.typography.titleLarge)
                }
            }
            val list = goals
            if (!list.isNullOrEmpty()) item { GoalsHero(list) }
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState("No goals yet", "Saving for something? Add a goal and Sprout shows how much to put aside each month.",
                        icon = androidx.compose.material.icons.Icons.Rounded.Savings) {
                        androidx.compose.material3.Button(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add your first goal") }
                    }
                }
            }
            items(list.orEmpty(), key = { it.goal.id }) { v ->
                GoalProgressRow(
                    v,
                    onClick = { editing = v },
                    footer = if (v.account == null && !v.progress.reached) ({
                        TextButton(onClick = { adjusting = v }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add money") }
                    }) else null,
                )
            }
        }
        // The empty state has its own button; one "add" on screen is enough.
        if (!goals.isNullOrEmpty()) ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Add goal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (adding || editing != null) {
        val g = editing?.goal
        GoalDialog(
            initialName = g?.name ?: "",
            initialTarget = g?.targetMinor,
            initialDate = g?.targetDate?.let(LocalDate::parse),
            initialAccount = g?.accountId,
            initialSaved = g?.savedMinor ?: 0,
            initialKind = g?.kind ?: com.emotionalleaves.sprout.data.goals.GoalRepository.KIND_SAVINGS,
            accounts = accounts,
            onDismiss = { adding = false; editing = null },
            onSave = { name, target, date, account, saved, kind -> vm.save(g?.id, name, target, date, account, saved, kind); adding = false; editing = null },
            onDelete = g?.let { goal -> {
                editing = null
                scope.launch {
                    val undo = vm.delete(goal.id)
                    scope.showUndoable(snackbar, "Deleted \u201C${goal.name}\u201D", undo)
                }
            } },
        )
    }
    adjusting?.let { v ->
        var text by remember { mutableStateOf("") }
        val amount = Money.parseUserInput(text.removePrefix("-"))?.let { if (text.startsWith("-")) -it else it }
        AlertDialog(
            onDismissRequest = { adjusting = null },
            title = { Text("Add money to ${v.goal.name}") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(10) }, prefix = { Text("$") }, singleLine = true,
                    label = { Text("Amount (use - to take out)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { amount?.let { vm.addMoney(v.goal.id, it) }; adjusting = null }, enabled = amount != null) { Text("Save") } },
            dismissButton = { TextButton(onClick = { adjusting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(
    initialName: String, initialTarget: Long?, initialDate: LocalDate?, initialAccount: String?, initialSaved: Long,
    initialKind: String,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, LocalDate?, String?, Long, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initialName) }
    var targetText by remember { mutableStateOf(initialTarget?.let { (it / 100).toString() } ?: "") }
    var date by remember { mutableStateOf(initialDate) }
    var account by remember { mutableStateOf(initialAccount) }
    var savedText by remember { mutableStateOf(if (initialSaved > 0) (initialSaved / 100).toString() else "") }
    var picking by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(initialKind) }
    val planned = kind == com.emotionalleaves.sprout.data.goals.GoalRepository.KIND_PLANNED
    val target = Money.parseUserInput(targetText)?.takeIf { it > 0 }
    val saved = if (savedText.isBlank()) 0L else Money.parseUserInput(savedText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(com.emotionalleaves.sprout.data.goals.GoalRepository.KIND_SAVINGS to "Savings goal",
                        com.emotionalleaves.sprout.data.goals.GoalRepository.KIND_PLANNED to "Planned expense").forEachIndexed { i, (k, label) ->
                        SegmentedButton(kind == k, { kind = k }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
                if (planned) {
                    Text("For a known future cost (car registration, holidays, insurance). Sprout sets aside a little each month " +
                        "and keeps it out of safe to spend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetText, onValueChange = { targetText = it.take(10) }, label = { Text("Target amount") },
                    prefix = { Text("$") }, singleLine = true, isError = targetText.isNotBlank() && target == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(date?.let { "By ${it.format(dateFmt)}" } ?: if (planned) "When is it due?" else "Add a target date (optional)")
                    }
                    if (date != null) TextButton(onClick = { date = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear") }
                }
                Text("Track progress with", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(account == null, role = Role.RadioButton) { account = null },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = account == null, onClick = null)
                    Text("Amounts I add myself", Modifier.padding(start = 8.dp))
                }
                accounts.forEach { a ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(account == a.id, role = Role.RadioButton) { account = a.id },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = account == a.id, onClick = null)
                        Text("${a.label}'s balance", Modifier.padding(start = 8.dp))
                    }
                }
                if (account == null) {
                    OutlinedTextField(value = savedText, onValueChange = { savedText = it.take(10) }, label = { Text("Saved so far") },
                        prefix = { Text("$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete goal") }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (target != null && saved != null) onSave(name, target, date, account, saved, kind) },
                enabled = name.isNotBlank() && target != null && saved != null && (!planned || date != null)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now().plusMonths(6)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
