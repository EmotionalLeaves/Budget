package com.emotionalleaves.sprout.ui.home

import com.emotionalleaves.sprout.domain.accounts.label
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.recurring.UpcomingBill
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.forecast.CashFlow
import com.emotionalleaves.sprout.ui.budgets.SafeToSpendBreakdown
import com.emotionalleaves.sprout.ui.common.BarChart
import com.emotionalleaves.sprout.ui.common.CategoryIcons
import com.emotionalleaves.sprout.ui.common.DonutChart
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.HeroCard
import com.emotionalleaves.sprout.ui.common.IconBadge
import com.emotionalleaves.sprout.ui.common.LegendRow
import com.emotionalleaves.sprout.ui.common.ProgressRing
import com.emotionalleaves.sprout.ui.common.Slice
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.StatTile
import com.emotionalleaves.sprout.ui.common.money
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.US)

/** "Good evening" + today's date, with the privacy eye on the right. */
@Composable
fun HomeHeader(hidden: Boolean, onTogglePrivacy: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello" }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium)
            Text(java.time.LocalDate.now().format(dayFormat), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onTogglePrivacy) {
            Icon(
                if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (hidden) "Show amounts" else "Hide amounts",
            )
        }
    }
}

/** The hero: safe to spend this month, per day, and a ring showing how much of the budget is used. */
@Composable
fun SafeToSpendHero(m: BudgetMonth?, netWorthMinor: Long, onSetBudget: () -> Unit) {
    val c = LocalSproutColors.current
    var breakdown by remember { mutableStateOf(false) }
    val safe = m?.safeToSpend
    HeroCard(c.hero, onClick = if (safe != null) ({ breakdown = true }) else null) {
        if (m == null || safe == null) {
            if (netWorthMinor != 0L) {
                Text("Total balance", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                Text(com.emotionalleaves.sprout.ui.common.animatedMoney(netWorthMinor), style = MaterialTheme.typography.displayMedium, color = c.onHero)
            } else {
                // No balance yet: a "$0.00" headline would only look alarming.
                Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                Text("Your daily number", style = MaterialTheme.typography.headlineMedium, color = c.onHero)
            }
            Text("Set budgets to see what's safe to spend each day.", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.85f))
            Spacer(Modifier.size(4.dp))
            Button(
                onClick = onSetBudget,
                colors = ButtonDefaults.buttonColors(containerColor = c.onHero, contentColor = Color(0xFF0F5137)),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Set up budgets") }
        } else {
            val used = if (m.totalBudgetMinor > 0) m.totalSpentBudgetedMinor.toFloat() / m.totalBudgetMinor else 0f
            HeroRow(ring = { ringSize ->
                ProgressRing(
                    used, color = c.onHero, track = c.onHero.copy(alpha = 0.18f), overColor = Color(0xFFFFB4A9),
                    modifier = Modifier.size(ringSize), stroke = 12.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(used * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                        Text("used", style = MaterialTheme.typography.labelSmall, color = c.onHero.copy(alpha = 0.8f))
                    }
                }
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                    Text(
                        com.emotionalleaves.sprout.ui.common.animatedMoney(safe.amountMinor),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (safe.amountMinor < 0) Color(0xFFFFB4A9) else c.onHero,
                        maxLines = 1,
                    )
                    safe.perDayMinor?.let { Text("≈ ${money(it)} a day", style = MaterialTheme.typography.titleMedium, color = c.onHero) }
                    safe.daysRemaining?.let {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            com.emotionalleaves.sprout.ui.common.HeroPill("$it day${if (it == 1) "" else "s"} left", c.onHero, Icons.Rounded.Schedule)
                            com.emotionalleaves.sprout.ui.common.HeroPill("Tap for details", c.onHero.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
    }
    if (breakdown && m != null) SafeToSpendBreakdown(m) { breakdown = false }
}

/**
 * Hero layout: the numbers on the left and a ring on the right. At large text sizes the ring moves below,
 * so a big amount never gets cut off.
 */
@Composable
fun HeroRow(ring: @Composable (ringSize: androidx.compose.ui.unit.Dp) -> Unit, content: @Composable () -> Unit) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    if (large) {
        // Bigger ring too, so its own large text fits inside.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content(); ring(150.dp) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { content() }
            Spacer(Modifier.width(12.dp))
            ring(108.dp)
        }
    }
}

/** Three quick numbers: net worth, spent this month (vs last month), month-end cash. */
@Composable
fun StatsRow(netWorthMinor: Long, trend: CashFlow.Trend?, forecastEndMinor: Long?, onNetWorth: () -> Unit = {}) {
    val c = LocalSproutColors.current
    com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        StatTile("Net worth", money(netWorthMinor, whole = true), Modifier.tile().clickable(onClick = onNetWorth), note = "see trend ›")
        val p = trend?.percent
        StatTile(
            "Spent", money(trend?.thisMonthMinor ?: 0, whole = true), Modifier.tile(),
            note = p?.let { if (it > 0) "↑ $it% vs last mo." else if (it < 0) "↓ ${-it}% vs last mo." else "Same as last mo." },
            noteColor = if ((p ?: 0) > 10) c.warning else if ((p ?: 0) < 0) c.ok else Color.Unspecified,
        )
        if (forecastEndMinor != null) {
            StatTile("Month end", money(forecastEndMinor, whole = true), Modifier.tile(), note = "cash, estimated",
                noteColor = if (forecastEndMinor < 0) c.danger else Color.Unspecified)
        }
    }
}

/** Where the money went: donut of this month's top categories with a tappable legend. */
@Composable
fun WhereItWentCard(m: BudgetMonth, onCategory: (Long) -> Unit, onSeeAll: () -> Unit) {
    val c = LocalSproutColors.current
    val lines = (m.budgeted + m.unbudgetedWithSpending).filter { it.spentMinor > 0 }.sortedByDescending { it.spentMinor }
    if (lines.isEmpty()) return
    val top = lines.take(5)
    val rest = lines.drop(5).sumOf { it.spentMinor }
    val slices = top.mapIndexed { i, l -> Slice(l.category.name, l.spentMinor, c.chart[i % c.chart.size]) } +
        (if (rest > 0) listOf(Slice("Everything else", rest, MaterialTheme.colorScheme.outline)) else emptyList())
    SoftCard(onClick = onSeeAll) {
        Text("Where it went", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(slices, Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(money(m.totalSpentMinor, whole = true), style = MaterialTheme.typography.titleMedium)
                    Text("this month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                top.forEachIndexed { i, l ->
                    LegendRow(slices[i].color, l.category.name, money(l.spentMinor, whole = true)) { onCategory(l.category.id) }
                }
                if (rest > 0) LegendRow(MaterialTheme.colorScheme.outline, "Everything else", money(rest, whole = true))
            }
        }
    }
}

/** Last 6 months of spending as bars, this month highlighted. */
@Composable
fun MonthsCard(months: List<Pair<YearMonth, Long>>) {
    if (months.size < 2 || months.all { it.second == 0L }) return
    val avg = months.dropLast(1).map { it.second }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 6 months", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (avg > 0) {
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)))
                Spacer(Modifier.width(6.dp))
                Text("avg ${money(avg, whole = true)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BarChart(months.map { it.second }, months.map { it.first.format(shortMonth) }, MaterialTheme.colorScheme.primary, average = avg.takeIf { it > 0 })
    }
}

/** Upcoming bills as a horizontal strip of small cards. */
@Composable
fun BillsStrip(bills: List<UpcomingBill>, onSeeAll: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(bills, key = { "${it.charge.id}-${it.dueAt}" }) { b ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = com.emotionalleaves.sprout.ui.common.cardBorder(), modifier = Modifier.width(156.dp).clickable(onClick = onSeeAll)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.emotionalleaves.sprout.ui.bills.DateBadge(b.dueAt)
                    Text(b.charge.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(money(b.charge.typicalAmountMinor), style = MaterialTheme.typography.titleMedium)
                    Text(com.emotionalleaves.sprout.ui.bills.dueText(b.dueAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun typeIcon(t: AccountType) = when (t) {
    AccountType.CREDIT -> Icons.Rounded.CreditCard
    AccountType.SAVINGS -> Icons.Rounded.Savings
    AccountType.INVESTMENT -> Icons.Rounded.TrendingUp
    AccountType.LOAN -> Icons.Rounded.TrendingDown
    else -> Icons.Rounded.AccountBalance
}

/** Accounts in one card, each with a type icon, balance and freshness. */
@Composable
fun AccountsCard(accounts: List<AccountEntity>, lastSuccess: Long?) {
    val c = LocalSproutColors.current
    val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
    SoftCard {
        accounts.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 52.dp).fillMaxWidth()
                    .clickable(onClickLabel = "Open ${a.label}") { navigate("account/" + android.net.Uri.encode(a.id)) },
            ) {
                IconBadge(typeIcon(a.type), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = buildString {
                        append(a.type.label)
                        if (!a.includeInTotals) append(" · not in total")
                        if (a.source == AccountSource.SIMPLEFIN) a.lastSyncedAt?.let { append(" · ").append(Format.ago(it, System.currentTimeMillis() / 1000)) }
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (a.source == AccountSource.SIMPLEFIN && AccountTypes.isStale(a.lastSyncedAt, lastSuccess)) {
                        Text("Not updating — closed? Hide it in Settings.", style = MaterialTheme.typography.labelSmall, color = c.warning)
                    }
                    a.lastErrorMessage?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, contentDescription = "Account warning", tint = c.warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
                Text(
                    if (AccountTypes.isUsd(a.currency)) money(a.balanceMinor) else "${a.balanceMinor / 100} ${AccountTypes.currencyLabel(a.currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (a.balanceMinor < 0) c.danger else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Box for screen-wide content padding at the end of lists, so the last card clears the nav bar. */
@Composable
fun BottomSpace() = Box(Modifier.size(24.dp))


/** Hero for Flex mode: what's left of the one Flex amount (after planned set-asides), per day, and the ring. */
@Composable
fun FlexHero(fx: com.emotionalleaves.sprout.data.budget.FlexState) {
    val c = LocalSproutColors.current
    val m = fx.month
    val used = if (m.flexBudgetMinor > 0) m.flexSpentMinor.toFloat() / m.flexBudgetMinor else 0f
    HeroCard(c.hero) {
        HeroRow(ring = { ringSize ->
            ProgressRing(used, color = c.onHero, track = c.onHero.copy(alpha = 0.18f), overColor = Color(0xFFFFB4A9), modifier = Modifier.size(ringSize), stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(used * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                    Text("used", style = MaterialTheme.typography.labelSmall, color = c.onHero.copy(alpha = 0.8f))
                }
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Safe to spend · Flex", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                Text(com.emotionalleaves.sprout.ui.common.animatedMoney(m.safeMinor), style = MaterialTheme.typography.displayMedium, color = if (m.safeMinor < 0) Color(0xFFFFB4A9) else c.onHero, maxLines = 1)
                m.perDayMinor?.let { Text("≈ ${money(it)} a day", style = MaterialTheme.typography.titleMedium, color = c.onHero) }
                Box(Modifier.padding(top = 6.dp)) {
                    com.emotionalleaves.sprout.ui.common.HeroPill("${money(m.flexSpentMinor, whole = true)} of ${money(m.flexBudgetMinor, whole = true)} spent", c.onHero)
                }
            }
        }
    }
}


/** YNAB's Age of Money, in one line. */
@Composable
fun AgeOfMoneyRow(days: Int) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Savings, MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Age of money: $days day${if (days == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        days >= 30 -> "You're spending money from over a month ago. That's a real buffer."
                        days >= 14 -> "Getting there: aim for 30 days so this month runs on last month's pay."
                        else -> "Money gets spent soon after it arrives. Building a buffer helps."
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Pinned categories and merchants with this month's totals. */
@Composable
fun WatchStrip(items: List<Triple<String, String, Long>>, navigate: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.first }) { (key, name, total) ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = com.emotionalleaves.sprout.ui.common.cardBorder(),
                modifier = Modifier.width(150.dp).clickable {
                    if (key.startsWith("c:")) navigate("category/" + key.drop(2)) else navigate("merchant/" + android.net.Uri.encode(key.drop(2)))
                }) {
                Column(Modifier.padding(14.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(money(total, whole = true), style = MaterialTheme.typography.titleLarge)
                    Text("this month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Shortcuts to the deeper screens. */
@Composable
fun QuickLinks(navigate: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val links = listOf("Challenges" to "challenges", "Reports" to "reports", "Year in review" to "year-review", "Net worth" to "networth", "Tags" to "tags", "Debt plan" to "debt", "Goals" to "goals")
        items(links, key = { it.second }) { (label, route) ->
            androidx.compose.material3.AssistChip(onClick = { navigate(route) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 44.dp))
        }
    }
}


/** The Home cards you can hide, in the order they appear. */
val HomeCards = listOf(
    "stats" to "Quick stats", "health" to "Money health", "challenge" to "Challenge", "age" to "Age of money", "watch" to "Watchlist",
    "links" to "Shortcuts", "insights" to "Insights", "payday" to "Until payday", "forecast" to "Month-end forecast", "where" to "Where it went", "months" to "Last 6 months",
    "calendar" to "Spending calendar", "bills" to "Coming up", "goals" to "Savings goals", "accounts" to "Accounts",
)

/** Show or hide Home cards; Safe to spend and Recent always stay. */
@Composable
fun CustomizeHomeDialog(hidden: Set<String>, onToggle: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Home") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("Turn off what you don't need. Fewer cards, calmer Home.", style = MaterialTheme.typography.bodySmall)
                HomeCards.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onToggle(k, k in hidden) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = k !in hidden, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** "Until payday": cash now, bills due before the next paycheck, and what's left per day. Tap for the bill list. */
@Composable
fun PaydayCard(p: com.emotionalleaves.sprout.domain.forecast.PaydayPlan, onPickSource: (String?) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    val colors = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
    com.emotionalleaves.sprout.ui.common.SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Until payday", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (p.short) "Short " + com.emotionalleaves.sprout.ui.common.money(-p.leftMinor, whole = true)
                    else com.emotionalleaves.sprout.ui.common.money(p.perDayMinor, whole = true) + " a day",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (p.short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${p.daysUntil} day${if (p.daysUntil == 1) "" else "s"} to ${p.payName} on " +
                        p.payday.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tone = if (p.short) MaterialTheme.colorScheme.error else colors.income
            androidx.compose.foundation.layout.Box(
                Modifier.size(56.dp).background(tone.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.daysUntil}", style = MaterialTheme.typography.titleMedium, color = tone)
                    Text(if (p.daysUntil == 1) "day" else "days", style = MaterialTheme.typography.labelSmall, color = tone)
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PaydayLine("Spending money now", p.cashMinor)
                p.bills.forEach { b -> PaydayLine(b.name + " · " + b.date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")), -kotlin.math.abs(b.amountMinor)) }
                androidx.compose.material3.HorizontalDivider()
                PaydayLine("Left until payday", p.leftMinor, bold = true)
                if (p.sources.size > 1) {
                    Text("Which paycheck is payday?", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.sources.forEach { name ->
                            androidx.compose.material3.FilterChip(selected = name == p.payName, onClick = { onPickSource(name) }, label = { Text(name) })
                        }
                    }
                }
                Text("Checking and cash, not savings. Bills are the ones Sprout found repeating; payday's own bills come out of the new paycheck.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PaydayLine(label: String, minor: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(com.emotionalleaves.sprout.ui.common.money(minor), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

/** A calm monthly nudge to save an encrypted backup file somewhere outside the phone. */
@Composable
fun BackupNudge(lastAt: Long?, onBackup: () -> Unit, onLater: () -> Unit) {
    com.emotionalleaves.sprout.ui.common.SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.emotionalleaves.sprout.ui.common.IconBadge(androidx.compose.material.icons.Icons.Rounded.CloudUpload, MaterialTheme.colorScheme.primary)
            Text("Back up your budget", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            if (lastAt == null) "Save an encrypted copy to Drive or your Files, so a lost phone doesn't mean lost history."
            else "Your last backup was ${java.time.Instant.ofEpochSecond(lastAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}. Save a fresh one?",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = onBackup, modifier = Modifier.heightIn(min = 44.dp)) { Text("Back up") }
            androidx.compose.material3.TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 44.dp)) { Text("In 2 weeks") }
        }
    }
}

/** The top of an account's page: balance, kind, freshness, a 6-month balance line, and this month's in/out. */
@Composable
fun AccountHeader(id: String, onBack: () -> Unit) {
    val app = com.emotionalleaves.sprout.ui.common.appContainer()
    val accounts by androidx.compose.runtime.remember { app.db.accounts().observeAll() }
        .collectAsState(initial = emptyList<com.emotionalleaves.sprout.data.db.AccountEntity>())
    val a = accounts.firstOrNull { it.id == id }
    val h = com.emotionalleaves.sprout.ui.common.rememberLoaded(id to a?.balanceMinor) { runCatching { app.insightsRepository.account(id) }.getOrNull() }
    val c = LocalSproutColors.current
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(a?.label ?: "Account", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a != null) {
                    Text(
                        a.type.label +
                            (a.lastSyncedAt?.let { " · updated " + Format.ago(it, System.currentTimeMillis() / 1000) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (a != null) Text(money(a.balanceMinor), style = MaterialTheme.typography.titleLarge,
                color = if (a.balanceMinor < 0) c.danger else MaterialTheme.colorScheme.onSurface)
            if (a != null) AccountMenu(a, onHidden = onBack)
        }
        val pts = h?.points.orEmpty()
        if (pts.size >= 2) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                com.emotionalleaves.sprout.ui.common.LineChart(pts.map { it.second }, MaterialTheme.colorScheme.primary, Modifier.height(96.dp))
            }
        }
        if (h != null) {
            com.emotionalleaves.sprout.ui.common.TileRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                StatTile("In this month", money(h.inMinor, whole = true), Modifier.tile(), noteColor = c.income, note = "deposits")
                StatTile("Out this month", money(h.outMinor, whole = true), Modifier.tile(), note = "charges & payments")
            }
        }
    }
}

/** Rename, count in total, or hide an account right from its page. */
@Composable
private fun AccountMenu(a: com.emotionalleaves.sprout.data.db.AccountEntity, onHidden: () -> Unit) {
    val app = com.emotionalleaves.sprout.ui.common.appContainer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            Icon(androidx.compose.material.icons.Icons.Rounded.MoreVert, contentDescription = "Account options")
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; renaming = true })
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (a.includeInTotals) "Leave out of total balance" else "Count in total balance") },
                onClick = { open = false; scope.launch { app.db.accounts().setIncludeInTotals(a.id, !a.includeInTotals) } },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Hide this account") },
                onClick = { open = false; scope.launch { app.db.accounts().setHidden(a.id, true); onHidden() } },
            )
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(a.label) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename account") },
            text = { androidx.compose.material3.OutlinedTextField(name, { name = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    renaming = false
                    scope.launch { app.db.accounts().rename(a.id, name.trim().take(60).ifBlank { null }?.takeIf { it != a.name }) }
                }) { Text("Save") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
