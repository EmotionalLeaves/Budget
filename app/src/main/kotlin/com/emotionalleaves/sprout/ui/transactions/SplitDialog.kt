package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.domain.split.SplitPart
import com.emotionalleaves.sprout.domain.split.Splits
import kotlin.math.abs

private class PartDraft(categoryId: Long?, text: String) {
    var categoryId by mutableStateOf(categoryId)
    var text by mutableStateOf(text)
}

private fun typed(minor: Long): String = abs(minor).let { (it / 100).toString() + if (it % 100 != 0L) "." + "%02d".format(it % 100) else "" }

/**
 * Move parts of a transaction to other categories. What isn't moved stays in [ownCategory].
 * [onSave] returns a problem to show, or null when saved.
 */
@Composable
fun SplitDialog(
    amountMinor: Long,
    ownCategory: CategoryEntity?,
    categories: List<CategoryEntity>,
    existing: List<SplitPart>,
    onDismiss: () -> Unit,
    onSave: (List<SplitPart>) -> Unit,
    problem: String?,
) {
    val drafts = remember {
        mutableStateListOf<PartDraft>().apply {
            if (existing.isEmpty()) add(PartDraft(null, "")) else existing.forEach { add(PartDraft(it.categoryId, typed(it.amountMinor))) }
        }
    }
    val choices = categories.filter { it.id != ownCategory?.id }
    val parsed = drafts.map { d -> d.categoryId to (if (d.text.isBlank()) 0L else Money.parseUserInput(d.text)) }
    val complete = parsed.filter { (id, amt) -> id != null && amt != null && amt > 0 }.map { (id, amt) -> SplitPart(id!!, amt!!) }
    val parts = Splits.normalize(amountMinor, complete)
    val localProblem = when {
        parsed.any { (_, amt) -> amt == null || amt < 0 } -> "Check the amounts."
        parsed.any { (id, amt) -> id == null && (amt ?: 0) > 0 } -> "Pick a category for each part."
        else -> Splits.problem(amountMinor, false, ownCategory?.id ?: -1, parts)
    }
    val remainder = Splits.remainder(amountMinor, parts)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${Money.format(abs(amountMinor))}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                drafts.forEachIndexed { i, d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryMenu(choices, d.categoryId, Modifier.weight(1f)) { d.categoryId = it }
                        OutlinedTextField(
                            value = d.text,
                            onValueChange = { d.text = it.take(12) },
                            label = { Text("Amount") },
                            prefix = { Text("$") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp),
                        )
                        TextButton(onClick = { drafts.removeAt(i) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("✕") }
                    }
                }
                if (drafts.size < Splits.MAX_PARTS) {
                    OutlinedButton(onClick = { drafts.add(PartDraft(null, "")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add part") }
                }
                Text(
                    "Stays in ${ownCategory?.name ?: "its category"}: ${Money.format(abs(remainder))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                (localProblem ?: problem)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(complete) }, enabled = localProblem == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryMenu(choices: List<CategoryEntity>, selected: Long?, modifier: Modifier, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(choices.firstOrNull { it.id == selected }?.name ?: "Category", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { onPick(c.id); open = false }) }
        }
    }
}
