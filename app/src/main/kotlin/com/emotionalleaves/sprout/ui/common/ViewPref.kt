package com.emotionalleaves.sprout.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A view choice the app remembers across launches (which period, which tab, list or calendar).
 * Setting [value] saves it; reading it never blocks: the default shows until the saved choice loads.
 */
private class PersistedState<T>(private val inner: MutableState<T>, private val save: (T) -> Unit) : MutableState<T> by inner {
    override var value: T
        get() = inner.value
        set(v) { if (v != inner.value) { inner.value = v; save(v) } }
}

@Composable
fun rememberViewPref(key: String, default: String): MutableState<String> =
    rememberViewPref(key, default, { it }, { it })

@Composable
fun <T : Enum<T>> rememberViewPref(key: String, default: T, values: Array<T>): MutableState<T> =
    rememberViewPref(key, default, { it.name }, { n -> values.firstOrNull { it.name == n } })

@Composable
fun <T : Any> rememberViewPref(key: String, default: T, encode: (T) -> String, decode: (String) -> T?): MutableState<T> {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val inner = rememberSaveable(key) { mutableStateOf(default) }
    val loaded = rememberSaveable(key + "#loaded") { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!loaded.value) {
            runCatching { c.settings.viewPref(key).first() }.getOrNull()?.let(decode)?.let { inner.value = it }
            loaded.value = true
        }
    }
    return PersistedState(inner) { v -> scope.launch { runCatching { c.settings.setViewPref(key, encode(v)) } } }
}

/**
 * A single choice among a few short labels: one segmented row at normal text sizes, wrapping chips with
 * large text, so no label is ever cut off ("Mont").
 */
@Composable
fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
              inset: androidx.compose.ui.unit.Dp = 16.dp) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
        // Wrap rather than scroll: every choice stays visible, even inside a card.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth().padding(horizontal = inset),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l ->
                androidx.compose.material3.FilterChip(selected = i == selected, onClick = { onSelect(i) },
                    label = { androidx.compose.material3.Text(l, maxLines = 1) },
                    modifier = androidx.compose.ui.Modifier.heightIn(min = 48.dp))
            }
        }
    } else {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = inset)) {
            // Four or more segments: no check mark and smaller text, so every label fits.
            val tight = labels.size >= 4
            labels.forEachIndexed { i, l ->
                val shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, labels.size)
                if (tight) SegmentedButton(i == selected, { onSelect(i) }, shape, icon = {}) {
                    androidx.compose.material3.Text(l, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                } else SegmentedButton(i == selected, { onSelect(i) }, shape) {
                    androidx.compose.material3.Text(l, maxLines = 1)
                }
            }
        }
    }
}
