package com.emotionalleaves.sprout.ui.update

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

/** Calm, dismissible "update ready" card for Home. Shows nothing when there's nothing to do. */
@Composable
fun UpdateCard(updates: UpdateRepository) {
    val state by updates.state.collectAsState()
    val dismissed by updates.dismissedVersion.collectAsState(initial = null)
    val visible = when (val s = state) {
        is UpdateState.Available -> s.release.version != dismissed
        is UpdateState.Downloading, is UpdateState.ReadyToInstall, is UpdateState.OpenReleasesPage -> true
        else -> false
    }
    AnimatedVisibility(visible) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) { UpdateBody(updates, state, showLater = true) }
    }
}

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { updates.download(state.release) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Update") }
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
                LaunchedEffect(state.file) { if (allowed) open(context, updates.installIntent(state.file)) }
                Text("Ready to install ${state.release.version}", style = MaterialTheme.typography.titleSmall)
                if (!allowed) {
                    Text(
                        "One-time step: Android asks you to allow Sprout to install updates. Tap below, switch on " +
                            "\"Allow from this source\", then come back and tap Install.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { open(context, updates.unknownSourcesIntent()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow updates") }
                }
                Button(onClick = { open(context, updates.installIntent(state.file)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Install") }
                Text("Your data stays: the update installs over this version.", style = MaterialTheme.typography.labelSmall)
            }
            is UpdateState.OpenReleasesPage -> {
                Text(
                    if (state.release != null) "Sprout ${state.release.version} is out" else "Get the newest Sprout",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("Open the Releases page and tap the .apk to install it over this version.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Releases") }
                    if (showLater) TextButton(onClick = { updates.reset() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Later") }
                }
            }
            else -> Unit
        }
    }
}

/** Settings → Updates: version, check now, automatic checks. */
@Composable
fun UpdateSettings(updates: UpdateRepository) {
    val state by updates.state.collectAsState()
    val auto by updates.autoCheck.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("You have Sprout ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Check for updates automatically", Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = { scope.launch { updates.setAutoCheck(it) } })
        }
        when (state) {
            UpdateState.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            UpdateState.UpToDate -> Text("You're up to date.", style = MaterialTheme.typography.bodySmall)
            is UpdateState.Failed -> Text((state as UpdateState.Failed).message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall, is UpdateState.OpenReleasesPage ->
                Card { UpdateBody(updates, state, showLater = false) }
            UpdateState.Idle -> Unit
        }
        OutlinedButton(
            onClick = { scope.launch { updates.check() } },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Check for updates") }
    }
}
