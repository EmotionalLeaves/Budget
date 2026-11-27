package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.categories.CategoryGrid
import com.emotionalleaves.sprout.ui.common.appContainer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Add a cash (or any manual) transaction: amount, what it was, category, account, date, note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val accounts by remember { c.db.accounts().observeVisible() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember { c.db.categories().observeActive() }.collectAsStateWithLifecycle(emptyList())
    var spending by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var what by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf(com.emotionalleaves.sprout.data.manual.ManualRepository.CASH_ACCOUNT_ID) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val manualAccounts = accounts.filter { it.source == AccountSource.MANUAL }
    val amount = Money.parseUserInput(amountText)?.takeIf { it > 0 }
    LaunchedEffect(spending, categories) {
        if (categoryId == null || categories.none { it.id == categoryId }) categoryId = null
    }
    // Fill in the category from what you type, until you pick one yourself.
    var pickedByHand by remember { mutableStateOf(false) }
    var guessed by remember { mutableStateOf(false) }
    LaunchedEffect(what, spending) {
        if (pickedByHand) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        val g = runCatching { c.categoryRepository.guessCategory(what, if (spending) -(amount ?: 1) else (amount ?: 1)) }.getOrNull()
        val ok = g != null && categories.any { it.id == g && ((it.kind == CategoryKind.EXPENSE) == spending || it.kind == CategoryKind.TRANSFER) }
        if (ok) { categoryId = g; guessed = true } else if (guessed) { categoryId = null; guessed = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a transaction", style = MaterialTheme.typography.headlineSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(spending, { spending = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Money out") }
                SegmentedButton(!spending, { spending = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Money in") }
            }
            OutlinedTextField(amountText, { amountText = it.take(12) }, label = { Text("Amount") }, prefix = { Text("$") }, singleLine = true,
                isError = amountText.isNotBlank() && amount == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(what, { what = it.take(120) }, label = { Text("What was it? (\"Farmers market\")") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (guessed && categoryId != null) {
                    Text("Guessed from the name · tap to change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            CategoryGrid(categories.filter { (it.kind == CategoryKind.EXPENSE) == spending || it.kind == CategoryKind.TRANSFER }, categoryId) {
                categoryId = it.id; pickedByHand = true; guessed = false
            }
            Text("Account", style = MaterialTheme.typography.titleSmall)
            // Wraps instead of running off the edge when there are several accounts.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = (listOf(com.emotionalleaves.sprout.data.manual.ManualRepository.CASH_ACCOUNT_ID to "Cash") +
                    manualAccounts.filter { it.id != com.emotionalleaves.sprout.data.manual.ManualRepository.CASH_ACCOUNT_ID }.map { it.id to it.label })
                options.take(8).forEach { (id, label) ->
                    androidx.compose.material3.FilterChip(accountId == id, { accountId = id }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("Date", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)))
            }
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val cat = categoryId ?: return@Button
                    val a = amount ?: return@Button
                    scope.launch {
                        val acct = if (accountId == com.emotionalleaves.sprout.data.manual.ManualRepository.CASH_ACCOUNT_ID) c.manualRepository.ensureCashAccount() else accountId
                        val id = c.manualRepository.add(acct, if (spending) -a else a, what, cat, date, note)
                        c.alertRepository.evaluateBudgets()
                        onAdded(id)
                    }
                },
                enabled = amount != null && categoryId != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(bottom = 16.dp),
            ) { Text("Add ${amount?.let { Money.format(it) } ?: ""}") }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
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
