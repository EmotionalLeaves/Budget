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

class TransactionDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val detail: StateFlow<TransactionDetail?> =
        c.db.transactions().observeDetail(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<CategoryEntity>> =
        c.db.categories().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun pick(category: CategoryEntity, always: Boolean, merchant: String) = viewModelScope.launch {
        val others = c.categoryRepository.recategorize(id, category.id, always)
        c.alertRepository.evaluateBudgets()
        if (always && others > 0) _messages.tryEmit("Moved $others more from $merchant to ${category.name}")
    }

    fun saveNote(note: String) = viewModelScope.launch { c.categoryRepository.setNote(id, note) }
    fun setExcluded(excluded: Boolean) = viewModelScope.launch {
        c.categoryRepository.setExcluded(id, excluded)
        c.alertRepository.evaluateBudgets()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(transactionId: String, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val vm = sproutViewModel(key = "tx-$transactionId") { TransactionDetailViewModel(it, transactionId) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(vm) { vm.messages.collect(onMessage) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        val d = detail ?: return@ModalBottomSheet
        val merchant = MerchantNormalizer.displayName(d.merchantKey)
        var always by rememberSaveable(transactionId) { mutableStateOf(true) }
        var note by remember(d.id) { mutableStateOf(d.note.orEmpty()) }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(merchant, style = MaterialTheme.typography.headlineSmall)
            Text(
                Format.amount(d.amountMinor),
                style = MaterialTheme.typography.headlineMedium,
                color = if (d.amountMinor > 0) LocalSproutColors.current.income else MaterialTheme.colorScheme.onSurface,
            )
            Text(d.description, style = MaterialTheme.typography.bodyMedium)
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

            Text("Category: ${d.categoryName}", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Always use this for $merchant", always) { always = it }
            CategoryGrid(categories, d.categoryId) { vm.pick(it, always, merchant) }

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
