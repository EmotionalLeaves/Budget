package com.emotionalleaves.sprout.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Savings
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
            Text("Total balance", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
            Text(money(netWorthMinor), style = MaterialTheme.typography.displayMedium, color = c.onHero)
            Text("Set budgets to see what's safe to spend each day.", style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.85f))
            Spacer(Modifier.size(4.dp))
            Button(
                onClick = onSetBudget,
                colors = ButtonDefaults.buttonColors(containerColor = c.onHero, contentColor = Color(0xFF0F5137)),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Set up budgets") }
        } else {
            val used = if (m.totalBudgetMinor > 0) m.totalSpentBudgetedMinor.toFloat() / m.totalBudgetMinor else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                    Text(
                        money(safe.amountMinor),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (safe.amountMinor < 0) Color(0xFFFFB4A9) else c.onHero,
                        maxLines = 1,
                    )
                    safe.perDayMinor?.let { Text("≈ ${money(it)} a day", style = MaterialTheme.typography.titleMedium, color = c.onHero) }
                    safe.daysRemaining?.let {
                        Text("$it day${if (it == 1) "" else "s"} left · tap for details", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.8f))
                    }
                }
                ProgressRing(
                    used, color = c.onHero, track = c.onHero.copy(alpha = 0.22f), overColor = Color(0xFFFFB4A9),
                    modifier = Modifier.size(104.dp), stroke = 11.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(used * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = c.onHero)
                        Text("used", style = MaterialTheme.typography.labelSmall, color = c.onHero.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
    if (breakdown && m != null) SafeToSpendBreakdown(m) { breakdown = false }
}

/** Three quick numbers: net worth, spent this month (vs last month), month-end cash. */
@Composable
fun StatsRow(netWorthMinor: Long, trend: CashFlow.Trend?, forecastEndMinor: Long?, onNetWorth: () -> Unit = {}) {
    val c = LocalSproutColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Net worth", money(netWorthMinor, whole = true), Modifier.weight(1f).clickable(onClick = onNetWorth), note = "see trend ›")
        val p = trend?.percent
        StatTile(
            "Spent", money(trend?.thisMonthMinor ?: 0, whole = true), Modifier.weight(1f),
            note = p?.let { if (it > 0) "↑ $it% vs last month" else if (it < 0) "↓ ${-it}% vs last month" else "Same as last month" },
            noteColor = if ((p ?: 0) > 10) c.warning else if ((p ?: 0) < 0) c.ok else Color.Unspecified,
        )
        if (forecastEndMinor != null) {
            StatTile("Month end", money(forecastEndMinor, whole = true), Modifier.weight(1f), note = "cash, estimated",
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
            if (avg > 0) Text("avg ${money(avg, whole = true)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BarChart(months.map { it.second }, months.map { it.first.format(shortMonth) }, MaterialTheme.colorScheme.primary)
    }
}

/** Upcoming bills as a horizontal strip of small cards. */
@Composable
fun BillsStrip(bills: List<UpcomingBill>, onSeeAll: () -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(bills, key = { "${it.charge.id}-${it.dueAt}" }) { b ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(156.dp).clickable(onClick = onSeeAll)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconBadge(CategoryIcons.forKey(null), MaterialTheme.colorScheme.secondary, 34.dp)
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
    SoftCard {
        accounts.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp)) {
                IconBadge(typeIcon(a.type), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = buildString {
                        append(a.type.name.lowercase().replaceFirstChar { it.uppercase() })
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Safe to spend · Flex", style = MaterialTheme.typography.labelLarge, color = c.onHero.copy(alpha = 0.8f))
                Text(money(m.safeMinor), style = MaterialTheme.typography.displayMedium, color = if (m.safeMinor < 0) Color(0xFFFFB4A9) else c.onHero, maxLines = 1)
                m.perDayMinor?.let { Text("≈ ${money(it)} a day", style = MaterialTheme.typography.titleMedium, color = c.onHero) }
                Text("${money(m.flexSpentMinor)} of ${money(m.flexBudgetMinor)} flex spent", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.8f))
            }
            ProgressRing(used, color = c.onHero, track = c.onHero.copy(alpha = 0.22f), overColor = Color(0xFFFFB4A9), modifier = Modifier.size(104.dp), stroke = 11.dp) {
                Text("${(used * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleLarge, color = c.onHero)
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
        val links = listOf("Reports" to "reports", "Year in review" to "year-review", "Net worth" to "networth", "Tags" to "tags", "Debt plan" to "debt", "Goals" to "goals")
        items(links, key = { it.second }) { (label, route) ->
            androidx.compose.material3.AssistChip(onClick = { navigate(route) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 44.dp))
        }
    }
}
