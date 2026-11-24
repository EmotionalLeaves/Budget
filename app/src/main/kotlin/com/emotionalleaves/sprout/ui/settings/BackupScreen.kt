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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.backup.BackupCrypto
import com.emotionalleaves.sprout.data.backup.BackupException
import com.emotionalleaves.sprout.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → Backup and restore. The file is encrypted with a password the user picks, saved wherever they choose,
 * and never contains the bank connection.
 */
@Composable
fun BackupScreen(snackbar: SnackbarHostState, onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var askBackupPassword by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingPassword
        pendingPassword = null
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = runCatching {
                val bytes = c.backupRepository.create(pw)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no stream")
                }
            }
            pw.fill('\u0000')
            busy = false
            snackbar.showSnackbar(if (result.isSuccess) "Backup saved" else "Backup failed. Try a different folder.")
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Backup and restore", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "A backup is one file with all your transactions, accounts, categories, budgets, rules and bills, locked with a " +
                "password you choose. Save it somewhere safe (for example your Downloads or Google Drive). It never includes " +
                "your bank connection: after restoring on a new phone, connect SimpleFIN again.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Sprout can't recover a forgotten backup password.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { askBackupPassword = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Back up to a file")
        }
        OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Restore from a backup")
        }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (askBackupPassword) {
        PasswordDialog(
            title = "Choose a backup password",
            confirmTwice = true,
            onDismiss = { askBackupPassword = false },
            onConfirm = { pw ->
                askBackupPassword = false
                pendingPassword = pw
                saveLauncher.launch("sprout-backup-${LocalDate.now()}.sproutbackup")
            },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = "Backup password",
            confirmTwice = false,
            onDismiss = { restoreUri = null },
            onConfirm = { pw ->
                restoreUri = null
                busy = true
                error = null
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BackupException("That file couldn't be opened.")
                        }
                        pendingRestore = c.backupRepository.read(bytes, pw)
                    } catch (e: BackupException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = "That file couldn't be opened."
                    } finally {
                        pw.fill('\u0000')
                        busy = false
                    }
                }
            },
        )
    }
    pendingRestore?.let { backup ->
        val created = (backup["createdAt"] as? JsonPrimitive)?.longOrNull?.let {
            Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US))
        } ?: "an unknown date"
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace your data?") },
            text = { Text("Everything in Sprout now will be replaced by the backup from $created. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        val msg = try {
                            val s = c.backupRepository.restore(backup)
                            c.alertRepository.evaluateBudgets()
                            "Restored ${s.transactions} transactions, ${s.accounts} accounts, ${s.budgets} budgets"
                        } catch (e: BackupException) {
                            e.message ?: "Restore failed"
                        } catch (e: Exception) {
                            "Restore failed. Your data wasn't changed."
                        }
                        busy = false
                        snackbar.showSnackbar(msg)
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PasswordDialog(title: String, confirmTwice: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val longEnough = first.length >= BackupCrypto.MIN_PASSWORD_LENGTH
    val matches = !confirmTwice || first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = first, onValueChange = { first = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = second, onValueChange = { second = it }, label = { Text("Type it again") }, singleLine = true,
                        isError = second.isNotEmpty() && !matches,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    )
                    Text("At least ${BackupCrypto.MIN_PASSWORD_LENGTH} characters.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(first.toCharArray()) }, enabled = longEnough && matches) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
