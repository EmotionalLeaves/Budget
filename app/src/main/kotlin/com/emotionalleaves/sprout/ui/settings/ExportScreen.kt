package com.emotionalleaves.sprout.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.csv.ExportKind
import com.emotionalleaves.sprout.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Settings → Export. Files are written with the Storage Access Framework (the user picks where). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(snackbar: SnackbarHostState, onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(ExportKind.TRANSACTIONS) }
    var allTime by remember { mutableStateOf(true) }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val text = c.exportRepository.export(kind, if (allTime) null else from, if (allTime) null else to)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Exported ${kind.label.lowercase()}" else "Export failed. Try a different folder.")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Export CSV", style = MaterialTheme.typography.titleLarge)
        }
        Text("Exports never include your bank connection or any password.", style = MaterialTheme.typography.bodyMedium)
        ExportKind.entries.forEach { k ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(kind == k, role = Role.RadioButton) { kind = k },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = kind == k, onClick = null)
                Text(k.label, Modifier.padding(start = 8.dp))
            }
        }
        if (kind == ExportKind.TRANSACTIONS) {
            Text("Dates", style = MaterialTheme.typography.titleSmall)
            listOf(true to "All time", false to "A date range").forEach { (v, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(allTime == v, role = Role.RadioButton) { allTime = v },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = allTime == v, onClick = null)
                    Text(label, Modifier.padding(start = 8.dp))
                }
            }
            if (!allTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("From $from") }
                    OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("To $to") }
                }
            }
        }
        Button(
            onClick = { launcher.launch("sprout-${kind.fileStem}-${LocalDate.now()}.csv") },
            enabled = allTime || !to.isBefore(from),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Export ${kind.label.lowercase()}") }
    }

    picking?.let { which ->
        val initial = if (which == "from") from else to
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") from = d else to = d
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
