package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.budget.FlexState
import com.emotionalleaves.sprout.domain.budget.Pace
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.FatProgress
import com.emotionalleaves.sprout.ui.common.ProgressRing
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import java.time.LocalDate

/** "On pace for $X by month end" for the current month; null for other months or too early to tell. */
fun paceNote(spentMinor: Long, budgetMinor: Long?, today: LocalDate, isCurrentMonth: Boolean): String? {
    if (!isCurrentMonth) return null
    val p = Pace.of(spentMinor, budgetMinor, today.dayOfMonth, today.lengthOfMonth())
    if (p.tooEarly || spentMinor == 0L) return null
    val over = p.overByMinor
    return if (over != null && over > 0) "On pace for ${Money.formatWhole(p.projectedMinor)} — ${Money.formatWhole(over)} over"
    else "On pace for ${Money.formatWhole(p.projectedMinor)}"
}

/** Flex mode: one amount for everyday spending, fixed costs expected, planned expenses set aside. */
@Composable
fun FlexSection(state: FlexState, today: LocalDate, isCurrent: Boolean, onSetFlex: (Long) -> Unit, onSetFixed: (Set<Long>) -> Unit,
                suggest: (suspend () -> Long?)? = null) {
    val m = state.month
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val sc = LocalSproutColors.current
    SoftCard(onClick = { editing = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Flex spending", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.flexBudgetMinor <= 0) {
                    Text("Set one amount", style = MaterialTheme.typography.headlineSmall)
                    Text("Everything that isn't a fixed cost shares it. Tap to set.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(leftOrOver(m.flexLeftMinor), style = MaterialTheme.typography.headlineSmall,
                        color = if (m.flexLeftMinor < 0) sc.danger else MaterialTheme.colorScheme.onSurface)
                    Text("${Money.format(m.flexSpentMinor)} of ${Money.format(m.flexBudgetMinor)}", style = MaterialTheme.typography.bodyMedium)
                    paceNote(m.flexSpentMinor, m.flexBudgetMinor, today, isCurrent)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (m.flexBudgetMinor > 0) {
                val f = m.flexSpentMinor.toFloat() / m.flexBudgetMinor
                ProgressRing(f, if (f >= 1f) sc.danger else if (f >= 0.8f) sc.warning else sc.ok, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(76.dp), stroke = 9.dp) {
                    Text("${(f * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (m.plannedSetAsideMinor > 0) Text("Set aside this month for planned expenses: ${Money.format(m.plannedSetAsideMinor)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SoftCard(onClick = { choosing = true }) {
        Text("Fixed costs", style = MaterialTheme.typography.titleSmall)
        Text("${Money.format(m.fixedSpentMinor)} paid" + (if (m.fixedExpectedMinor > 0) " · bills this month ≈ ${Money.format(m.fixedExpectedMinor)}" else ""),
            style = MaterialTheme.typography.bodyMedium)
        if (m.fixedExpectedMinor > 0) FatProgress(m.fixedSpentMinor.toFloat() / m.fixedExpectedMinor, MaterialTheme.colorScheme.secondary)
        Text(state.categories.filter { it.first in state.fixedIds }.joinToString(", ") { it.second }.ifEmpty { "None chosen" } + " · tap to change",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) {
        var text by remember { mutableStateOf(if (m.flexBudgetMinor > 0) (m.flexBudgetMinor / 100).toString() else "") }
        val parsed = Money.parseUserInput(text)?.takeIf { it >= 0 }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Flex spending per month") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One amount for groceries, eating out, shopping and everything else that isn't fixed.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it.take(10) }, prefix = { Text("$") }, singleLine = true, isError = text.isNotBlank() && parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    // One tap to start from what you actually spend.
                    var suggestion by remember { mutableStateOf<Long?>(null) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { suggestion = suggest?.let { runCatching { it() }.getOrNull() } }
                    suggestion?.let { amt ->
                        androidx.compose.material3.SuggestionChip(
                            onClick = { text = (amt / 100).toString() },
                            label = { Text("Use ${Money.formatWhole(amt)} · your usual month") },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { parsed?.let(onSetFlex); editing = false }, enabled = parsed != null) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    if (choosing) {
        val picked = remember { mutableStateListOf<Long>().apply { addAll(state.fixedIds) } }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Which costs are fixed?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.categories.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { if (id in picked) picked.remove(id) else picked.add(id) },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in picked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSetFixed(picked.toSet()); choosing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}
