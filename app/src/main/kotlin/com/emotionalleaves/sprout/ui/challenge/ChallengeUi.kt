package com.emotionalleaves.sprout.ui.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.challenge.ChallengeProgress
import com.emotionalleaves.sprout.domain.challenge.ChallengeStatus
import com.emotionalleaves.sprout.domain.challenge.ChallengeType
import com.emotionalleaves.sprout.domain.challenge.HealthScore
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.FatProgress
import com.emotionalleaves.sprout.ui.common.IconBadge
import com.emotionalleaves.sprout.ui.common.ProgressRing
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.common.rememberLoaded
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import kotlinx.coroutines.launch

@Composable
private fun statusColor(s: ChallengeStatus) = when (s) {
    ChallengeStatus.WON -> LocalSproutColors.current.ok
    ChallengeStatus.MISSED -> LocalSproutColors.current.danger
    ChallengeStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
}

@Composable
fun ChallengeCard(p: ChallengeProgress, onRemove: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.EmojiEvents, statusColor(p.status))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.challenge.title, style = MaterialTheme.typography.titleSmall)
                Text(when (p.status) { ChallengeStatus.WON -> "Done! " + p.detail; ChallengeStatus.MISSED -> p.detail; else -> p.detail },
                    style = MaterialTheme.typography.labelMedium, color = statusColor(p.status))
            }
            if (onRemove != null) TextButton(onClick = onRemove) { Text(if (p.status == ChallengeStatus.ON_TRACK) "Stop" else "Clear") }
        }
        FatProgress(p.fraction, statusColor(p.status))
    }
}

/** Short spending challenges with progress: start one from a template in a tap. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChallengesScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val list by remember { c.challengeRepository.challenges }.collectAsStateWithLifecycle(emptyList())
    val progress = rememberLoaded(list) { c.challengeRepository.progress() } ?: emptyList()
    val cats by remember { c.db.categories().observeActive() }.collectAsStateWithLifecycle(emptyList())
    var custom by remember { mutableStateOf(false) }
    val eatingOut = cats.firstOrNull { it.key == DefaultCategories.EATING_OUT }
    val groceries = cats.firstOrNull { it.key == DefaultCategories.GROCERIES }
    val shopping = cats.firstOrNull { it.key == DefaultCategories.SHOPPING }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Challenges") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        LazyColumn {
            if (progress.isEmpty()) item { EmptyState("No challenges yet", "Pick one below. Small and time-boxed works best.") }
            items(progress, key = { it.challenge.id }) { p -> ChallengeCard(p, onRemove = { scope.launch { c.challengeRepository.remove(p.challenge.id) } }) }
            item { com.emotionalleaves.sprout.ui.common.SectionHeader("Start one") }
            val templates = listOfNotNull(
                eatingOut?.let { Triple("No eating out for 7 days", ChallengeType.NO_SPEND_CATEGORY, it.id) },
                shopping?.let { Triple("No shopping for 14 days", ChallengeType.NO_SPEND_CATEGORY, it.id) },
                groceries?.let { Triple("Groceries under \$100 this week", ChallengeType.UNDER_AMOUNT, it.id) },
                Triple("3 no-spend days this week", ChallengeType.NO_SPEND_DAYS, null),
            )
            items(templates, key = { it.first }) { (title, type, cat) ->
                SoftCard(onClick = {
                    scope.launch {
                        when (type) {
                            ChallengeType.NO_SPEND_CATEGORY -> c.challengeRepository.add(type, title, if (title.contains("14")) 14 else 7, cat)
                            ChallengeType.UNDER_AMOUNT -> c.challengeRepository.add(type, title, 7, cat, amountMinor = 10_000)
                            ChallengeType.NO_SPEND_DAYS -> c.challengeRepository.add(type, title, 7, goal = 3)
                        }
                    }
                }) { Text(title, style = MaterialTheme.typography.titleSmall); Text("Tap to start today", style = MaterialTheme.typography.labelMedium) }
            }
            item { TextButton(onClick = { custom = true }, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) { Text("Make your own…") } }
        }
    }
    if (custom) {
        var catId by remember { mutableStateOf<Long?>(null) }
        var days by remember { mutableIntStateOf(7) }
        var amount by remember { mutableStateOf("") }
        val expense = cats.filter { it.kind == CategoryKind.EXPENSE }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("Your challenge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        expense.take(12).forEach { FilterChip(catId == it.id, { catId = it.id }, label = { Text(it.name) }) }
                    }
                    Text("For", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(7, 14, 30).forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d days") }) } }
                    OutlinedTextField(amount, { amount = it.take(8) }, label = { Text("Limit (empty = spend nothing)") }, prefix = { Text("$") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = catId != null, onClick = {
                    val id = catId ?: return@TextButton
                    val name = expense.first { it.id == id }.name
                    val limit = Money.parseUserInput(amount)?.takeIf { it > 0 }
                    scope.launch {
                        if (limit == null) c.challengeRepository.add(ChallengeType.NO_SPEND_CATEGORY, "No $name for $days days", days, id)
                        else c.challengeRepository.add(ChallengeType.UNDER_AMOUNT, "$name under ${Money.formatWhole(limit)} for $days days", days, id, amountMinor = limit)
                    }
                    custom = false
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } },
        )
    }
}

/** "Money health 72/100" with a ring; tap for what makes up the score. */
@Composable
fun HealthCard(r: HealthScore.Result) {
    val score = r.score ?: return
    var open by remember { mutableStateOf(false) }
    val sc = LocalSproutColors.current
    val color = if (score >= 75) sc.ok else if (score >= 45) sc.warning else sc.danger
    SoftCard(onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(score / 100f, color, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(64.dp), stroke = 7.dp) {
                Text("$score", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Money health", style = MaterialTheme.typography.titleSmall)
                Text(r.parts.minByOrNull { it.points * 100 / it.max }?.tip ?: "", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = color)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Money health: $score/100") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                r.parts.forEach { p ->
                    Column {
                        Row { Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Text("${p.points}/${p.max}") }
                        FatProgress(p.points.toFloat() / p.max, MaterialTheme.colorScheme.primary, height = 6.dp)
                        Text(p.tip, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("A simple guide, not a credit score. Parts Sprout can't work out yet are left out.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
    )
}
