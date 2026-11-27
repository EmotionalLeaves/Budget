package com.emotionalleaves.sprout.ui.budgets

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.emotionalleaves.sprout.ui.common.FatProgress
import com.emotionalleaves.sprout.ui.common.CategoryIcons
import com.emotionalleaves.sprout.ui.common.IconBadge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.budget.CategoryLine
import com.emotionalleaves.sprout.domain.budget.ProgressLevel
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors

@Composable
fun levelColor(level: ProgressLevel?): Color {
    val c = LocalSproutColors.current
    return when (level) {
        ProgressLevel.RED -> c.danger
        ProgressLevel.AMBER -> c.warning
        else -> c.ok
    }
}

/** "$X left" / "$X over" — the text always carries the meaning, not just the color. */
fun leftOrOver(remaining: Long): String =
    if (remaining >= 0) "${Money.format(remaining)} left" else "${Money.format(-remaining)} over"

/** " (incl. $50 rolled over)" or " ($30 overspent last month)" when rollover changed this month's budget. */
fun carryNote(line: CategoryLine): String = when {
    !line.rolloverOn || line.carryMinor == 0L || line.baseBudgetMinor == null -> ""
    line.carryMinor > 0 -> " (incl. ${Money.format(line.carryMinor)} rolled over)"
    else -> " (${Money.format(-line.carryMinor)} overspent before)"
}

@Composable
fun BudgetProgress(spent: Long, budget: Long, level: ProgressLevel?, modifier: Modifier = Modifier) {
    val fraction = if (budget <= 0) (if (spent > 0) 1f else 0f) else (spent.toFloat() / budget).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { fraction },
        color = levelColor(level),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().heightIn(min = 8.dp),
    )
}

@Composable
fun CategoryBudgetRow(line: CategoryLine, onEdit: (() -> Unit)? = null, pace: String? = null, onClick: () -> Unit) {
    val budget = line.budgetMinor ?: 0L
    val remaining = line.remainingMinor ?: 0L
    val desc = "${line.category.name}: ${Money.format(line.spentMinor)} of ${Money.format(budget)}, ${leftOrOver(remaining)}"
    val tint = if (line.category.colorArgb != 0) Color(line.category.colorArgb) else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = com.emotionalleaves.sprout.ui.common.cardBorder(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(
            Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
                .semantics(mergeDescendants = true) { contentDescription = desc },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(CategoryIcons.forKey(line.category.iconKey), tint, 40.dp)
                Spacer(Modifier.width(12.dp))
                // At large text sizes "left" moves under the name, so the name keeps its width.
                val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
                Column(Modifier.weight(1f)) {
                    Text(line.category.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${Money.format(line.spentMinor)} of ${Money.format(budget)}" + carryNote(line),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (large) Text(leftOrOver(remaining), style = MaterialTheme.typography.titleSmall, color = levelColor(line.level))
                }
                if (!large) Text(leftOrOver(remaining), style = MaterialTheme.typography.titleSmall, color = levelColor(line.level))
                if (onEdit != null) {
                    androidx.compose.material3.IconButton(onClick = onEdit) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Edit, contentDescription = "Edit ${line.category.name} budget",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FatProgress(
                if (budget <= 0) (if (line.spentMinor > 0) 1f else 0f) else line.spentMinor.toFloat() / budget,
                levelColor(line.level),
            )
            if (pace != null) Text(pace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The safe-to-spend card. Tapping it shows how the number is worked out. */
@Composable
fun SafeToSpendCard(month: BudgetMonth, onSetBudget: () -> Unit) {
    var showBreakdown by remember { mutableStateOf(false) }
    val safe = month.safeToSpend
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (safe == null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Safe to spend", style = MaterialTheme.typography.labelLarge)
                Text("Set a budget to see what's safe to spend", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onSetBudget, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set up budgets") }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Show breakdown") { showBreakdown = true }.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Safe to spend this month", style = MaterialTheme.typography.labelLarge)
                Text(
                    Money.format(safe.amountMinor),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (safe.amountMinor < 0) LocalSproutColors.current.danger else MaterialTheme.colorScheme.onSurface,
                )
                safe.perDayMinor?.let { Text("≈ ${Money.format(it)}/day", style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "Spent ${Money.format(month.totalSpentBudgetedMinor)} of ${Money.format(month.totalBudgetMinor)} budgeted · tap for details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showBreakdown && safe != null) SafeToSpendBreakdown(month) { showBreakdown = false }
}

/** How safe to spend is worked out, line by line. */
@Composable
fun SafeToSpendBreakdown(month: BudgetMonth, onDismiss: () -> Unit) {
    val safe = month.safeToSpend ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it's worked out") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Left in your budgets: ${Money.format(safe.budgetRemainingMinor)}")
                month.budgeted.forEach { l ->
                    Text("  ${l.category.name}: ${leftOrOver(l.remainingMinor ?: 0)}", style = MaterialTheme.typography.bodySmall)
                }
                if (safe.upcomingUnbudgeted.isNotEmpty()) {
                    Text("Minus bills still due this month (not in a budget):")
                    safe.upcomingUnbudgeted.forEach { ch ->
                        Text("  ${ch.name} on ${Format.dateTime(ch.dueAt).substringBefore(',' )}: −${Money.format(ch.amountMinor)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (safe.plannedMinor > 0) Text("Minus this month's set-aside for planned expenses: −${Money.format(safe.plannedMinor)}")
                Text("= ${Money.format(safe.amountMinor)} safe to spend", style = MaterialTheme.typography.titleMedium)
                safe.daysRemaining?.let { d ->
                    Text("$d day${if (d == 1) "" else "s"} left including today" +
                        (safe.perDayMinor?.let { " ≈ ${Money.format(it)}/day" } ?: ""))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Edit a category's budget: amount plus "This month only" or "Every month". */
@Composable
fun EditBudgetDialog(
    categoryName: String,
    initialMinor: Long?,
    hasBudget: Boolean,
    rolloverOn: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (amountMinor: Long, thisMonthOnly: Boolean, rollover: Boolean) -> Unit,
    onRemove: (thisMonthOnly: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(initialMinor?.let { (it / 100).toString() + if (it % 100 != 0L) "." + "%02d".format(it % 100) else "" } ?: "") }
    var thisMonthOnly by remember { mutableStateOf(false) }
    var roll by remember { mutableStateOf(rolloverOn) }
    val parsed = Money.parseUserInput(text)?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for $categoryName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(12) },
                    label = { Text("Amount per month") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                listOf(false to "Every month", true to "This month only").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(thisMonthOnly == value, role = Role.RadioButton) { thisMonthOnly = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = thisMonthOnly == value, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(roll, role = Role.Switch) { roll = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over leftover money")
                        Text(
                            "Unspent money adds to next month; overspending takes from it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = roll, onCheckedChange = null)
                }
                if (hasBudget) {
                    TextButton(onClick = { onRemove(thisMonthOnly) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (thisMonthOnly) "Remove this month's amount" else "Remove budget")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let { onSave(it, thisMonthOnly, roll) } }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
