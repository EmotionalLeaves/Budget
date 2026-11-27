package com.emotionalleaves.sprout.ui.insights

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.insights.InsightsRepository
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.DonutChart
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.FatProgress
import com.emotionalleaves.sprout.ui.common.LegendRow
import com.emotionalleaves.sprout.ui.common.SectionHeader
import com.emotionalleaves.sprout.ui.common.Slice
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.StatTile
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mShort = DateTimeFormatter.ofPattern("MMM", Locale.US)
private val mLong = DateTimeFormatter.ofPattern("MMMM", Locale.US)

/** Income next to spending for each month, on a faint grid with the top value marked. */
@Composable
private fun CashFlowChart(income: List<Long>, spending: List<Long>, labels: List<String>) {
    val inc = LocalSproutColors.current.income
    val sp = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (income + spending).maxOrNull()?.coerceAtLeast(1) ?: 1
    val desc = labels.indices.joinToString(", ") { "${labels[it]}: in ${Money.formatWhole(income[it])}, out ${Money.formatWhole(spending[it])}" }
    val progress = androidx.compose.runtime.remember(income, spending) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(income, spending) {
        progress.animateTo(1f, androidx.compose.animation.core.tween(850, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val maxLabel = Money.formatWhole(max)
    Column(Modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val axis = tm.measure(maxLabel, axisStyle)
            val top = axis.size.height + 4.dp.toPx()
            val chartH = size.height - top
            // Guides at the top, two thirds and one third, with the top value written above the first.
            for (k in 0..2) {
                val y = top + chartH * k / 3f
                drawLine(grid.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
            }
            drawText(axis, topLeft = Offset(0f, 0f))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = (slot * 0.3f).coerceAtMost(36.dp.toPx())
            val gap = 4.dp.toPx()
            labels.indices.forEach { i ->
                val x = i * slot + (slot - 2 * bw - gap) / 2
                val hi = (chartH * income[i] / max * progress.value).coerceAtLeast(2f)
                val hs = (chartH * spending[i] / max * progress.value).coerceAtLeast(2f)
                bar(x, size.height - hi, bw, size.height, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(inc, inc.copy(alpha = 0.6f)), size.height - hi, size.height))
                bar(x + bw + gap, size.height - hs, bw, size.height, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(sp, sp.copy(alpha = 0.6f)), size.height - hs, size.height))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(inc, "Came in"); LegendDot(sp, "Spent")
        }
    }
}

/** A bar with rounded top corners sitting flat on the baseline. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bar(x: Float, top: Float, w: Float, bottom: Float, brush: androidx.compose.ui.graphics.Brush) {
    val r = (w / 3).coerceAtMost(bottom - top)
    drawPath(androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(x, top, x + w, bottom),
            CornerRadius(r, r), CornerRadius(r, r), CornerRadius.Zero, CornerRadius.Zero))
    }, brush)
}

/** Reports' hero: what you kept, with what came in and went out underneath and a split bar between them. */
@Composable
private fun ReportsHero(totalIn: Long, totalOut: Long) {
    val sc = LocalSproutColors.current
    val on = sc.onHero
    val net = totalIn - totalOut
    com.emotionalleaves.sprout.ui.common.HeroCard(sc.hero) {
        Text(if (net >= 0) "Kept" else "Spent more than came in", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
        Text((if (net >= 0) "" else "−") + com.emotionalleaves.sprout.ui.common.animatedMoney(kotlin.math.abs(net), whole = true),
            style = MaterialTheme.typography.displayMedium, color = if (net >= 0) on else androidx.compose.ui.graphics.Color(0xFFFFB4A9), maxLines = 1)
        if (totalIn > 0) com.emotionalleaves.sprout.ui.common.HeroPill("${net * 100 / totalIn}% of income", on)
        Spacer(Modifier.height(10.dp))
        // In vs out as one bar: the bright part is what was spent.
        val f = if (totalIn + totalOut > 0) totalOut.toFloat() / maxOf(totalIn, totalOut) else 0f
        com.emotionalleaves.sprout.ui.common.FatProgress(f, on, track = on.copy(alpha = 0.18f), height = 8.dp)
        com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(top = 4.dp)) {
            Column(Modifier.tile()) {
                Text("Came in", style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
                Text(Money.formatWhole(totalIn), style = MaterialTheme.typography.titleLarge, color = on, maxLines = 1)
            }
            Column(Modifier.tile()) {
                Text("Spent", style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
                Text(Money.formatWhole(totalOut), style = MaterialTheme.typography.titleLarge, color = on, maxLines = 1)
            }
        }
    }
}

/** One ranked line inside a card: a color key, name, amount and a bar relative to the total. */
@Composable
private fun ShareRow(color: androidx.compose.ui.graphics.Color, name: String, value: Long, total: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(10.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(Money.format(value), style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FatProgress(value.toFloat() / total.coerceAtLeast(1), color, Modifier.weight(1f), height = 6.dp)
            val pct = value * 100 / total.coerceAtLeast(1)
            Text(if (pct == 0L && value > 0) "<1%" else "$pct%", Modifier.widthIn(min = 40.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(c: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(c) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Reports: cash flow by month, and where the money went by category and by account, for a chosen range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val c = appContainer()
    var range by com.emotionalleaves.sprout.ui.common.rememberViewPref("reports-range", 1, { it.toString() }, { it.toIntOrNull()?.takeIf { n -> n in setOf(1, 3, 6, 12) } })
    val ranges = listOf(1 to "Month", 3 to "3 mo", 6 to "6 mo", 12 to "Year")
    val months = ranges[range].first.coerceAtLeast(1)
    val r = com.emotionalleaves.sprout.ui.common.rememberLoaded(months) { runCatching { c.insightsRepository.reports(maxOf(months, 1)) }.getOrNull() }
    var comparing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (comparing) { CompareMonths(onBack = { comparing = false }); return }
    Column(Modifier.fillMaxSize()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), 
            title = { Text("Reports") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                androidx.compose.material3.TextButton(onClick = { comparing = true }) { Text("Compare") }
                androidx.compose.material3.TextButton(onClick = { scope.launch { shareMonthPdf(context, c, java.time.YearMonth.now()) } }) { Text("PDF") }
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ranges.forEachIndexed { i, (_, label) -> SegmentedButton(range == i, { range = i }, SegmentedButtonDefaults.itemShape(i, ranges.size)) { Text(label, maxLines = 1) } }
        }
        val rep = r ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        val totalIn = rep.incomeByMonth.sum()
        val totalOut = rep.spendingByMonth.sum()
        if (totalIn == 0L && totalOut == 0L) {
            EmptyState("Nothing in this range", "Reports fill in as transactions sync or import.")
            return@Column
        }
        val sc = LocalSproutColors.current
        LazyColumn {
            item { ReportsHero(totalIn, totalOut) }
            if (rep.months.size > 1) item {
                SoftCard {
                    Text("Cash flow", style = MaterialTheme.typography.titleMedium)
                    CashFlowChart(rep.incomeByMonth, rep.spendingByMonth, rep.months.map { it.format(mShort) })
                }
            }
            if (rep.categories.isNotEmpty()) {
                item { SectionHeader("By category") }
                item {
                    SoftCard {
                        val top = rep.categories.take(6)
                        val rest = rep.categories.drop(6).sumOf { it.second }
                        val slices = top.mapIndexed { i, (n, v) -> Slice(n, v, sc.chart[i % sc.chart.size]) } +
                            (if (rest > 0) listOf(Slice("Other", rest, MaterialTheme.colorScheme.outline)) else emptyList())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(slices, Modifier.size(136.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Money.formatWhole(totalOut), style = MaterialTheme.typography.titleSmall)
                                    Text("spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { slices.forEach { LegendRow(it.color, it.label, Money.formatWhole(it.value)) } }
                        }
                    }
                }
                item {
                    SoftCard {
                        val other = MaterialTheme.colorScheme.outline
                        rep.categories.forEachIndexed { i, (n, v) ->
                            ShareRow(if (i < 6) sc.chart[i % sc.chart.size] else other, n, v, totalOut)
                        }
                    }
                }
            }
            if (rep.accounts.size > 1) {
                item { SectionHeader("By account") }
                item {
                    SoftCard {
                        rep.accounts.forEach { (n, v) -> ShareRow(MaterialTheme.colorScheme.secondary, n, v, totalOut) }
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

/** Year in review: the year's totals, top categories and merchants, best and priciest month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearInReviewScreen(onBack: () -> Unit) {
    val c = appContainer()
    var year by rememberSaveable { mutableIntStateOf(java.time.LocalDate.now().year) }
    val y = com.emotionalleaves.sprout.ui.common.rememberLoaded(year) { runCatching { c.insightsRepository.yearReview(year) }.getOrNull() }
    // The earliest year you have data for, so "‹ 2025" only shows when there's something to see.
    val firstYear = com.emotionalleaves.sprout.ui.common.rememberLoaded(Unit) {
        runCatching { c.db.budgets().firstOccurredAt()?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).year } }.getOrNull()
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), 
            title = { Text("$year in review") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                if (firstYear != null && year > firstYear) androidx.compose.material3.TextButton(onClick = { year -= 1 }) { Text("‹ ${year - 1}") }
                if (year < java.time.LocalDate.now().year) androidx.compose.material3.TextButton(onClick = { year += 1 }) { Text("${year + 1} ›") }
            },
        )
        val r = y ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        if (r.spentMinor == 0L && r.incomeMinor == 0L) {
            EmptyState("No data for $year", "Year in review fills in from your synced or imported transactions.")
            return@Column
        }
        val sc = LocalSproutColors.current
        LazyColumn {
            item {
                com.emotionalleaves.sprout.ui.common.HeroCard(sc.hero) {
                    Text("You spent", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                    Text(com.emotionalleaves.sprout.ui.common.animatedMoney(r.spentMinor, whole = true), style = MaterialTheme.typography.displayMedium, color = sc.onHero, maxLines = 1)
                    if (r.incomeMinor > 0) {
                        val kept = r.incomeMinor - r.spentMinor
                        Text(if (kept >= 0) "and kept ${Money.formatWhole(kept)} of ${Money.formatWhole(r.incomeMinor)} that came in"
                            else "${Money.formatWhole(-kept)} more than came in", style = MaterialTheme.typography.bodyLarge, color = sc.onHero)
                    }
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.emotionalleaves.sprout.ui.common.HeroPill("${r.transactions} transactions", sc.onHero)
                        com.emotionalleaves.sprout.ui.common.HeroPill("${r.noSpendDays} no-spend days", sc.onHero)
                    }
                }
            }
            item {
                SoftCard {
                    Text("Month by month", style = MaterialTheme.typography.titleMedium)
                    com.emotionalleaves.sprout.ui.common.BarChart(r.byMonth.map { it.second }, r.byMonth.map { it.first.format(mShort).take(1) }, MaterialTheme.colorScheme.primary)
                    r.cheapestMonth?.let { Text("Lightest month: ${it.first.format(mLong)} (${Money.formatWhole(it.second)})", style = MaterialTheme.typography.bodySmall) }
                    r.priciestMonth?.let { Text("Biggest month: ${it.first.format(mLong)} (${Money.formatWhole(it.second)})", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (r.topCategories.isNotEmpty()) {
                item { SectionHeader("Top categories") }
                item { RankedCard(r.topCategories) }
            }
            if (r.topMerchants.isNotEmpty()) {
                item { SectionHeader("Top places") }
                item { RankedCard(r.topMerchants) }
            }
            r.biggest?.let { (n, v) ->
                item {
                    SoftCard {
                        Text("Biggest single purchase", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(Money.format(v), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}


/** Builds the month's PDF and opens the share sheet (save to Files, email, print…). */
suspend fun shareMonthPdf(context: android.content.Context, c: com.emotionalleaves.sprout.AppContainer, month: java.time.YearMonth) {
    val recap = c.insightsRepository.recap(month)
    val budget = runCatching { c.budgetRepository.observeMonth(month).first() }.getOrNull()
    val file = com.emotionalleaves.sprout.data.report.MonthReportPdf.write(context, recap, budget)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".updates", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share ${month} report")) }
}

/** Month vs the month before, category by category, biggest change first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareMonths(onBack: () -> Unit) {
    val c = appContainer()
    androidx.activity.compose.BackHandler(onBack = onBack)
    var month by rememberSaveable { androidx.compose.runtime.mutableStateOf(java.time.YearMonth.now().toString()) }
    val ym = java.time.YearMonth.parse(month)
    val rows = com.emotionalleaves.sprout.ui.common.rememberLoaded(month) { runCatching { c.insightsRepository.compare(ym) }.getOrNull() }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
    val sc = LocalSproutColors.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), 
            title = { Text("Compare months") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { month = ym.minusMonths(1).toString() }) { Text("‹ Earlier") }
            Text("${ym.format(fmt)} vs ${ym.minusMonths(1).format(fmt)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            androidx.compose.material3.TextButton(enabled = ym < java.time.YearMonth.now(), onClick = { month = ym.plusMonths(1).toString() }) { Text("Later ›") }
        }
        val list = rows ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        if (list.isEmpty()) { EmptyState("Nothing to compare", "Spending in either month shows up here."); return@Column }
        val now = list.sumOf { it.nowMinor }
        val before = list.sumOf { it.beforeMinor }
        LazyColumn {
            item {
                com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    StatTile(ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM")), Money.formatWhole(now), Modifier.tile())
                    StatTile(ym.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM")), Money.formatWhole(before), Modifier.tile())
                    val d = now - before
                    StatTile("Change", (if (d > 0) "+" else if (d < 0) "−" else "") + Money.formatWhole(kotlin.math.abs(d)), Modifier.tile(),
                        noteColor = if (d > 0) sc.danger else sc.ok, note = if (d > 0) "more" else if (d < 0) "less" else "same")
                }
            }
            item {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM")))
                    LegendDot(MaterialTheme.colorScheme.outline, ym.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM")))
                }
                if (ym == java.time.YearMonth.now()) {
                    Text("This month isn't over yet, so it will usually look lower.", Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // One scale for every row, so a $34 change looks small next to a $950 one.
            val scale = list.maxOfOrNull { maxOf(it.nowMinor, it.beforeMinor) }?.coerceAtLeast(1) ?: 1
            val groups = listOf(
                "Went up" to list.filter { it.deltaMinor > 0 },
                "Went down" to list.filter { it.deltaMinor < 0 },
                "About the same" to list.filter { it.deltaMinor == 0L },
            ).filter { it.second.isNotEmpty() }
            groups.forEach { (label, rows) ->
                item(key = "cmp-h-$label") {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp))
                }
                item(key = "cmp-g-$label") {
                    SoftCard {
                        rows.forEachIndexed { i, r ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    val up = r.deltaMinor > 0
                                    Text(
                                        if (r.deltaMinor == 0L) Money.formatWhole(r.nowMinor) else
                                            (if (up) "▲ " else "▼ ") + Money.formatWhole(kotlin.math.abs(r.deltaMinor)) +
                                                (r.percent?.let { " (${kotlin.math.abs(it)}%)" } ?: if (r.beforeMinor == 0L) " (new)" else ""),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (up) sc.danger else if (r.deltaMinor < 0) sc.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                FatProgress(r.nowMinor.toFloat() / scale, MaterialTheme.colorScheme.primary, height = 6.dp)
                                Spacer(Modifier.height(3.dp))
                                FatProgress(r.beforeMinor.toFloat() / scale, MaterialTheme.colorScheme.outline, height = 6.dp)
                                Text("${Money.formatWhole(r.nowMinor)} vs ${Money.formatWhole(r.beforeMinor)}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.height(24.dp)) }
        }
    }
}

/** A numbered top-5 list in one card, each with a bar relative to the first. */
@Composable
private fun RankedCard(rows: List<Pair<String, Long>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    SoftCard {
        rows.forEachIndexed { i, (n, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge,
                        color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(Money.formatWhole(v), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    FatProgress(v.toFloat() / max, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), height = 6.dp)
                }
            }
        }
    }
}
