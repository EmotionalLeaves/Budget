package com.emotionalleaves.sprout.ui.stats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.domain.insights.SpendingStats
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.common.merchantName
import com.emotionalleaves.sprout.ui.common.readable
import com.emotionalleaves.sprout.ui.common.rememberLoaded
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** 53 weeks, so the heatmap always shows a full year of columns. */
private const val HEAT_DAYS = 53 * 7

/** The time range the Stats screen looks at. */
enum class StatsPeriod(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), THREE_MONTHS("3 months"), YEAR("12 months"), ALL("All time") }

private enum class StatsView(val label: String) { OVERVIEW("Overview"), FLOW("Flow"), PATTERNS("Patterns"), PLACES("Places"), TRENDS("Trends") }

private class StatsData(
    val now: SpendingStats.Summary,
    val before: SpendingStats.Summary?,
    val trends: Map<Long, List<Long>>,
    val trendMonths: List<YearMonth>,
    val categories: Map<Long, Pair<String, Int>>,
    val inProgress: Boolean,
    val daysInPeriod: Int,
    /** Last 371 days (53 full weeks), oldest first, ending today. */
    val heat: List<Long>,
    val heatEnd: LocalDate,
    val inOut: List<Pair<Long, Long>>,
    /** Days of the heatmap since your first transaction (the rest is before Sprout had data). */
    val heatDataDays: Int,
    /** Set when your data starts inside the period, so the hero can say "since Jul 3". */
    val dataSince: LocalDate? = null,
)

/** Stats: numbers and views of your spending beyond budgets: patterns, places and trends. */
@Composable
fun StatsScreen(onBack: () -> Unit, onMerchant: (String) -> Unit = {}) {
    val c = appContainer()
    var period by com.emotionalleaves.sprout.ui.common.rememberViewPref("stats-period", StatsPeriod.THIS_MONTH, StatsPeriod.values())
    var view by com.emotionalleaves.sprout.ui.common.rememberViewPref("stats-view", StatsView.OVERVIEW, StatsView.values())
    val zone = ZoneId.systemDefault()
    val data = rememberLoaded(period) {
        runCatching {
            val today = LocalDate.now(zone)
            val month = YearMonth.from(today)
            val (start, end) = when (period) {
                StatsPeriod.THIS_MONTH -> month.atDay(1) to month.plusMonths(1).atDay(1)
                StatsPeriod.LAST_MONTH -> month.minusMonths(1).atDay(1) to month.atDay(1)
                StatsPeriod.THREE_MONTHS -> month.minusMonths(2).atDay(1) to month.plusMonths(1).atDay(1)
                StatsPeriod.YEAR -> month.minusMonths(11).atDay(1) to month.plusMonths(1).atDay(1)
                StatsPeriod.ALL -> {
                    val oldest = c.db.transactions().oldestSynced()
                    val first = oldest?.let { java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDate().withDayOfMonth(1) } ?: month.minusMonths(11).atDay(1)
                    first to month.plusMonths(1).atDay(1)
                }
            }
            val prevStart = if (period == StatsPeriod.ALL) null else when (period) {
                StatsPeriod.THIS_MONTH, StatsPeriod.LAST_MONTH -> YearMonth.from(start).minusMonths(1).atDay(1)
                StatsPeriod.THREE_MONTHS -> YearMonth.from(start).minusMonths(3).atDay(1)
                else -> YearMonth.from(start).minusMonths(12).atDay(1)
            }
            val loadFrom = minOf(prevStart ?: start, month.minusMonths(11).atDay(1), today.minusDays(HEAT_DAYS.toLong()))
            val rows = c.db.budgets().statsRows(loadFrom.atStartOfDay(zone).toEpochSecond(), end.atStartOfDay(zone).toEpochSecond())
                .map { SpendingStats.Row(it.amountMinor, it.occurredAt, it.categoryId, it.kind, it.merchantKey) }
            val firstDay = c.db.budgets().firstOccurredAt()?.let { java.time.Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
            // Averages and no-spend days count only from your first transaction, not from before Sprout had data.
            val effStart = firstDay?.takeIf { it > start && it < end } ?: start
            val now = SpendingStats.summarize(rows, effStart, end, today, zone, top = 30)
            // The previous period, compared like for like: the same number of days into it.
            val before = prevStart?.let { ps ->
                val prevToday = ps.plusDays((now.days - 1).toLong().coerceAtLeast(0))
                SpendingStats.summarize(rows, ps, start, prevToday, zone)
            }
            // Trends start at your first month of data (up to 12), so months before Sprout had anything don't
            // show as zeros and drag every "usually" down.
            val trendCount = firstDay?.let { (java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(it), month) + 1).toInt() }
                ?.coerceIn(2, 12) ?: 12
            val trendMonths = (trendCount - 1 downTo 0).map { month.minusMonths(it.toLong()) }
            StatsData(
                now = now, before = before,
                trends = SpendingStats.categoryTrends(rows, month, trendCount, zone),
                trendMonths = trendMonths,
                categories = c.db.categories().getAll().associate { it.id to (it.name to it.colorArgb) },
                inProgress = today < end && today >= start,
                daysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(effStart, end).toInt(),
                heat = SpendingStats.dailySpending(rows, today, HEAT_DAYS, zone),
                heatEnd = today,
                inOut = SpendingStats.monthlyInOut(rows, month, trendCount, zone),
                dataSince = effStart.takeIf { it != start },
                heatDataDays = firstDay?.let { (java.time.temporal.ChronoUnit.DAYS.between(it, today) + 1).toInt() }?.coerceIn(1, HEAT_DAYS) ?: HEAT_DAYS,
            )
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Stats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p -> FilterChip(selected = p == period, onClick = { period = p }, label = { Text(p.label) }, modifier = Modifier.heightIn(min = 40.dp)) }
        }
        com.emotionalleaves.sprout.ui.common.ChoiceRow(StatsView.entries.map { it.label }, StatsView.entries.indexOf(view),
            { view = StatsView.entries[it] }, Modifier.padding(vertical = 8.dp))
        val d = data
        if (d == null) {
            com.emotionalleaves.sprout.ui.common.LoadingSkeleton()
            return@Column
        }
        if (d.now.purchases == 0 && d.now.earned == 0L && d.now.spent == 0L) {
            com.emotionalleaves.sprout.ui.common.EmptyState(
                "Nothing in ${period.label.lowercase()}",
                "Stats fill in from your synced or imported transactions.",
                icon = androidx.compose.material.icons.Icons.Rounded.BarChart,
            ) {
                val hasData = com.emotionalleaves.sprout.ui.common.rememberHasData()
                if (hasData == false) com.emotionalleaves.sprout.ui.common.GetDataActions()
                else if (hasData == true && period != StatsPeriod.YEAR && period != StatsPeriod.ALL) {
                    androidx.compose.material3.OutlinedButton(onClick = { period = StatsPeriod.YEAR }) { Text("Look at 12 months") }
                }
            }
            return@Column
        }
        AnimatedContent(targetState = view, label = "stats-view") { v ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (v) {
                    StatsView.OVERVIEW -> overview(d, period)
                    StatsView.FLOW -> flow(d)
                    StatsView.PATTERNS -> patterns(d)
                    StatsView.PLACES -> places(d, onMerchant)
                    StatsView.TRENDS -> trends(d)
                }
            }
        }
    }
}

// ---- Overview -----------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.overview(d: StatsData, period: StatsPeriod) {
    val s = d.now
    item { OverviewHero(d, period) }
    item {
        StatGrid(listOf(
            "Daily average" to Money.format(s.dailyAverage),
            "Purchases" to "${s.purchases}",
            "Typical purchase" to Money.format(s.medianPurchase),
            "Average purchase" to Money.format(s.averagePurchase),
            "No-spend days" to "${s.noSpendDays} of ${s.days}",
            "Savings rate" to (s.savingsRate?.let { "$it%" } ?: "—"),
        ))
    }
    s.biggest?.let { b ->
        item {
            Card {
                Label("Biggest purchase")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(merchantName(b.merchantKey), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(-b.amountMinor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(com.emotionalleaves.sprout.ui.common.Format.dateTime(b.occurredAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (s.cumulative.size > 1) {
        item {
            Card {
                Label("Spending so far vs. the period before")
                Spacer(Modifier.height(8.dp))
                val sc = LocalSproutColors.current
                CumulativeChart(s.cumulative, d.before?.cumulative, sc.chart.first(), MaterialTheme.colorScheme.outline,
                    Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Cumulative spending chart" })
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    LegendDot(sc.chart.first(), "This period")
                    if (d.before != null) LegendDot(MaterialTheme.colorScheme.outline, "Before")
                }
            }
        }
    }
}

@Composable
private fun OverviewHero(d: StatsData, period: StatsPeriod) {
    val s = d.now
    val sc = LocalSproutColors.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(sc.hero).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Spent · ${period.label.lowercase()}" + (d.dataSince?.let { " · since " + it.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)) } ?: ""), style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.85f))
            val anim = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(s.spent) { anim.animateTo(s.spent.toFloat(), tween(800)) }
            Text(Money.format(if (anim.isRunning) kotlin.math.round(anim.value).toLong() else s.spent),
                style = MaterialTheme.typography.displaySmall, color = sc.onHero, fontWeight = FontWeight.SemiBold)
            val change = d.before?.let { SpendingStats.changePercent(it.spent, s.spent) }
            if (change != null) {
                val up = change > 0
                Text(
                    "${if (up) "▲" else "▼"} ${abs(change)}% vs. the same point last ${if (period == StatsPeriod.YEAR) "year" else "period"}",
                    style = MaterialTheme.typography.bodyMedium, color = sc.onHero,
                )
            }
            if (d.inProgress && s.days in 1 until d.daysInPeriod) {
                val projected = s.spent * d.daysInPeriod / s.days
                Text("On pace for ${Money.format(projected)} by the end", style = MaterialTheme.typography.bodyMedium, color = sc.onHero.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroFigure("Earned", Money.format(s.earned), sc.onHero)
                HeroFigure("Net", Money.format(s.net, showPlus = true), sc.onHero)
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Flow ---------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.flow(d: StatsData) {
    val (sources, sinks) = SpendingStats.flows(d.now)
    item {
        Card {
            Label("Where your money went")
            val kept = sinks.firstOrNull { it.label == "Kept" }?.amount
            Text(
                when {
                    sources.isEmpty() && sinks.isEmpty() -> "Nothing came in or went out yet"
                    kept != null -> "You kept ${Money.format(kept)} of what came in"
                    sources.any { it.label == "From savings" } -> "You spent ${Money.format(sources.last().amount)} more than came in"
                    else -> "Every dollar that came in went out"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            if (sinks.isNotEmpty()) MoneyFlowChart(sources, sinks, d.categories)
        }
    }
    item {
        Text("Bands are drawn to scale: the thicker the band, the more money took that path. Transfers between your accounts aren't counted.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** A Sankey diagram: income (and savings, if you dipped in) on the left, flowing to categories and "Kept" on the right. */
@Composable
private fun MoneyFlowChart(sources: List<SpendingStats.Flow>, sinks: List<SpendingStats.Flow>, categories: Map<Long, Pair<String, Int>>) {
    val sc = LocalSproutColors.current
    val outline = MaterialTheme.colorScheme.outline
    val keptColor = sc.income
    val savingsColor = sc.warning
    val colors = sinks.mapIndexed { i, f ->
        when {
            f.label == "Kept" -> keptColor
            f.categoryId != null -> categories[f.categoryId]?.second?.let { Color(it).readable() } ?: sc.chart[i % sc.chart.size]
            else -> outline
        }
    }
    fun sinkLabel(f: SpendingStats.Flow) = f.categoryId?.let { categories[it]?.first } ?: f.label
    val total = sinks.sumOf { it.amount }.coerceAtLeast(1)
    val grow by animateFloatAsState(1f, tween(900), label = "flow")
    val gap = 6.dp
    val rowH = 34.dp
    val height = rowH * sinks.size + gap * (sinks.size - 1)
    Row(Modifier.fillMaxWidth()) {
        Canvas(Modifier.weight(1f).height(height).semantics {
            contentDescription = sinks.joinToString { "${sinkLabel(it)} ${Money.format(it.amount)}" }
        }) {
            val gapPx = gap.toPx()
            val usable = size.height - gapPx * (sinks.size - 1)
            val nodeW = 10.dp.toPx()
            // Left: one column of source nodes stacked; right: one node per sink, evenly spaced.
            var sy = 0f
            val srcTotal = sources.sumOf { it.amount }.coerceAtLeast(1)
            val srcRects = sources.map { f ->
                val h = size.height * f.amount / srcTotal
                val r = sy to (sy + h); sy += h; r
            }
            sources.forEachIndexed { i, f ->
                val (a, b) = srcRects[i]
                drawRoundRect(if (f.label == "From savings") savingsColor else keptColor, Offset(0f, a), Size(nodeW, (b - a).coerceAtLeast(2f)), CornerRadius(4f, 4f))
            }
            // Band thickness on the left follows each sink's share of all money; the right end fills its row.
            var leftY = 0f
            val rowPx = rowH.toPx()
            sinks.forEachIndexed { i, f ->
                val thick = size.height * f.amount / total
                val rightTop = i * (rowPx + gapPx)
                val rightThick = maxOf(rowPx * 0.25f, minOf(rowPx, thick * (usable / size.height)))
                val rTop = rightTop + (rowPx - rightThick) / 2
                val x0 = nodeW
                val x1 = (size.width - nodeW) * grow + nodeW * (1 - grow)
                val cx = (x0 + x1) / 2
                val c = colors[i]
                val path = Path().apply {
                    moveTo(x0, leftY)
                    cubicTo(cx, leftY, cx, rTop, x1, rTop)
                    lineTo(x1, rTop + rightThick)
                    cubicTo(cx, rTop + rightThick, cx, leftY + thick, x0, leftY + thick)
                    close()
                }
                drawPath(path, Brush.horizontalGradient(listOf(c.copy(alpha = 0.18f), c.copy(alpha = 0.55f)), startX = x0, endX = x1))
                drawRoundRect(c, Offset(size.width - nodeW, rTop), Size(nodeW, rightThick), CornerRadius(4f, 4f))
                leftY += thick
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            sinks.forEachIndexed { i, f ->
                Column(Modifier.height(rowH), verticalArrangement = Arrangement.Center) {
                    Text(sinkLabel(f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (f.label == "Kept") keptColor else MaterialTheme.colorScheme.onSurface)
                    Text("${Money.format(f.amount)} · ${f.amount * 100 / total}%", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
        sources.forEach { LegendDot(if (it.label == "From savings") savingsColor else keptColor, "${it.label} ${Money.format(it.amount)}") }
    }
}

// ---- Patterns -----------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.patterns(d: StatsData) {
    val s = d.now
    item {
        Card {
            Label("Your year, day by day")
            val active = d.heat.count { it > 0 }
            Text("You spent something on $active of the last ${d.heatDataDays} days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            YearHeatmap(d.heat, d.heatEnd, Modifier.fillMaxWidth())
        }
    }
    item {
        Card {
            Label("By day of the week")
            s.busiestWeekday?.let { Text("You spend most on ${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}s", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(12.dp))
            val labels = java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
            Bars(s.byWeekday, labels, highlight = s.byWeekday.indexOf(s.byWeekday.maxOrNull() ?: -1))
        }
    }
    item {
        Card {
            Label("Through the month")
            val parts = listOf("1st–10th", "11th–20th", "21st–end")
            val max = s.byMonthPart.maxOrNull() ?: 0
            Text(if (max == 0L) "No spending yet" else "Heaviest: the ${parts[s.byMonthPart.indexOf(max)]}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Bars(s.byMonthPart, parts, highlight = s.byMonthPart.indexOf(max))
        }
    }
    item {
        Card {
            Label("Purchase sizes")
            Spacer(Modifier.height(8.dp))
            val total = s.sizes.sumOf { it.total }.coerceAtLeast(1)
            val sc = LocalSproutColors.current
            s.sizes.forEachIndexed { i, b ->
                ShareRow(b.label, "${b.count} · ${Money.format(b.total)}", b.total.toFloat() / total, sc.chart[i % sc.chart.size])
            }
            Text("Small buys add up: count and total side by side show where the money really goes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---- Places -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.places(d: StatsData, onMerchant: (String) -> Unit) {
    item {
        var byVisits by rememberSaveable { mutableStateOf(false) }
        var showAll by rememberSaveable { mutableStateOf(false) }
        val full = if (byVisits) d.now.topByVisits else d.now.topByTotal
        val list = if (showAll) full else full.take(8)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Top places", Modifier.weight(1f))
                FilterChip(selected = !byVisits, onClick = { byVisits = false }, label = { Text("Most spent") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = byVisits, onClick = { byVisits = true }, label = { Text("Most visits") })
            }
            if (list.isEmpty()) Text("No purchases in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = list.maxOfOrNull { if (byVisits) it.visits.toLong() else it.total }?.coerceAtLeast(1) ?: 1
            val sc = LocalSproutColors.current
            list.forEachIndexed { i, p ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onMerchant(p.merchantKey) }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Text(merchantName(p.merchantKey), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (byVisits) "${p.visits}×" else Money.format(p.total), style = MaterialTheme.typography.titleSmall)
                    }
                    val share = if (d.now.spent > 0) (p.total * 100 / d.now.spent).toInt() else 0
                    Text((if (p.visits == 1) "1 visit" else "${p.visits} visits · ${Money.format(p.perVisit)} each on average") +
                        (if (share >= 1) " · $share% of spending" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp))
                    Meter((if (byVisits) p.visits.toLong() else p.total).toFloat() / max, sc.chart[i % sc.chart.size], Modifier.padding(start = 24.dp, top = 6.dp))
                }
            }
            if (full.size > 8) {
                androidx.compose.material3.TextButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showAll) "Show top 8" else "Show all ${full.size}")
                }
            }
        }
    }
}

// ---- Trends -------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.trends(d: StatsData) {
    val rows = d.trends.entries.filter { it.value.sum() > 0 && d.categories.containsKey(it.key) }.sortedByDescending { it.value.sum() }
    item {
        Card {
            Label("Money in vs. out, by month")
            val kept = d.inOut.sumOf { it.first - it.second }
            val n = d.trendMonths.size
            Text(if (kept >= 0) "You kept ${Money.format(kept)} over $n months" else "You spent ${Money.format(-kept)} more than came in over $n months",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            InOutBars(d.inOut, d.trendMonths)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                LegendDot(LocalSproutColors.current.income, "In")
                LegendDot(LocalSproutColors.current.chart.let { it[2 % it.size] }, "Out")
            }
        }
    }
    item {
        Card {
            Label("${d.trendMonths.size} months by category")
            Text("Each line is a category's monthly spending; the dot is this month. Tap one to open it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (rows.isEmpty()) item { Text("Nothing to show yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    rows.forEach { (id, values) ->
        item(key = id) {
            val (name, color) = d.categories.getValue(id)
            val col = Color(color).readable()
            val avg = values.dropLast(1).filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
            val nowV = values.last()
            // Tap a category's trend to open it.
            val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
            Card(onClick = { navigate("category/$id") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(nowV), style = MaterialTheme.typography.titleSmall)
                }
                Spark(values, col, Modifier.fillMaxWidth().height(44.dp).padding(vertical = 6.dp))
                val change = SpendingStats.changePercent(avg, nowV)
                Text(
                    if (avg == 0L) "New this month · nothing here in the months before"
                    else "Usually ${Money.format(avg)} a month" + (change?.let {
                        if (abs(it) < 3) " · about the same this month" else " · this month ${if (it > 0) "+" else ""}$it%"
                    } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Building blocks ----------------------------------------------------------------------------------

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val perRow = if (large) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                if (row.size < perRow) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
    }
}

@Composable
private fun ShareRow(label: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Meter(fraction, color, Modifier.padding(top = 4.dp))
    }
}

/** Rounded vertical bars with labels; [highlight] gets the accent colour, the rest a softer tint. */
@Composable
private fun Bars(values: List<Long>, labels: List<String>, highlight: Int) {
    val sc = LocalSproutColors.current
    val accent = sc.chart.first()
    val soft = accent.copy(alpha = 0.35f)
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val grow by animateFloatAsState(1f, tween(700), label = "bars")
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = labels.zip(values).joinToString { (l, v) -> "$l ${Money.format(v)}" } }) {
            val n = values.size
            val gap = size.width * 0.04f
            val w = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val h = (v.toFloat() / max) * size.height * grow
                drawRoundRect(
                    color = if (i == highlight) accent else soft,
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(minOf(w * 0.25f, 12.dp.toPx()), minOf(w * 0.25f, 12.dp.toPx())),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) }
        }
    }
}

/** This period's running total (solid, with a soft fill) over the previous period's (dashed). */
@Composable
private fun CumulativeChart(now: List<Long>, before: List<Long>?, color: Color, beforeColor: Color, modifier: Modifier) {
    val reveal by animateFloatAsState(1f, tween(900), label = "cumulative")
    Canvas(modifier) {
        val len = maxOf(now.size, before?.size ?: 0).coerceAtLeast(2)
        val max = maxOf(now.maxOrNull() ?: 0, before?.maxOrNull() ?: 0).coerceAtLeast(1)
        fun x(i: Int) = size.width * i / (len - 1)
        fun y(v: Long) = size.height - size.height * (v.toFloat() / max)
        for (g in 1..3) drawLine(beforeColor.copy(alpha = 0.15f), Offset(0f, size.height * g / 4), Offset(size.width, size.height * g / 4), 1f)
        before?.takeIf { it.size > 1 }?.let { b ->
            val p = Path().apply { b.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            drawPath(p, beforeColor, style = Stroke(3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        }
        val shown = (now.size * reveal).toInt().coerceIn(1, now.size)
        val line = Path()
        val fill = Path()
        for (i in 0 until shown) {
            val px = x(i); val py = y(now[i])
            if (i == 0) { line.moveTo(px, py); fill.moveTo(px, size.height); fill.lineTo(px, py) } else { line.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(x(shown - 1), size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(5f, cap = StrokeCap.Round))
        drawCircle(color, 7f, Offset(x(shown - 1), y(now[shown - 1])))
    }
}

/** A small smooth sparkline with a dot on the last value. */
@Composable
private fun Spark(values: List<Long>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Long) = size.height - size.height * 0.9f * (v.toFloat() / max) - size.height * 0.05f
        val p = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) p.moveTo(x(i), y(v)) else {
                val cx = (x(i - 1) + x(i)) / 2
                p.cubicTo(cx, y(values[i - 1]), cx, y(v), x(i), y(v))
            }
        }
        val fill = Path().apply { addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(p, color, style = Stroke(4f, cap = StrokeCap.Round))
        drawCircle(color, 6f, Offset(x(values.size - 1), y(values.last())))
    }
}

/** A GitHub-style grid: one square per day, a column per week, darker = more spent. */
@Composable
private fun YearHeatmap(days: List<Long>, end: LocalDate, modifier: Modifier) {
    val sc = LocalSproutColors.current
    val base = sc.chart.first()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    // Quartiles of spending days, so one huge rent day doesn't wash out everything else.
    val sorted = days.filter { it > 0 }.sorted()
    fun q(p: Double) = if (sorted.isEmpty()) 0L else sorted[((sorted.size - 1) * p).toInt()]
    val cuts = listOf(q(0.25), q(0.5), q(0.75))
    val start = end.minusDays((days.size - 1).toLong())
    // Rows are Monday..Sunday; the first column starts on the Monday of the first week.
    val lead = start.dayOfWeek.value - 1
    val weeks = (lead + days.size + 6) / 7
    val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
    Column {
        // Tap a day to see its purchases.
        Canvas(modifier.aspectRatio(weeks / 7f).semantics { contentDescription = "Daily spending for the past year" }
            .pointerInput(days, end) {
                detectTapGestures { pos ->
                    val gap = 2.dp.toPx()
                    val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
                    val col = (pos.x / (cell + gap)).toInt(); val row = (pos.y / (cell + gap)).toInt()
                    if (row !in 0..6) return@detectTapGestures
                    val i = col * 7 + row - lead
                    if (i in days.indices && days[i] > 0) {
                        val d = start.plusDays(i.toLong())
                        navigate("search/" + android.net.Uri.encode(d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy", java.util.Locale.US)).lowercase()))
                    }
                }
            }) {
            val gap = 2.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            days.forEachIndexed { i, v ->
                val slot = i + lead
                val col = slot / 7
                val row = slot % 7
                val level = when {
                    v <= 0 -> -1
                    v <= cuts[0] -> 0
                    v <= cuts[1] -> 1
                    v <= cuts[2] -> 2
                    else -> 3
                }
                val color = if (level < 0) empty else base.copy(alpha = listOf(0.3f, 0.5f, 0.75f, 1f)[level])
                drawRoundRect(color, Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.25f, cell * 0.25f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + start.year, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.3f, 0.5f, 0.75f, 1f).forEach { a ->
                Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(base.copy(alpha = a)))
            }
            Text(" More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Paired bars per month: money in (income colour) next to money out. */
@Composable
private fun InOutBars(values: List<Pair<Long, Long>>, months: List<YearMonth>) {
    val sc = LocalSproutColors.current
    val inC = sc.income
    val outC = sc.chart[2 % sc.chart.size]
    val max = values.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1) ?: 1
    val grow by animateFloatAsState(1f, tween(700), label = "inout")
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {
            contentDescription = months.zip(values).joinToString { (m, v) -> "${m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}: in ${Money.format(v.first)}, out ${Money.format(v.second)}" }
        }) {
            val n = values.size
            val slot = size.width / n
            val w = slot * 0.32f
            val r = minOf(w / 2, 6.dp.toPx())
            values.forEachIndexed { i, (a, b) ->
                val x0 = i * slot + slot * 0.14f
                val ha = size.height * (a.toFloat() / max) * grow
                val hb = size.height * (b.toFloat() / max) * grow
                drawRoundRect(inC, Offset(x0, size.height - ha), Size(w, ha.coerceAtLeast(2f)), CornerRadius(r, r))
                drawRoundRect(outC, Offset(x0 + w + slot * 0.04f, size.height - hb), Size(w, hb.coerceAtLeast(2f)), CornerRadius(r, r))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            months.forEach { m ->
                Text(m.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
