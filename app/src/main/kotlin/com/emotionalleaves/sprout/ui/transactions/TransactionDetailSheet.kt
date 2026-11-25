package com.emotionalleaves.sprout.ui.transactions

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
        val merchant = MerchantNormalizer.displayName(d.merchantKey)
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
            }
            Text(d.description, style = MaterialTheme.typography.bodyMedium)
            val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
            OutlinedButton(
                onClick = { onDismiss(); navigate("merchant/" + android.net.Uri.encode(d.merchantKey)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Everything at $merchant") }
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
            )

            var bill by remember(d.recurringChargeId) { mutableStateOf<String?>(null) }
            LaunchedEffect(d.recurringChargeId) { bill = vm.billText(d.recurringChargeId) }
            bill?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Text("Category: ${d.categoryName}", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Always use this for $merchant", always) { always = it }
            CategoryGrid(categories, d.categoryId) { vm.pick(it, always, merchant) }

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
