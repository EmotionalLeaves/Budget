package com.emotionalleaves.sprout.ui.insights

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
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

/** Income (green) next to spending (primary) for each month. */
@Composable
private fun CashFlowChart(income: List<Long>, spending: List<Long>, labels: List<String>) {
    val inc = LocalSproutColors.current.income
    val sp = MaterialTheme.colorScheme.tertiary
    val max = (income + spending).maxOrNull()?.coerceAtLeast(1) ?: 1
    val desc = labels.indices.joinToString(", ") { "${labels[it]}: in ${Money.formatWhole(income[it])}, out ${Money.formatWhole(spending[it])}" }
    Column(Modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.32f
            labels.indices.forEach { i ->
                val x = i * slot + slot * 0.16f
                val hi = size.height * income[i] / max
                val hs = size.height * spending[i] / max
                drawRoundRect(inc, Offset(x, size.height - hi), Size(bw, hi.coerceAtLeast(2f)), CornerRadius(bw / 3, bw / 3))
                drawRoundRect(sp, Offset(x + bw + 2f, size.height - hs), Size(bw, hs.coerceAtLeast(2f)), CornerRadius(bw / 3, bw / 3))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(inc, "Came in"); LegendDot(sp, "Spent")
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
    var range by rememberSaveable { mutableIntStateOf(1) }
    val ranges = listOf(1 to "Month", 3 to "3 mo", 6 to "6 mo", 12 to "Year")
    val months = ranges[range].first.coerceAtLeast(1)
    val r = com.emotionalleaves.sprout.ui.common.rememberLoaded(months) { runCatching { c.insightsRepository.reports(maxOf(months, 1)) }.getOrNull() }
    var comparing by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (comparing) { CompareMonths(onBack = { comparing = false }); return }
    Column(Modifier.fillMaxSize()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        TopAppBar(
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
        val rep = r ?: return@Column
        val totalIn = rep.incomeByMonth.sum()
        val totalOut = rep.spendingByMonth.sum()
        if (totalIn == 0L && totalOut == 0L) {
            EmptyState("Nothing in this range", "Reports fill in as transactions sync or import.")
            return@Column
        }
        val sc = LocalSproutColors.current
        LazyColumn {
            item {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Came in", Money.formatWhole(totalIn), Modifier.weight(1f))
                    StatTile("Spent", Money.formatWhole(totalOut), Modifier.weight(1f))
                    val net = totalIn - totalOut
                    StatTile("Kept", (if (net >= 0) "" else "−") + Money.formatWhole(kotlin.math.abs(net)), Modifier.weight(1f),
                        note = if (totalIn > 0) "${net * 100 / totalIn}% of income" else null, noteColor = if (net >= 0) sc.ok else sc.danger)
                }
            }
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
                            DonutChart(slices, Modifier.size(130.dp)) { Text(Money.formatWhole(totalOut), style = MaterialTheme.typography.titleSmall) }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { slices.forEach { LegendRow(it.color, it.label, Money.formatWhole(it.value)) } }
                        }
                    }
                }
                items(rep.categories, key = { "rc-" + it.first }) { (n, v) ->
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Row { Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(Money.format(v), style = MaterialTheme.typography.labelLarge) }
                        FatProgress(v.toFloat() / totalOut.coerceAtLeast(1), MaterialTheme.colorScheme.primary, height = 6.dp)
                    }
                }
            }
            if (rep.accounts.size > 1) {
                item { SectionHeader("By account") }
                items(rep.accounts, key = { "ra-" + it.first }) { (n, v) ->
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Row { Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(Money.format(v), style = MaterialTheme.typography.labelLarge) }
                        FatProgress(v.toFloat() / totalOut.coerceAtLeast(1), MaterialTheme.colorScheme.secondary, height = 6.dp)
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
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("$year in review") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                androidx.compose.material3.TextButton(onClick = { year -= 1 }) { Text("‹ ${year - 1}") }
                if (year < java.time.LocalDate.now().year) androidx.compose.material3.TextButton(onClick = { year += 1 }) { Text("${year + 1} ›") }
            },
        )
        val r = y ?: return@Column
        if (r.spentMinor == 0L && r.incomeMinor == 0L) {
            EmptyState("No data for $year", "Year in review fills in from your synced or imported transactions.")
            return@Column
        }
        val sc = LocalSproutColors.current
        LazyColumn {
            item {
                com.emotionalleaves.sprout.ui.common.HeroCard(sc.hero) {
                    Text("You spent", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                    Text(Money.formatWhole(r.spentMinor), style = MaterialTheme.typography.displayMedium, color = sc.onHero)
                    if (r.incomeMinor > 0) {
                        val kept = r.incomeMinor - r.spentMinor
                        Text(if (kept >= 0) "and kept ${Money.formatWhole(kept)} of ${Money.formatWhole(r.incomeMinor)} that came in"
                            else "${Money.formatWhole(-kept)} more than came in", style = MaterialTheme.typography.bodyLarge, color = sc.onHero)
                    }
                    Text("${r.transactions} transactions · ${r.noSpendDays} no-spend days", style = MaterialTheme.typography.bodySmall, color = sc.onHero.copy(alpha = 0.85f))
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
        TopAppBar(
            title = { Text("Compare months") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { month = ym.minusMonths(1).toString() }) { Text("‹ Earlier") }
            Text("${ym.format(fmt)} vs ${ym.minusMonths(1).format(fmt)}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            androidx.compose.material3.TextButton(enabled = ym < java.time.YearMonth.now(), onClick = { month = ym.plusMonths(1).toString() }) { Text("Later ›") }
        }
        val list = rows ?: return@Column
        if (list.isEmpty()) { EmptyState("Nothing to compare", "Spending in either month shows up here."); return@Column }
        val now = list.sumOf { it.nowMinor }
        val before = list.sumOf { it.beforeMinor }
        LazyColumn {
            item {
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM")), Money.formatWhole(now), Modifier.weight(1f))
                    StatTile(ym.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM")), Money.formatWhole(before), Modifier.weight(1f))
                    val d = now - before
                    StatTile("Change", (if (d > 0) "+" else if (d < 0) "−" else "") + Money.formatWhole(kotlin.math.abs(d)), Modifier.weight(1f),
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
            items(list, key = { "cmp-" + it.name }) { r ->
                val max = maxOf(r.nowMinor, r.beforeMinor).coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        val up = r.deltaMinor > 0
                        Text(
                            if (r.deltaMinor == 0L) "same" else
                                (if (up) "▲ " else "▼ ") + Money.formatWhole(kotlin.math.abs(r.deltaMinor)) +
                                    (r.percent?.let { " (${kotlin.math.abs(it)}%)" } ?: if (r.beforeMinor == 0L) " (new)" else ""),
                            style = MaterialTheme.typography.labelLarge, color = if (up) sc.danger else if (r.deltaMinor < 0) sc.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FatProgress(r.nowMinor.toFloat() / max, MaterialTheme.colorScheme.primary, height = 6.dp)
                    Spacer(Modifier.height(3.dp))
                    FatProgress(r.beforeMinor.toFloat() / max, MaterialTheme.colorScheme.outline, height = 6.dp)
                    Text("${Money.formatWhole(r.nowMinor)} vs ${Money.formatWhole(r.beforeMinor)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
