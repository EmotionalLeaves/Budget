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
