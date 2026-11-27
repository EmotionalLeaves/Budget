package com.emotionalleaves.sprout.ui.update

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.BuildConfig
import com.emotionalleaves.sprout.data.update.UpdateRepository
import com.emotionalleaves.sprout.data.update.UpdateState
import kotlinx.coroutines.launch

private fun open(context: android.content.Context, intent: Intent) = runCatching { context.startActivity(intent) }

/** Installs in place (PackageInstaller session); falls back to Android's installer screen if that can't start. */
private fun installNow(context: android.content.Context, updates: UpdateRepository, file: java.io.File) {
    if (!com.emotionalleaves.sprout.data.update.UpdateInstaller.install(context, file)) open(context, updates.installIntent(file))
}

/** Calm, dismissible "update ready" card for Home. Shows nothing when there's nothing to do. */
@Composable
fun UpdateCard(updates: UpdateRepository) {
    val state by updates.state.collectAsState()
    val dismissed by updates.dismissedVersion.collectAsState(initial = null)
    val visible = when (val s = state) {
        is UpdateState.Available -> s.release.version != dismissed
        is UpdateState.Downloading, is UpdateState.ReadyToInstall, is UpdateState.OpenReleasesPage, is UpdateState.DownloadFailed, is UpdateState.InstallFailed -> true
        else -> false
    }
    AnimatedVisibility(visible) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) { UpdateBody(updates, state, showLater = true) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UpdateBody(updates: UpdateRepository, state: UpdateState, showLater: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is UpdateState.Available -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                    Text("Sprout ${state.release.version} is ready", style = MaterialTheme.typography.titleMedium)
                }
                if (state.release.notes.isNotBlank()) Text(state.release.notes, style = MaterialTheme.typography.bodySmall)
                if (state.release.sizeBytes > 0) {
                    Text("%.1f MB download".format(state.release.sizeBytes / 1_048_576.0), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val waiting by androidx.compose.runtime.remember {
                    com.emotionalleaves.sprout.work.UpdateWhenChargingWorker.observeWaitingFor(context)
                }.collectAsState(initial = null)
                val queued = waiting != null
                var meteredAsk by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if (meteredAsk) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { meteredAsk = false },
                        title = { Text("You're on mobile data") },
                        text = {
                            Text(
                                (if (state.release.sizeBytes > 0) "This update is %.1f MB. ".format(state.release.sizeBytes / 1_048_576.0) else "") +
                                    "Download it now, or let Sprout wait for Wi-Fi and update itself then.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                meteredAsk = false
                                com.emotionalleaves.sprout.work.UpdateWhenChargingWorker.schedule(context, onWifi = true)
                            }) { Text("Wait for Wi-Fi") }
                        },
                        dismissButton = { TextButton(onClick = { meteredAsk = false; scope.launch { updates.download(state.release) } }) { Text("Download now") } },
                    )
                }
                if (queued) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (waiting == "wifi") "Sprout will update itself as soon as you're on Wi-Fi. Nothing else to do."
                            else "Sprout will update itself the next time your phone is charging. Nothing else to do.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { com.emotionalleaves.sprout.work.UpdateWhenChargingWorker.cancel(context) }) { Text("Cancel") }
                    }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                        // Ask once on mobile data (only when Sprout can update by itself later); otherwise just go.
                        if (updates.hasKey && cm != null && cm.isActiveNetworkMetered) meteredAsk = true
                        else scope.launch { updates.download(state.release) }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Update now") }
                    if (updates.hasKey && !queued) {
                        OutlinedButton(onClick = {
                            com.emotionalleaves.sprout.work.UpdateWhenChargingWorker.schedule(context)
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("While charging") }
                    }
                    if (showLater) TextButton(onClick = { scope.launch { updates.dismiss(state.release.version) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
                }
            }
            is UpdateState.Downloading -> {
                Text("Downloading ${state.release.version}… ${state.percent}%", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.ReadyToInstall -> {
                val allowed = updates.canInstall()
                // Open the installer as soon as the download finishes, when Android already allows it.
                LaunchedEffect(state.file) { if (allowed) installNow(context, updates, state.file) }
                Text("Ready to install ${state.release.version}", style = MaterialTheme.typography.titleSmall)
                if (!allowed) {
                    Text(
                        "One-time step: Android asks you to allow Sprout to install updates. Tap below, switch on " +
                            "\"Allow from this source\", then come back and tap Install.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { open(context, updates.unknownSourcesIntent()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow updates") }
                }
                Button(onClick = { installNow(context, updates, state.file) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Install") }
                Text("Your data stays: the update installs over this version, and a safety copy is saved first (Settings → Backup).",
                    style = MaterialTheme.typography.labelSmall)
            }
            is UpdateState.OpenReleasesPage -> {
                Text(
                    if (state.release != null) "Sprout ${state.release.version} is out" else "Get the newest Sprout",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("To update right here in Sprout, add your read-only update key in Settings → Updates (one time). Or open the Releases page.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Releases") }
                    if (showLater) TextButton(onClick = { updates.reset() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
                }
            }
            is UpdateState.InstallFailed -> {
                Text("${state.release.version} didn't install", style = MaterialTheme.typography.titleSmall)
                Text("It may have been cancelled, or the phone is low on space. Nothing changed.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { installNow(context, updates, state.file) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Install again") }
            }
            is UpdateState.DownloadFailed -> {
                Text("The download of ${state.release.version} didn't finish", style = MaterialTheme.typography.titleSmall)
                Text("Check your connection and try again. Nothing changed on your phone.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { updates.download(state.release) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    if (showLater) TextButton(onClick = { updates.reset() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
                }
            }
            else -> Unit
        }
    }
}

/** Settings → Updates: version, check now, automatic checks. */
@Composable
fun UpdateSettings(updates: UpdateRepository, beta: Boolean = false, onBeta: (Boolean) -> Unit = {}) {
    val state by updates.state.collectAsState()
    val auto by updates.autoCheck.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("You have Sprout ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Check for updates automatically", Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { scope.launch { updates.setAutoCheck(it) } })
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Get test builds too")
                Text("Newer builds before they're a release. Might be rough.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = beta, onCheckedChange = onBeta)
        }
        val hasKey by updates.hasKeyFlow.collectAsState()
        LaunchedEffect(Unit) { updates.refreshHasKey() }
        if (!updates.keyIsBuiltIn) UpdateKeyRow(updates, hasKey)
        if (hasKey) {
            val autoDl by updates.autoDownload.collectAsState(initial = true)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Download updates on Wi-Fi in the background", Modifier.weight(1f))
                Switch(checked = autoDl, enabled = auto, onCheckedChange = { scope.launch { updates.setAutoDownload(it) } })
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val autoIn by updates.autoInstall.collectAsState(initial = true)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Install updates by themselves")
                        Text("After one update installed from Sprout, later ones install in the background.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoIn, enabled = auto && autoDl, onCheckedChange = { scope.launch { updates.setAutoInstall(it) } })
                }
            }
        }
        when (state) {
            UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            UpdateState.UpToDate, UpdateState.Idle -> {
                val last by updates.lastCheckedAt.collectAsState(initial = null)
                val ago = last?.let { com.emotionalleaves.sprout.ui.common.Format.ago(it, System.currentTimeMillis() / 1000) }
                Text(
                    when {
                        state == UpdateState.UpToDate -> "You're up to date." + (ago?.let { " Checked $it." } ?: "")
                        ago != null -> "Last checked $ago."
                        else -> "Not checked yet."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UpdateState.Failed -> Text((state as UpdateState.Failed).message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall, is UpdateState.OpenReleasesPage, is UpdateState.DownloadFailed, is UpdateState.InstallFailed ->
                Card { UpdateBody(updates, state, showLater = false) }
        }
        val context = LocalContext.current
        var notes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.emotionalleaves.sprout.update.Changelog.Group>?>(null) }
        TextButton(onClick = { scope.launch { notes = bundledNotes(context) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("What's new in this version") }
        TextButton(onClick = {
            scope.launch {
                val md = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrDefault("")
                }
                notes = com.emotionalleaves.sprout.update.Changelog.between(md, "0", BuildConfig.VERSION_NAME)
                    .flatMap { (v, g) -> listOf(com.emotionalleaves.sprout.update.Changelog.Group("Version $v", emptyList())) + g }
            }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Release history") }
        notes?.let { n ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { notes = null },
                title = { Text("What's new in ${BuildConfig.VERSION_NAME}") },
                text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { if (n.isEmpty()) Text("No notes for this build.") else WhatsNewList(n) } },
                confirmButton = { TextButton(onClick = { notes = null }) { Text("OK") } },
            )
        }
        OutlinedButton(
            onClick = { scope.launch { updates.check() } },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Check for updates") }
    }
}


/**
 * Paste a read-only GitHub key once so updates download inside Sprout (this repo is private). Stored encrypted with
 * a key that never leaves the phone; only ever sent to api.github.com.
 */
@Composable
private fun UpdateKeyRow(updates: UpdateRepository, hasKey: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    if (hasKey && !editing) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Update key saved: updates install right here", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { editing = true }) { Text("Change") }
        }
        return
    }
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Update inside Sprout", style = MaterialTheme.typography.titleSmall)
            Text(
                "One time: make a read-only key on GitHub (Settings → Developer settings → Fine-grained tokens → only the Budget repo, " +
                    "Contents: Read-only), then paste it here. After that, updates download and install without leaving Sprout.",
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { text = it.trim().take(200) }, singleLine = true,
                label = { Text("Read-only key") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val ok = updates.saveKey(text)
                        result = if (ok) null else "GitHub didn't accept that key. Check it has read access to the Budget repo."
                        if (ok) { text = ""; editing = false }
                    }
                }, enabled = text.length >= 20, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save key") }
                TextButton(onClick = {
                    open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new")))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Make a key") }
            }
        }
    }
}
