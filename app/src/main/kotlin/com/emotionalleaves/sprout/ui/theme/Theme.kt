package com.emotionalleaves.sprout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emotionalleaves.sprout.R

/** Manrope (SIL OFL), bundled: no font is ever downloaded. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

// Sprout's palette: deep evergreen, warm paper, a teal and an amber accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF17694A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7EBD6),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF2B6773),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8EE),
    onSecondaryContainer = Color(0xFF001F25),
    tertiary = Color(0xFFA35B12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDBF),
    onTertiaryContainer = Color(0xFF2F1500),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFF6F4EE),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFE6E4DB),
    onSurfaceVariant = Color(0xFF55574F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFAF5),
    surfaceContainer = Color(0xFFF1EFE8),
    surfaceContainerHigh = Color(0xFFEBE9E1),
    surfaceContainerHighest = Color(0xFFE4E2DA),
    outline = Color(0xFF86887E),
    outlineVariant = Color(0xFFD3D1C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8AA),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF0F5137),
    onPrimaryContainer = Color(0xFFC7EBD6),
    secondary = Color(0xFF93D0DC),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF0F4E59),
    onSecondaryContainer = Color(0xFFCDE8EE),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00),
    onTertiaryContainer = Color(0xFFFFDDBF),
    background = Color(0xFF0D1210),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF0D1210),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFBEC3BC),
    surfaceContainerLowest = Color(0xFF080C0A),
    surfaceContainerLow = Color(0xFF141A17),
    surfaceContainer = Color(0xFF18201C),
    surfaceContainerHigh = Color(0xFF212924),
    surfaceContainerHighest = Color(0xFF2B332E),
    outline = Color(0xFF8A9089),
    outlineVariant = Color(0xFF3C443F),
)

private fun t(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

val SproutTypography = Typography(
    displayLarge = t(52, FontWeight.ExtraBold, 58, -1.5),
    displayMedium = t(42, FontWeight.ExtraBold, 48, -1.0),
    displaySmall = t(34, FontWeight.ExtraBold, 40, -0.5),
    headlineLarge = t(30, FontWeight.Bold, 36, -0.5),
    headlineMedium = t(26, FontWeight.Bold, 32, -0.25),
    headlineSmall = t(22, FontWeight.Bold, 28),
    titleLarge = t(20, FontWeight.Bold, 26),
    titleMedium = t(16, FontWeight.Bold, 22),
    titleSmall = t(14, FontWeight.SemiBold, 20),
    bodyLarge = t(16, FontWeight.Medium, 24),
    bodyMedium = t(14, FontWeight.Medium, 20),
    bodySmall = t(12, FontWeight.Medium, 16),
    labelLarge = t(14, FontWeight.SemiBold, 20),
    labelMedium = t(12, FontWeight.SemiBold, 16),
    labelSmall = t(11, FontWeight.SemiBold, 16, 0.2),
)

val SproutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colors with meaning. Never the only signal: text always says the same thing. */
data class SproutColors(
    val income: Color,
    val warning: Color,
    val danger: Color,
    val ok: Color,
    /** The hero card's gradient. */
    val hero: Brush,
    val onHero: Color,
    /** Chart series, in order: distinct in hue and lightness, readable on both themes. */
    val chart: List<Color>,
)

private val lightChart = listOf(
    Color(0xFF17694A), Color(0xFF2B6773), Color(0xFFA35B12), Color(0xFF7A4FA0), Color(0xFFB3404A),
    Color(0xFF4F7A1F), Color(0xFF2F5BA8), Color(0xFF8A6D00),
)
private val darkChart = listOf(
    Color(0xFF7DD8AA), Color(0xFF93D0DC), Color(0xFFFFB877), Color(0xFFCDA6F2), Color(0xFFFF9EA6),
    Color(0xFFB4DB7E), Color(0xFF9FBEFF), Color(0xFFE9CF6B),
)

val LocalSproutColors = staticCompositionLocalOf {
    SproutColors(
        Color(0xFF17694A), Color(0xFFA35B12), Color(0xFFB3261E), Color(0xFF17694A),
        Brush.linearGradient(listOf(Color(0xFF0F5137), Color(0xFF17694A))), Color.White, lightChart,
    )
}

@Composable
fun SproutTheme(themeMode: Int = 0, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }.let { s ->
        // "Black": true black surfaces, kind to OLED screens and to your eyes at night.
        if (themeMode == 3) s.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0F0D), surfaceContainer = Color(0xFF101512), surfaceContainerHigh = Color(0xFF161C19),
        ) else s
    }
    val semantic = if (dark) {
        SproutColors(
            income = Color(0xFF7DD8AA), warning = Color(0xFFFFB877), danger = Color(0xFFFF8A80), ok = Color(0xFF7DD8AA),
            hero = Brush.linearGradient(listOf(Color(0xFF0B3D2A), Color(0xFF145F45), Color(0xFF1C6E63))),
            onHero = Color(0xFFEFFFF6), chart = darkChart,
        )
    } else {
        SproutColors(
            income = Color(0xFF17694A), warning = Color(0xFF9A5200), danger = Color(0xFFB3261E), ok = Color(0xFF17694A),
            hero = Brush.linearGradient(listOf(Color(0xFF0F5137), Color(0xFF17694A), Color(0xFF2B7A6E))),
            onHero = Color.White, chart = lightChart,
        )
    }
    CompositionLocalProvider(LocalSproutColors provides semantic) {
        MaterialTheme(colorScheme = scheme, typography = SproutTypography, shapes = SproutShapes, content = content)
    }
}
