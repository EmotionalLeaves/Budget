package com.emotionalleaves.sprout.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.SproutApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as SproutApp).container

/** Creates a ViewModel that gets the app container (manual DI). */
@Composable
inline fun <reified VM : ViewModel> sproutViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}


/** Navigate to a route from deep inside a screen (sheets, cards). No-op outside the main scaffold. */
val LocalNavigate = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }

/** Loads a value off the main thread when [keys] change; null until loaded. */
@androidx.compose.runtime.Composable
fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T): T? {
    val state = androidx.compose.runtime.remember(*keys) { androidx.compose.runtime.mutableStateOf<T?>(null) }
    androidx.compose.runtime.LaunchedEffect(*keys) { state.value = load() }
    return state.value
}
