package com.emotionalleaves.sprout.ui.importcsv

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.csv.Csv
import com.emotionalleaves.sprout.data.csv.CsvImport
import com.emotionalleaves.sprout.data.csv.CsvMapping
import com.emotionalleaves.sprout.data.csv.CsvParseResult
import com.emotionalleaves.sprout.data.csv.ImportSummary
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.Banner
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ImportUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val rows: List<List<String>> = emptyList(),
    val mapping: CsvMapping? = null,
    val parsed: CsvParseResult? = null,
    /** Existing manual account id, or null to create [newName]. */
    val accountId: String? = null,
    val newName: String = "",
    val newType: AccountType = AccountType.CHECKING,
    val importing: Boolean = false,
    val summary: ImportSummary? = null,
) {
    val header: List<String> get() = mapping?.let { rows.getOrNull(it.headerRow) } ?: emptyList()
}

class ImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    val manualAccounts: StateFlow<List<AccountEntity>> =
        c.db.accounts().observeManual().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(context: Context, uri: Uri) {
        _state.value = ImportUiState(loading = true)
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                        if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) {
                _state.value = ImportUiState(error = "That file couldn't be read (or it's larger than 10 MB).")
                return@launch
            }
            val rows = withContext(Dispatchers.Default) { Csv.parse(text) }
            val mapping = CsvImport.detect(rows)
            _state.value = ImportUiState(
                rows = rows,
                mapping = mapping,
                parsed = mapping?.let { CsvImport.parse(rows, it) },
                error = if (mapping == null) "Sprout couldn't find Date, Description and Amount columns in this file." else null,
            )
        }
    }

    fun updateMapping(m: CsvMapping) = _state.update { it.copy(mapping = m, parsed = if (m.isValid) CsvImport.parse(it.rows, m) else null) }
    fun chooseAccount(id: String?) = _state.update { it.copy(accountId = id) }
    fun setNewName(name: String) = _state.update { it.copy(newName = name.take(60), accountId = null) }
    fun setNewType(t: AccountType) = _state.update { it.copy(newType = t) }

    fun import() {
        val s = _state.value
        val parsed = s.parsed ?: return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val accountId = s.accountId ?: c.importRepository.createManualAccount(s.newName, s.newType)
            val summary = c.importRepository.import(accountId, parsed)
            _state.update { it.copy(importing = false, summary = summary) }
        }
    }

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

private val previewDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

@Composable
fun ImportScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val vm = sproutViewModel { ImportViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.manualAccounts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Storage Access Framework: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.load(context, it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Import CSV", style = MaterialTheme.typography.titleLarge)
        }

        state.summary?.let { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Import finished", style = MaterialTheme.typography.titleMedium)
                    Text("${s.imported} imported")
                    Text("${s.duplicates} skipped as already imported")
                    Text("${s.failed.size} couldn't be read")
                    s.failed.take(20).forEach { Text("Line ${it.line}: ${it.reason}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            return@Column
        }

        if (state.rows.isEmpty()) {
            Text(
                "Download a CSV from your bank's website (look for Download or Export on the transactions page), then pick it here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream")) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Choose a CSV file") }
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Banner(it) }
            return@Column
        }

        state.error?.let { Banner(it) }
        val header = state.header.ifEmpty { state.rows.firstOrNull().orEmpty() }
        val m = state.mapping ?: CsvMapping(0, 0, 1, amount = 2, dateFormat = CsvImport.DATE_FORMATS.first())
        Text("Columns", style = MaterialTheme.typography.titleMedium)
        ColumnPicker("Date", header, m.date) { vm.updateMapping(m.copy(date = it ?: 0)) }
        ColumnPicker("Description", header, m.description) { vm.updateMapping(m.copy(description = it ?: 0)) }
        val split = m.amount == null
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) {
            vm.updateMapping(if (split) m.copy(amount = m.debit ?: m.credit ?: 0, debit = null, credit = null) else m.copy(debit = m.amount, credit = null, amount = null))
        }, verticalAlignment = Alignment.CenterVertically) {
            Text("Separate Debit and Credit columns", Modifier.weight(1f))
            Switch(checked = split, onCheckedChange = null)
        }
        if (split) {
            ColumnPicker("Debit (money out)", header, m.debit, allowNone = true) { vm.updateMapping(m.copy(debit = it)) }
            ColumnPicker("Credit (money in)", header, m.credit, allowNone = true) { vm.updateMapping(m.copy(credit = it)) }
        } else {
            ColumnPicker("Amount", header, m.amount) { vm.updateMapping(m.copy(amount = it ?: 0)) }
        }
        FormatPicker(m.dateFormat) { vm.updateMapping(m.copy(dateFormat = it)) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { vm.updateMapping(m.copy(invert = !m.invert)) },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Invert signs")
                Text("Turn on if purchases show as positive numbers", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = m.invert, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { vm.updateMapping(m.copy(decimalComma = !m.decimalComma)) },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Comma decimals")
                Text("Turn on if amounts look like 1.234,56", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = m.decimalComma, onCheckedChange = null)
        }
        ColumnPicker("Bank's category (optional)", header, m.category, allowNone = true) { vm.updateMapping(m.copy(category = it)) }

        val parsed = state.parsed
        Text("Preview", style = MaterialTheme.typography.titleMedium)
        if (parsed == null || parsed.rows.isEmpty()) {
            Text("No rows could be read with these settings.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("${parsed.rows.size} rows ready, ${parsed.errors.size} can't be read", style = MaterialTheme.typography.bodySmall)
            parsed.rows.take(10).forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text(r.date.format(previewDate), Modifier.weight(0.3f), style = MaterialTheme.typography.bodySmall)
                    Text(r.description, Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(Money.format(r.amountMinor, showPlus = true), Modifier.weight(0.2f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Import into", style = MaterialTheme.typography.titleMedium)
        accounts.forEach { a ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.accountId == a.id, role = Role.RadioButton) { vm.chooseAccount(a.id) },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.accountId == a.id, onClick = null)
                Text(a.label, Modifier.padding(start = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.accountId == null, role = Role.RadioButton) { vm.chooseAccount(null) },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = state.accountId == null, onClick = null)
            Text("A new account", Modifier.padding(start = 8.dp))
        }
        if (state.accountId == null) {
            OutlinedTextField(value = state.newName, onValueChange = vm::setNewName, label = { Text("Account name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            TypePicker(state.newType, vm::setNewType)
        }
        Button(
            onClick = vm::import,
            enabled = !state.importing && parsed != null && parsed.rows.isNotEmpty() && (state.accountId != null || state.newName.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(if (state.importing) "Importing…" else "Import ${parsed?.rows?.size ?: 0} transactions") }
    }
}

@Composable
private fun ColumnPicker(label: String, header: List<String>, selected: Int?, allowNone: Boolean = false, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = selected?.let { header.getOrNull(it)?.ifBlank { "Column ${it + 1}" } ?: "Column ${it + 1}" } ?: "None"
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(name) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onPick(null); open = false })
            header.forEachIndexed { i, h ->
                DropdownMenuItem(text = { Text(h.ifBlank { "Column ${i + 1}" }) }, onClick = { onPick(i); open = false })
            }
        }
    }
}

@Composable
private fun FormatPicker(selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Date format", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(selected) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CsvImport.DATE_FORMATS.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { onPick(f); open = false }) }
        }
    }
}

@Composable
private fun TypePicker(selected: AccountType, onPick: (AccountType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Account type", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(selected.name.lowercase().replaceFirstChar { it.uppercase() })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AccountType.entries.forEach { t ->
                DropdownMenuItem(text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { onPick(t); open = false })
            }
        }
    }
}
