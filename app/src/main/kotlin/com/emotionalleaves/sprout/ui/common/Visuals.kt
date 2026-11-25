package com.emotionalleaves.sprout.ui.common

import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.domain.money.Money

/** Privacy mode: amounts on overview screens show as dots until you tap the eye. */
val LocalHideAmounts = staticCompositionLocalOf { false }

@Composable
fun money(minor: Long, whole: Boolean = false): String =
    if (LocalHideAmounts.current) "$ •••" else if (whole) Money.formatWhole(minor) else Money.format(minor)

/** A big rounded card with a gradient background: the one thing on a screen that matters most. */
@Composable
fun HeroCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Side margin; 0 when the screen already pads its content, so the card lines up with everything else. */
    inset: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A soft card for a section. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, inset: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = inset, vertical = 6.dp),
    ) {
        Column(
            Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Section header with an optional action on the right ("See all"). */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

/**
 * Category colors are picked for a light background; on a dark one, deep colors (brown, teal, purple) nearly vanish.
 * In dark themes this lifts any color that's too dark toward white, keeping its hue.
 */
@Composable
fun Color.readable(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (!dark || luminance() >= 0.30f) return this
    return androidx.compose.ui.graphics.lerp(this, Color.White, 0.45f)
}

/** A round tinted badge holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    val t = tint.readable()
    Box(Modifier.size(size).clip(CircleShape).background(t.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = t, modifier = Modifier.size(size * 0.52f))
    }
}

/** Ring that fills to [fraction] (0..1, capped) with an animated sweep. Over 1 shows the [overColor]. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    overColor: Color = color,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            drawArc(if (fraction > 1f) overColor else color, -90f, 360f * anim, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut chart with a short grow-in animation. Decorative: the legend next to it carries the numbers. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, stroke: Dp = 22.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { s ->
                val sweep = 360f * s.value / total * progress.value
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}

/** Simple animated bar chart; the last bar is highlighted. Values are cents, labels short ("Jun"). */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, color: Color, modifier: Modifier = Modifier, highlight: Color = color) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val desc = labels.zip(values).joinToString(", ") { (l, v) -> "$l ${Money.formatWhole(v)}" }
    Column(modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.56f
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / max) * progress.value
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(
                    if (i == values.lastIndex) highlight else color.copy(alpha = 0.45f),
                    Offset(x, size.height - h), Size(bw, h.coerceAtLeast(3f)), CornerRadius(bw / 3, bw / 3),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

/** A small label–value tile for stat rows. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null, noteColor: Color = Color.Unspecified) {
    // fillMaxHeight: tiles in one row share the tallest one's height (the row uses IntrinsicSize.Min).
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = if (noteColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else noteColor, maxLines = 2)
        }
    }
}

/** A thick rounded progress bar with an animated fill. */
@Composable
fun FatProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant, height: Dp = 10.dp) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

/** Legend dot + label + value, one line. */
@Composable
fun LegendRow(color: Color, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 36.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

/** Smooth line with a soft fill below and a dot on the last point. Decorative; pair it with text. */
@Composable
fun LineChart(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val min = values.min().toFloat()
        val max = values.max().toFloat()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val pad = 10.dp.toPx()
        val pts = values.mapIndexed { i, v ->
            Offset(pad + (size.width - 2 * pad) * i / (values.size - 1), pad + (size.height - 2 * pad) * (1f - (v - min) / span))
        }
        val shown = (pts.size * progress.value).toInt().coerceIn(2, pts.size)
        val line = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until shown) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val mid = (p0.x + p1.x) / 2
                cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
            }
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(pts[shown - 1].x, size.height)
            lineTo(pts[0].x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 5.dp.toPx(), pts[shown - 1])
    }
}

/** Money that counts up/down smoothly to a new value (the exact value once it settles). */
@Composable
fun animatedMoney(minor: Long, whole: Boolean = false): String {
    val anim = remember { Animatable(minor.toFloat()) }
    LaunchedEffect(minor) { anim.animateTo(minor.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) kotlin.math.round(anim.value).toLong() else minor
    return money(shown, whole)
}

/** Soft pulsing placeholder blocks while the first data loads. */
@Composable
fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        0.35f, 0.75f,
        androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse",
    )
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a)))
}
