package com.emotionalleaves.sprout.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A small hint shown until you close it, then never again ("Swipe right to change a category"). Keys are stable
 * strings; the dismissed set is kept in settings, so tips survive restarts and updates.
 */
@Composable
fun OneTimeTip(key: String, text: String, modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 16.dp) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    // null while loading, so a dismissed tip never flashes on screen.
    val dismissed by androidx.compose.runtime.remember { c.settings.settings.map { key in it.dismissedTips } }.collectAsState(initial = null)
    AnimatedVisibility(visible = dismissed == false, exit = fadeOut() + shrinkVertically()) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = inset, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer).padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp))
            IconButton(onClick = { scope.launch { c.settings.dismissTip(key) } }) {
                Icon(Icons.Rounded.Close, contentDescription = "Got it, hide this tip", tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}
