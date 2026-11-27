package com.emotionalleaves.sprout.ui.common

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.setValue
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

/** True when the current theme is dark (Dark, Black or a dark dynamic scheme). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The hairline edge every card shares: gives pale cards definition on paper, and depth on dark. */
@Composable
fun cardBorder(): BorderStroke =
    BorderStroke(1.dp, if (isDarkTheme()) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

private val HeroShape = RoundedCornerShape(28.dp)

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
    val dark = isDarkTheme()
    val glow = if (dark) Color(0xFF7DD8AA) else Color(0xFF0F5137)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset, vertical = 8.dp)
            .shadow(16.dp, HeroShape, ambientColor = glow.copy(alpha = 0.35f), spotColor = glow.copy(alpha = 0.45f))
            .clip(HeroShape)
            .background(brush)
            .drawBehind { heroDecor(if (dark) 0.7f else 1f) }
            .border(1.dp, Color.White.copy(alpha = 0.10f), HeroShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Soft light on the hero: a glow in two corners, a sheen along the top, and faint rings. Decorative only. */
private fun DrawScope.heroDecor(strength: Float) {
    val w = size.width
    val h = size.height
    val r1 = maxOf(w, h) * 0.62f
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f * strength), Color.Transparent), Offset(w * 0.95f, -h * 0.1f), r1), r1, Offset(w * 0.95f, -h * 0.1f))
    val r2 = maxOf(w, h) * 0.5f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9BE7C4).copy(alpha = 0.16f), Color.Transparent), Offset(w * 0.02f, h * 1.1f), r2), r2, Offset(w * 0.02f, h * 1.1f))
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.45f to Color.Transparent))
    val ring = 1.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.07f), h * 0.95f, Offset(w * 1.02f, -h * 0.05f), style = Stroke(ring))
    drawCircle(Color.White.copy(alpha = 0.05f), h * 0.62f, Offset(w * 1.02f, -h * 0.05f), style = Stroke(ring))
}

/** A small frosted pill on a hero card ("5 days left", "10 bills"). */
@Composable
fun HeroPill(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A soft card for a section. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, inset: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = cardBorder(),
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
    val dark = isDarkTheme()
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
    val shown = growIn(fraction.coerceIn(0f, 1f), 1000)
    val main = if (fraction > 1f) overColor else color
    // The arc starts a shade softer and deepens to full color at its tip. For darker colors the softer shade is opaque,
    // so the start cap never shows the track through it; white (on a hero) fades with alpha instead.
    val dark = isDarkTheme()
    val start = when {
        main.luminance() > 0.8f -> main.copy(alpha = 0.6f)
        dark -> androidx.compose.ui.graphics.lerp(main, Color.Black, 0.3f)
        else -> androidx.compose.ui.graphics.lerp(main, Color.White, 0.4f)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            val sweep = 360f * shown
            if (sweep > 0.5f) {
                val lead = 8f
                val brush = Brush.sweepGradient(
                    0f to start,
                    ((lead + sweep) / 360f).coerceIn(0.02f, 1f) to main,
                    1f to main,
                    center = center,
                )
                rotate(-90f - lead) { drawArc(brush, lead, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round)) }
            }
        }
        content()
    }
}

/**
 * A value that grows in from zero the first time a screen shows it, then glides to new values.
 * Remembered across scrolling, so a list doesn't replay the animation every time a row comes back into view.
 */
@Composable
private fun growIn(target: Float, durationMs: Int): Float {
    var done by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val anim = remember { Animatable(if (done) target else 0f) }
    LaunchedEffect(target) {
        anim.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
        done = true
    }
    return anim.value
}

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut chart with a short grow-in animation. Decorative: the legend next to it carries the numbers. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, stroke: Dp = 22.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val inner = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            // A soft disc in the middle so the total sits on its own plate.
            drawCircle(inner, d / 2 - w / 2 - 6.dp.toPx(), center)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            var start = -90f
            val gap = if (slices.size > 1) 3f else 0f
            slices.forEach { s ->
                val sweep = 360f * s.value / total * progress.value
                if (sweep > gap) {
                    drawArc(
                        Brush.radialGradient(listOf(s.color.copy(alpha = 0.82f), s.color), center, d / 2 + w / 2),
                        start + gap / 2, sweep - gap, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        content()
    }
}

/** A dashed guide line for charts. */
private fun DrawScope.guide(y: Float, color: Color, dashed: Boolean = true) {
    drawLine(
        color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())) else null,
    )
}

/** A bar with rounded top corners and a square foot, so bars sit on the baseline. */
private fun DrawScope.topRoundedBar(x: Float, top: Float, width: Float, bottom: Float, brush: Brush) {
    val r = (width / 3).coerceAtMost((bottom - top).coerceAtLeast(0f))
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(RoundRect(Rect(x, top, x + width, bottom), CornerRadius(r, r), CornerRadius(r, r), CornerRadius.Zero, CornerRadius.Zero))
    }
    drawPath(path, brush)
}

/**
 * Animated bar chart; the last bar is highlighted and labelled with its amount. Values are cents, labels short ("Jun").
 * [average], when given, draws a dashed line at that level.
 */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, color: Color, modifier: Modifier = Modifier, highlight: Color = color, average: Long? = null) {
    val max = (listOfNotNull(values.maxOrNull(), average).maxOrNull() ?: 0L).coerceAtLeast(1)
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val desc = labels.zip(values).joinToString(", ") { (l, v) -> "$l ${Money.formatWhole(v)}" }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val tm = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = highlight.readable(), fontWeight = FontWeight.Bold)
    val lastText = money(values.lastOrNull() ?: 0L, whole = true)
    val hl = highlight.readable()
    val base = color.readable()
    Column(modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val measured = tm.measure(lastText, valueStyle)
            val top = measured.size.height + 6.dp.toPx()
            val chartH = size.height - top
            for (k in 0..2) guide(top + chartH * k / 3f, grid.copy(alpha = 0.7f))
            guide(size.height - 0.5.dp.toPx(), grid, dashed = false)
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = (slot * 0.56f).coerceAtMost(44.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (chartH * (v.toFloat() / max) * progress.value).coerceAtLeast(3f)
                val x = i * slot + (slot - bw) / 2
                val y = size.height - h
                val last = i == values.lastIndex
                val brush = if (last) Brush.verticalGradient(listOf(hl, hl.copy(alpha = 0.72f)), y, size.height)
                else Brush.verticalGradient(listOf(base.copy(alpha = 0.42f), base.copy(alpha = 0.16f)), y, size.height)
                topRoundedBar(x, y, bw, size.height, brush)
                if (last && progress.value > 0.6f) {
                    val tx = (x + bw / 2 - measured.size.width / 2).coerceIn(0f, size.width - measured.size.width)
                    drawText(measured, topLeft = Offset(tx, y - measured.size.height - 3.dp.toPx()), alpha = ((progress.value - 0.6f) / 0.4f).coerceIn(0f, 1f))
                }
            }
            if (average != null && average > 0) {
                val y = size.height - chartH * (average.toFloat() / max)
                drawLine(hl.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                val last = i == labels.lastIndex
                Text(
                    l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = if (last) hl else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (last) FontWeight.ExtraBold else null,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/** A small label–value tile for stat rows. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null, noteColor: Color = Color.Unspecified) {
    // fillMaxHeight: tiles in one row share the tallest one's height (the row uses IntrinsicSize.Min).
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = cardBorder(), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = if (noteColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else noteColor, maxLines = 2)
        }
    }
}

/** A thick rounded progress bar with an animated fill that brightens toward its end. */
@Composable
fun FatProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant, height: Dp = 10.dp) {
    val shown = growIn(fraction.coerceIn(0f, 1f), 800)
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        if (shown > 0f) {
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
            )
        }
    }
}

/** Legend dot + label + value, one line. */
@Composable
fun LegendRow(color: Color, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 36.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 12.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Smooth line with a soft fill below, faint guides and a glowing dot on the last point. Pair it with text.
 * With [onScrub], touching and dragging picks a point (shown with a guide line) and reports its index; null on release.
 */
@Composable
fun LineChart(values: List<Long>, color: Color, modifier: Modifier = Modifier, onScrub: ((Int?) -> Unit)? = null) {
    if (values.size < 2) return
    var scrub by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.surfaceContainerLow
    val scrubMod = if (onScrub == null) Modifier else Modifier.pointerInput(values) {
        fun pick(x: Float): Int {
            val pad = 10.dp.toPx()
            val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
            return kotlin.math.round(f * (values.size - 1)).toInt()
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            scrub = pick(down.position.x); onScrub(scrub)
            do {
                val e = awaitPointerEvent()
                e.changes.firstOrNull()?.let { ch ->
                    if (ch.pressed) { val i = pick(ch.position.x); if (i != scrub) { scrub = i; onScrub(i) }; ch.consume() }
                }
            } while (e.changes.any { it.pressed })
            scrub = null; onScrub(null)
        }
    }
    Canvas(modifier.fillMaxWidth().height(160.dp).then(scrubMod)) {
        val min = values.min().toFloat()
        val max = values.max().toFloat()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val pad = 10.dp.toPx()
        for (k in 0..3) guide(pad + (size.height - 2 * pad) * k / 3f, grid.copy(alpha = 0.6f))
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
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.04f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.18f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val end = scrub?.let { pts.getOrNull(it) } ?: pts[shown - 1]
        if (scrub != null) drawLine(color.copy(alpha = 0.5f), Offset(end.x, 0f), Offset(end.x, size.height), 1.5.dp.toPx())
        drawCircle(color.copy(alpha = 0.2f), 11.dp.toPx(), end)
        drawCircle(color, 6.dp.toPx(), end)
        drawCircle(dot, 2.5.dp.toPx(), end)
    }
}

/**
 * Money that counts up smoothly: from zero the first time a screen shows it, then from the old value to a new one.
 * Shows the exact value once it settles.
 */
@Composable
fun animatedMoney(minor: Long, whole: Boolean = false): String {
    var counted by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val anim = remember { Animatable(if (counted) minor.toFloat() else 0f) }
    LaunchedEffect(minor) {
        anim.animateTo(minor.toFloat(), tween(if (counted) 700 else 1100, easing = FastOutSlowInEasing))
        counted = true
    }
    val shown = if (anim.isRunning || !counted) kotlin.math.round(anim.value).toLong() else minor
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

/**
 * What a screen shows while its numbers load: nothing for the first moment (most loads are instant, and a flash
 * of grey looks worse than a blank), then a calm skeleton of a hero card and a few rows.
 */
@Composable
fun LoadingSkeleton(rows: Int = 3) {
    var show by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(250); show = true }
    androidx.compose.animation.AnimatedVisibility(show, enter = androidx.compose.animation.fadeIn(tween(300))) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "Loading" }) {
            SkeletonBlock(160.dp)
            repeat(rows) { SkeletonBlock(64.dp) }
        }
    }
}

/** Inside a [TileRow]: the modifier each tile takes (shares the row, or the full width when stacked). */
interface TileScope { fun Modifier.tile(): Modifier }

/**
 * A row of stat tiles that stacks into a column at large text sizes, so "$7,775" is never cut to "$7,7…".
 * In a row the tiles share the tallest one's height.
 */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable TileScope.() -> Unit) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    if (large) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val scope = object : TileScope { override fun Modifier.tile() = this.fillMaxWidth() }
            scope.content()
        }
    } else {
        Row(modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val row = this
            val scope = object : TileScope { override fun Modifier.tile() = with(row) { this@tile.weight(1f) } }
            scope.content()
        }
    }
}
