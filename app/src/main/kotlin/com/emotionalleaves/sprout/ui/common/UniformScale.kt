package com.emotionalleaves.sprout.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Sprout is laid out for a 411 dp wide phone (a typical 1080-pixel screen at the default display size). On a
 * narrower or wider phone everything is scaled by the same factor, so cards, charts and text keep the same
 * proportions and the screen looks the same as it does on that phone, instead of wrapping or leaving gaps.
 * Your font size setting still applies on top. Limited to 85–125% so tablets and tiny screens stay usable.
 */
const val DESIGN_WIDTH_DP = 411f

fun uniformScaleFactor(smallestWidthDp: Int): Float =
    if (smallestWidthDp <= 0) 1f else (smallestWidthDp / DESIGN_WIDTH_DP).coerceIn(0.85f, 1.25f)

@Composable
fun UniformScale(enabled: Boolean = true, content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    // The smaller side, so turning the phone doesn't change the scale.
    val factor = if (enabled) uniformScaleFactor(minOf(config.screenWidthDp, config.screenHeightDp)) else 1f
    if (factor == 1f) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density * factor, density.fontScale), content = content)
    }
}
