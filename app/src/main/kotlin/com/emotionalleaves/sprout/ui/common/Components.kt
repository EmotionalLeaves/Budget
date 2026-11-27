@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.emotionalleaves.sprout.ui.common

import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Spa
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors

const val BRIDGE_URL = "https://bridge.simplefin.org"
const val REPO_URL = "https://github.com/EmotionalLeaves/Budget"

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, inRow: Boolean = false) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        // In a row with buttons, the row supplies the vertical space so the title lines up with them.
        modifier = modifier.padding(start = 20.dp, end = 16.dp, top = if (inRow) 0.dp else 20.dp, bottom = if (inRow) 0.dp else 6.dp).semantics { heading() },
    )
}

/** A warning or info banner. Messages from the server are rendered as plain text only. */
@Composable
fun Banner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    warning: Boolean = true,
) {
    val container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (warning) {
                Icon(Icons.Filled.Warning, contentDescription = "Warning")
            } else {
                Icon(androidx.compose.material.icons.Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 40.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, color: Int, iconKey: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (iconKey != null) {
            Icon(CategoryIcons.forKey(iconKey), contentDescription = null, tint = Color(color).readable(), modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(color).readable()))
        }
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TransactionItem(
    row: TransactionRow,
    /** Lists without day headers show the date in the row. */
    showDate: Boolean = false,
    /** On a merchant's own page the name is the same on every row, so the date leads instead. */
    dateAsTitle: Boolean = false,
    /** Selection mode (Activity): long-press starts it; a selected row gets a tinted background and a check. */
    selected: Boolean = false,
    /** On an account's own page every row is from that account, so the name is left out. */
    hideAccount: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val merchant = com.emotionalleaves.sprout.ui.common.merchantName(row.merchantKey)
    val date = remember(row.occurredAt) {
        java.time.Instant.ofEpochSecond(row.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern(if (dateAsTitle) "EEE, MMM d, yyyy" else "MMM d"))
    }
    val amountText = Format.amount(row.amountMinor)
    val spoken = buildString {
        append(if (dateAsTitle) date else merchant).append(", ").append(Format.amountSpoken(row.amountMinor))
        if (showDate && !dateAsTitle) append(", ").append(date)
        append(", ").append(row.categoryName).append(", ").append(row.accountName)
        if (row.pending) append(", pending")
        if (row.splitCount > 0) append(", split across categories")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick, onLongClickLabel = "Select")
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = spoken + if (selected) ", selected" else ""
                if (onClick != null) this.onClick { onClick(); true }
                if (onLongClick != null) this.onLongClick(label = "Select") { onLongClick(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            IconBadge(CategoryIcons.forKey(row.categoryIcon), Color(row.categoryColor), 42.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Big text: let the name use two lines instead of cutting it short.
            val lines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 2 else 1
            Text(if (dateAsTitle) date else merchant, style = MaterialTheme.typography.titleSmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.categoryName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (row.splitCount > 0) {
                    Text("+${row.splitCount} split", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                }
                if (showDate && !dateAsTitle) Text("· $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (!hideAccount) Text("· " + row.accountName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                amountText,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.amountMinor > 0) LocalSproutColors.current.income else MaterialTheme.colorScheme.onSurface,
            )
            if (row.pending) {
                Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.material.icons.Icons.Rounded.Spa,
    action: (@Composable () -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val second = MaterialTheme.colorScheme.secondaryContainer
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Layered halo: two faint rings around a gradient disc.
        Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(136.dp)) {
                drawCircle(container.copy(alpha = 0.25f))
                drawCircle(primary.copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                drawCircle(container.copy(alpha = 0.45f), size.minDimension * 0.39f)
            }
            Box(
                Modifier.size(84.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(container, second))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(42.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(4.dp))
            action()
        }
    }
}
