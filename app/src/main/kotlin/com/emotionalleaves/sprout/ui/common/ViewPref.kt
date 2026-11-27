package com.emotionalleaves.sprout.ui.common

import androidx.compose.runtime.Composable
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
