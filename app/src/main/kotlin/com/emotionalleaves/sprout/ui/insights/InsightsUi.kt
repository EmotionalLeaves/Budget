package com.emotionalleaves.sprout.ui.insights

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.insights.YearRow
import com.emotionalleaves.sprout.domain.insights.Insight
import com.emotionalleaves.sprout.domain.insights.Tone
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.CategoryIcons
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.IconBadge
import com.emotionalleaves.sprout.ui.common.LineChart
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.StatTile
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.common.money
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun toneColor(t: Tone): Color = when (t) {
    Tone.GOOD -> LocalSproutColors.current.ok
    Tone.HEADS_UP -> LocalSproutColors.current.warning
    Tone.NEUTRAL -> MaterialTheme.colorScheme.secondary
}

private fun toneIcon(t: Tone) = when (t) {
    Tone.GOOD -> Icons.Rounded.CheckCircle
    Tone.HEADS_UP -> Icons.Rounded.Warning
    Tone.NEUTRAL -> Icons.Rounded.Lightbulb
}

/** Insights as a swipeable strip of cards on Home. */
@Composable
fun InsightsStrip(insights: List<Insight>, onCategory: (Long) -> Unit) {
    if (insights.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(insights, key = { it.key }) { i ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = toneColor(i.tone).copy(alpha = 0.12f),
                modifier = Modifier.width(250.dp).heightIn(min = 120.dp)
                    .then(i.categoryId?.let { id -> Modifier.clickable { onCategory(id) } } ?: Modifier),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(toneIcon(i.tone), contentDescription = null, tint = toneColor(i.tone))
                    Text(i.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(i.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
        }
    }
}

private val monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)

/** Net worth over the last 12 months, rebuilt from your transactions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(onBack: () -> Unit) {
    val c: AppContainer = appContainer()
    val series by produceState<List<Pair<java.time.LocalDate, Long>>?>(null) { value = runCatching { c.insightsRepository.netWorth(12) }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), title = { Text("Net worth") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        val s = series ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        if (s.size < 2) {
            EmptyState("Not enough history yet", "Net worth over time appears once Sprout has a month or two of your transactions.")
            return@Column
        }
        val now = s.last().second
        val first = s.first().second
        val lastMonth = s[s.size - 2].second
        LazyColumn {
            item {
                SoftCard {
                    // Touch and drag the line to read any month; let go to go back to today.
                    var picked by remember { mutableStateOf<Int?>(null) }
                    val p = picked?.let { s.getOrNull(it) }
                    Text(p?.let { if (it == s.last()) "Today" else it.first.format(monthLabel) } ?: "Today",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(p?.second ?: now), style = MaterialTheme.typography.displaySmall)
                    LineChart(s.map { it.second }, MaterialTheme.colorScheme.primary, onScrub = { picked = it })
                    Row(Modifier.fillMaxWidth()) {
                        Text(s.first().first.format(monthLabel), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Today", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val c1 = now - lastMonth
                    val c2 = now - first
                    StatTile("This month", (if (c1 >= 0) "+" else "") + money(c1, whole = true), Modifier.tile(),
                        noteColor = if (c1 >= 0) LocalSproutColors.current.ok else LocalSproutColors.current.danger, note = if (c1 >= 0) "▲ since last month" else "▼ since last month")
                    StatTile("Since ${s.first().first.format(monthLabel)}", (if (c2 >= 0) "+" else "") + money(c2, whole = true), Modifier.tile(),
                        noteColor = if (c2 >= 0) LocalSproutColors.current.ok else LocalSproutColors.current.danger, note = if (c2 >= 0) "▲ in total" else "▼ in total")
                }
            }
            item { com.emotionalleaves.sprout.ui.common.SectionTitle("Month by month") }
            val rows = s.reversed()
            items(rows.size, key = { rows[it].first.toString() }) { i ->
                val (d, v) = rows[i]
                // Each month's change against the month before it; the oldest month has nothing to compare to.
                val change = rows.getOrNull(i + 1)?.let { v - it.second }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 24.dp)
                    .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (d == s.last().first) "Today" else d.format(monthLabel), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (change != null && change != 0L) {
                        val up = change > 0
                        Text((if (up) "▲ " else "▼ ") + money(kotlin.math.abs(change), whole = true),
                            Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelMedium,
                            color = if (up) LocalSproutColors.current.ok else LocalSproutColors.current.danger)
                    }
                    Text(money(v), style = MaterialTheme.typography.titleSmall)
                }
            }
            item {
                Text("Counted: accounts included in your total. Rebuilt from transactions, so months before your first synced or imported transaction aren't shown.",
                    Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A year at a glance: every category's spending per month, as a heat grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(onBack: () -> Unit, onCategory: (Long) -> Unit) {
    val c: AppContainer = appContainer()
    val data by produceState<Pair<List<java.time.YearMonth>, List<YearRow>>?>(null) { value = runCatching { c.insightsRepository.yearGrid(12) }.getOrNull() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), title = { Text("Year view") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        val (months, rows) = data ?: run { com.emotionalleaves.sprout.ui.common.LoadingSkeleton(); return@Column }
        if (rows.isEmpty()) {
            EmptyState("Nothing to show yet", "Your spending by month appears here after your first sync or CSV import.")
            return@Column
        }
        val totals = months.indices.map { i -> rows.sumOf { it.byMonth[i] } }
        val maxCell = rows.maxOf { r -> r.byMonth.max() }.coerceAtLeast(1)
        val scroll = rememberScrollState()
        // Open on the latest months (the right end), where the data is.
        androidx.compose.runtime.LaunchedEffect(months.size) {
            // Rows measure one by one, so the end keeps moving for a moment; follow it until it settles.
            kotlinx.coroutines.withTimeoutOrNull(1_500) {
                androidx.compose.runtime.snapshotFlow { scroll.maxValue }.collect { if (it in 1 until Int.MAX_VALUE) scroll.scrollTo(it) }
            }
        }
        val heat = MaterialTheme.colorScheme.primary
        // "Each category" shades every row against its own biggest month, so a pricey month for a small category
        // stands out instead of everything looking pale next to rent.
        var perRow by com.emotionalleaves.sprout.ui.common.rememberViewPref("year-shade-per-row", false, { it.toString() }, { it.toBooleanStrictOrNull() })
        LazyColumn {
            item {
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    listOf("By amount" to false, "Within each category" to true).forEachIndexed { i, (label, v) ->
                        SegmentedButton(perRow == v, { perRow = v }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2)) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                Text(if (perRow) "Darker = a bigger month for that category. Scroll sideways for every month; tap a row to open the category."
                    else "Darker = more spent. Scroll sideways for every month; tap a row to open the category.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.padding(start = 16.dp)) {
                    Spacer(Modifier.width(132.dp))
                    Row(Modifier.horizontalScroll(scroll)) {
                        months.forEach { Text(it.format(shortMonth), Modifier.width(64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                        Text("Avg", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(rows, key = { it.categoryId }) { r ->
                Row(Modifier.padding(start = 16.dp, top = 3.dp, bottom = 3.dp).clickable { onCategory(r.categoryId) }, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(132.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(CategoryIcons.forKey(r.iconKey), Color(r.colorArgb), 28.dp)
                        Spacer(Modifier.width(6.dp))
                        // A zero-width space after each hyphen lets "Person-to-Person" wrap at the hyphens, not mid-word.
                        Text(r.name.replace("-", "-\u200B"), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.horizontalScroll(scroll)) {
                        val scale = if (perRow) r.byMonth.max().coerceAtLeast(1) else maxCell
                        r.byMonth.forEach { v ->
                            val a = (v.toFloat() / scale).coerceIn(0f, 1f)
                            Box(
                                Modifier.width(60.dp).height(36.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
                                    .background(heat.copy(alpha = 0.06f + 0.7f * a)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (v == 0L) "–" else Money.formatWhole(v), style = MaterialTheme.typography.labelSmall,
                                    color = if (a > 0.55f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(Money.formatWhole(r.average), Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", Modifier.width(132.dp), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(scroll)) {
                        totals.forEach { Text(Money.formatWhole(it), Modifier.width(64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

/** Header card for the Bills tab: what your repeating charges cost per month and per year. */
@Composable
fun SubscriptionsSummary() {
    val c: AppContainer = appContainer()
    val subs by produceState<com.emotionalleaves.sprout.data.insights.Subscriptions?>(null) { value = runCatching { c.insightsRepository.subscriptions() }.getOrNull() }
    val changes by produceState<List<com.emotionalleaves.sprout.domain.insights.PriceChange>>(emptyList()) {
        value = runCatching { c.insightsRepository.priceChanges() }.getOrDefault(emptyList())
    }
    val s = subs ?: return
    if (s.count == 0) return
    changes.forEach { ch ->
        SoftCard {
            val tone = if (ch.up) LocalSproutColors.current.warning else LocalSproutColors.current.ok
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (ch.up) androidx.compose.material.icons.Icons.AutoMirrored.Rounded.TrendingUp else androidx.compose.material.icons.Icons.AutoMirrored.Rounded.TrendingDown, tone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${ch.name} ${if (ch.up) "went up" else "went down"}", style = MaterialTheme.typography.titleSmall, color = tone)
                    Text("${Money.format(ch.fromMinor)} → ${Money.format(ch.toMinor)} on the latest charge" +
                        if (ch.up) ". Not worth it any more? Cancel with the company directly." else ".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            // At large text sizes the monthly amount moves under the title, so the title keeps its width.
            val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
            Column(Modifier.weight(1f)) {
                Text("${s.count} repeating charges", style = MaterialTheme.typography.titleSmall)
                Text("≈ ${Money.formatWhole(s.yearlyMinor)} a year", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (large) Text("${Money.formatWhole(s.monthlyMinor)}/mo", style = MaterialTheme.typography.titleMedium)
            }
            if (!large) Text("${Money.formatWhole(s.monthlyMinor)}/mo", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** This month as a calendar: darker days cost more, ✓ marks no-spend days. */
@Composable
fun SpendCalendarCard(cal: com.emotionalleaves.sprout.domain.calendar.SpendCalendar) {
    val heat = MaterialTheme.colorScheme.primary
    val ok = LocalSproutColors.current.ok
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This month by day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${cal.noSpendDays} no-spend day${if (cal.noSpendDays == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = ok)
        }
        Row(Modifier.fillMaxWidth()) {
            com.emotionalleaves.sprout.domain.calendar.SpendCalendar.headers.forEach {
                Text(it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.US), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cal.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        if (d != null) {
                            val a = if (cal.maxDayMinor > 0) (d.spentMinor.toFloat() / cal.maxDayMinor).coerceIn(0f, 1f) else 0f
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(
                                    when {
                                        d.future -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        d.spentMinor == 0L -> ok.copy(alpha = 0.14f)
                                        else -> heat.copy(alpha = 0.12f + 0.75f * a)
                                    },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (!d.future && d.spentMinor == 0L) "✓" else d.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        !d.future && d.spentMinor == 0L -> ok
                                        a > 0.55f -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val recapMonth = DateTimeFormatter.ofPattern("MMMM", Locale.US)

/** "Your October" — shown on Home during the first week of a month. Dismiss with ✕. */
@Composable
fun RecapCard(r: com.emotionalleaves.sprout.domain.insights.Recap, onDismiss: () -> Unit) {
    if (r.spentMinor == 0L && r.incomeMinor == 0L) return
    val sc = LocalSproutColors.current
    com.emotionalleaves.sprout.ui.common.HeroCard(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your ${r.month.format(recapMonth)}", style = MaterialTheme.typography.titleLarge, color = sc.onHero, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("✕", color = sc.onHero) }
        }
        Text(r.headline(), style = MaterialTheme.typography.bodyLarge, color = sc.onHero)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Spent", style = MaterialTheme.typography.labelMedium, color = sc.onHero.copy(alpha = 0.8f))
                Text(money(r.spentMinor, whole = true), style = MaterialTheme.typography.titleLarge, color = sc.onHero)
                r.changePercent?.let { Text(if (it > 0) "↑ $it% vs month before" else if (it < 0) "↓ ${-it}% vs month before" else "same as before",
                    style = MaterialTheme.typography.labelSmall, color = sc.onHero.copy(alpha = 0.85f)) }
            }
            if (r.incomeMinor > 0) Column(Modifier.weight(1f)) {
                Text("Came in", style = MaterialTheme.typography.labelMedium, color = sc.onHero.copy(alpha = 0.8f))
                Text(money(r.incomeMinor, whole = true), style = MaterialTheme.typography.titleLarge, color = sc.onHero)
            }
        }
        r.topCategories.firstOrNull()?.let { (n, v) -> Text("Most went to $n (${money(v, whole = true)})", style = MaterialTheme.typography.bodySmall, color = sc.onHero) }
        r.biggest?.let { (n, v) -> Text("Biggest purchase: $n, ${money(v)}", style = MaterialTheme.typography.bodySmall, color = sc.onHero) }
        Text("${r.noSpendDays} no-spend days", style = MaterialTheme.typography.bodySmall, color = sc.onHero)
    }
}
