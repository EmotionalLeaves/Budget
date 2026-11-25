package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.TransactionDetail
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.domain.split.SplitPart
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.abs
import com.emotionalleaves.sprout.ui.categories.CategoryGrid
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionDetailViewModel(val c: AppContainer, private val id: String) : ViewModel() {
    val detail: StateFlow<TransactionDetail?> =
        c.db.transactions().observeDetail(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<CategoryEntity>> =
        c.db.categories().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<Pair<String, (suspend () -> Unit)?>>(extraBufferCapacity = 1)
    val messages: SharedFlow<Pair<String, (suspend () -> Unit)?>> = _messages

    fun pick(category: CategoryEntity, always: Boolean, merchant: String) = viewModelScope.launch {
        val change = c.categoryRepository.recategorizeWithUndo(id, category.id, always)
        c.alertRepository.evaluateBudgets()
        val undo: suspend () -> Unit = {
            change.undo()
            c.alertRepository.evaluateBudgets()
        }
        val msg = if (always && change.others > 0) "Moved ${change.others + 1} from $merchant to ${category.name}" else "Moved to ${category.name}"
        _messages.tryEmit(msg to undo)
    }

    val splits: StateFlow<List<com.emotionalleaves.sprout.data.db.TransactionSplitEntity>> =
        c.db.splits().observeFor(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves the split; returns a problem to show, or null when saved. */
    suspend fun saveSplits(parts: List<SplitPart>): String? {
        val problem = c.categoryRepository.setSplits(id, parts)
        if (problem == null) c.alertRepository.evaluateBudgets()
        return problem
    }

    fun saveNote(note: String) = viewModelScope.launch { c.categoryRepository.setNote(id, note) }

    /** "Part of a bill" text, when this charge belongs to a recurring charge. */
    suspend fun billText(recurringId: Long?): String? = recurringId?.let { rid ->
        c.db.recurring().getById(rid)?.takeIf { it.status != com.emotionalleaves.sprout.data.db.RecurringStatus.DISMISSED }
            ?.let { "Part of a bill: ${it.displayName} (${com.emotionalleaves.sprout.ui.bills.cadenceLabel(it.cadence).lowercase()}) — see the Bills tab" }
    }
    fun setExcluded(excluded: Boolean) = viewModelScope.launch {
        c.categoryRepository.setExcluded(id, excluded)
        c.alertRepository.evaluateBudgets()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(transactionId: String, onDismiss: () -> Unit, onMessage: (String, (suspend () -> Unit)?) -> Unit) {
    val vm = sproutViewModel(key = "tx-$transactionId") { TransactionDetailViewModel(it, transactionId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val splits by vm.splits.collectAsStateWithLifecycle()
    var splitting by remember { mutableStateOf(false) }
    var splitProblem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(vm) { vm.messages.collect { (msg, undo) -> onMessage(msg, undo) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val d = detail ?: return@ModalBottomSheet
        val merchant = com.emotionalleaves.sprout.ui.common.merchantName(d.merchantKey)
        var always by rememberSaveable(transactionId) { mutableStateOf(true) }
        var note by remember(d.id) { mutableStateOf(d.note.orEmpty()) }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val cat = categories.firstOrNull { it.id == d.categoryId }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.emotionalleaves.sprout.ui.common.IconBadge(
                    com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(cat?.iconKey),
                    cat?.let { androidx.compose.ui.graphics.Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary, 64.dp,
                )
                Text(merchant, style = MaterialTheme.typography.titleLarge)
                Text(
                    Format.amount(d.amountMinor),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (d.amountMinor > 0) LocalSproutColors.current.income else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        append(if (d.pending) "Pending · " else "")
                        append(Format.dateTime(d.occurredAt))
                        if (d.transactedAt != null && !d.pending && d.transactedAt != d.postedAt) {
                            append(" · made ").append(Format.dateTime(d.transactedAt))
                        }
                        append(" · ").append(d.accountName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                // The bank's own wording, in case the tidied-up name hides something.
                if (!d.description.equals(merchant, ignoreCase = true)) {
                    Text("Bank says: " + d.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
                TextButton(
                    onClick = { onDismiss(); navigate("merchant/" + android.net.Uri.encode(d.merchantKey)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Everything at $merchant ›") }
            }

            var bill by remember(d.recurringChargeId) { mutableStateOf<String?>(null) }
            LaunchedEffect(d.recurringChargeId) { bill = vm.billText(d.recurringChargeId) }
            bill?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            // The category as one calm row; the full grid only when you ask to change it (or it needs one).
            val current = categories.firstOrNull { it.id == d.categoryId }
            var changing by remember(d.id) { mutableStateOf(current?.key == com.emotionalleaves.sprout.domain.categorize.DefaultCategories.UNCATEGORIZED) }
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.clickable { changing = !changing }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.emotionalleaves.sprout.ui.common.IconBadge(
                        com.emotionalleaves.sprout.ui.common.CategoryIcons.forKey(current?.iconKey),
                        androidx.compose.ui.graphics.Color(current?.colorArgb ?: 0xFF888888.toInt()), 40.dp,
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(d.categoryName, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { changing = !changing }) { Text(if (changing) "Done" else "Change") }
                }
            }
            androidx.compose.animation.AnimatedVisibility(changing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchRow("Always use this for $merchant", always) { always = it }
                    CategoryGrid(categories, d.categoryId) { vm.pick(it, always, merchant); changing = false }
                }
            }

            val names = categories.associate { it.id to it.name }
            if (splits.isNotEmpty()) {
                Text("Split", style = MaterialTheme.typography.titleMedium)
                splits.forEach { p ->
                    Text("${names[p.categoryId] ?: "Category"}: ${Money.format(abs(p.amountMinor))}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${d.categoryName}: ${Money.format(abs(d.amountMinor - splits.sumOf { it.amountMinor }))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (d.pending) {
                Text("You can split this once it posts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (d.amountMinor != 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { splitProblem = null; splitting = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (splits.isEmpty()) "Split across categories" else "Edit split")
                    }
                    if (splits.isNotEmpty()) {
                        OutlinedButton(onClick = { scope.launch { vm.saveSplits(emptyList()) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Remove split")
                        }
                    }
                }
            }
            if (splitting) {
                SplitDialog(
                    amountMinor = d.amountMinor,
                    ownCategory = categories.firstOrNull { it.id == d.categoryId },
                    categories = categories,
                    existing = splits.map { SplitPart(it.categoryId, it.amountMinor) },
                    onDismiss = { splitting = false },
                    onSave = { parts -> scope.launch { splitProblem = vm.saveSplits(parts); if (splitProblem == null) splitting = false } },
                    problem = splitProblem,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
            LaunchedEffect(note) {
                if (note != d.note.orEmpty()) {
                    kotlinx.coroutines.delay(600)
                    vm.saveNote(note)
                }
            }
            com.emotionalleaves.sprout.ui.tags.TransactionTags(vm.c, d.id)
            TransactionReceipts(vm.c, d.id)
            SwitchRow("Exclude from budget", d.excludedFromBudget) { vm.setExcluded(it) }
            if (com.emotionalleaves.sprout.data.manual.ManualRepository.isManual(d.id)) {
                var editingManual by remember { mutableStateOf(false) }
                TextButton(onClick = { editingManual = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit amount, name or date") }
                if (editingManual) {
                    var amt by remember { mutableStateOf(Money.format(abs(d.amountMinor)).removePrefix("$").replace(",", "")) }
                    var name by remember { mutableStateOf(d.description) }
                    var dateText by remember { mutableStateOf(Format.localDate(d.occurredAt).toString()) }
                    val parsed = Money.parseUserInput(amt)?.takeIf { it > 0 }
                    val date = runCatching { java.time.LocalDate.parse(dateText) }.getOrNull()
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { editingManual = false },
                        title = { Text("Edit transaction") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(amt, { amt = it.take(12) }, label = { Text("Amount") }, prefix = { Text("$") }, singleLine = true, isError = parsed == null, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(name, { name = it.take(120) }, label = { Text("What was it") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(dateText, { dateText = it.take(10) }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = date == null, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = parsed != null && date != null, onClick = {
                                val sign = if (d.amountMinor < 0) -1 else 1
                                scope.launch {
                                    vm.c.manualRepository.update(d.id, sign * (parsed ?: 0), name, date ?: return@launch)
                                    vm.c.alertRepository.evaluateBudgets()
                                }
                                editingManual = false
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { editingManual = false }) { Text("Cancel") } },
                    )
                }
                TextButton(onClick = { scope.launch { vm.c.manualRepository.delete(d.id); vm.c.alertRepository.evaluateBudgets(); onDismiss() } },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete this transaction", color = MaterialTheme.colorScheme.error) }
            }
            Text(" ", Modifier.heightIn(min = 16.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
