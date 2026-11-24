package com.emotionalleaves.sprout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E6B0),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF52634F),
    background = Color(0xFFF6FBF3),
    surface = Color(0xFFF6FBF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD594),
    onPrimary = Color(0xFF003911),
    primaryContainer = Color(0xFF13521B),
    onPrimaryContainer = Color(0xFFB8F1AF),
    secondary = Color(0xFFB9CCB4),
    background = Color(0xFF101510),
    surface = Color(0xFF101510),
)

/** Colors with meaning. Never the only signal: text always says the same thing. */
data class SproutColors(val income: Color, val warning: Color, val danger: Color, val ok: Color)

val LocalSproutColors = staticCompositionLocalOf {
    SproutColors(Color(0xFF2E7D32), Color(0xFFB26A00), Color(0xFFC62828), Color(0xFF2E7D32))
}

@Composable
fun SproutTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val semantic = if (dark) {
        SproutColors(income = Color(0xFF81C784), warning = Color(0xFFFFB74D), danger = Color(0xFFEF9A9A), ok = Color(0xFF81C784))
    } else {
        SproutColors(income = Color(0xFF2E7D32), warning = Color(0xFFB26A00), danger = Color(0xFFC62828), ok = Color(0xFF2E7D32))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSproutColors provides semantic) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
