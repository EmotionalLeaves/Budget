package com.emotionalleaves.sprout.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.emotionalleaves.sprout.ui.common.readable
import com.emotionalleaves.sprout.ui.common.CategoryIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.db.CategoryEntity

/** A wrap-around grid of category buttons. The selected one has a border and is announced as selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(categories: List<CategoryEntity>, selectedId: Long?, onPick: (CategoryEntity) -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        categories.forEach { c ->
            val selected = c.id == selectedId
            val readable = Color(c.colorArgb).readable()
            // A light wash of the category's own colour, so the grid reads at a glance; pressing gives a little squeeze.
            val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.94f else 1f, label = "chip")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .heightIn(min = 48.dp)
                    .widthIn(min = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .background(readable.copy(alpha = if (selected) 0.28f else 0.10f))
                    .then(if (selected) Modifier.border(2.dp, readable, RoundedCornerShape(14.dp)) else Modifier)
                    .semantics { this.selected = selected }
                    .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onPick(c)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(CategoryIcons.forKey(c.iconKey), contentDescription = null, tint = readable, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A row of color swatches for picking a category color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, color ->
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(if (color == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .semantics { this.selected = color == selected }
                    .clickable(role = Role.RadioButton, onClickLabel = "Color ${i + 1}") { onPick(color) },
            )
        }
    }
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}
