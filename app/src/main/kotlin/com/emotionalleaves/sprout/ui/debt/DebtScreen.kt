package com.emotionalleaves.sprout.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.debt.Debt
import com.emotionalleaves.sprout.domain.debt.DebtPlanner
import com.emotionalleaves.sprout.domain.debt.Strategy
import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.HeroCard
import com.emotionalleaves.sprout.ui.common.SoftCard
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.theme.LocalSproutColors
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

private fun typed(minor: Long?) = minor?.let { (it / 100).toString() + if (it % 100 != 0L) "." + "%02d".format(it % 100) else "" } ?: ""

/** Plan paying off credit cards and loans: avalanche or snowball, debt-free date and interest. Planning only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(onBack: () -> Unit) {
    val c = appContainer()
    val store = remember { com.emotionalleaves.sprout.data.debt.DebtStore(c.appContext) }
    val accounts by remember { c.db.accounts().observeVisible() }.collectAsStateWithLifecycle(emptyList())
    val inputs by store.inputs.collectAsStateWithLifecycle(emptyMap())
    val savedMonthly by store.monthlyPayment.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var strategy by rememberSaveable { mutableStateOf(Strategy.AVALANCHE) }
    val debtAccounts = accounts.filter { (it.type == AccountType.CREDIT || it.type == AccountType.LOAN) && it.balanceMinor < 0 }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Debt payoff plan") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        if (debtAccounts.isEmpty()) {
            EmptyState("No debt to plan", "Credit cards and loans with a balance owed show up here. Nice work if that's because you owe nothing!")
            return@Column
        }
        val debts = debtAccounts.map { a ->
            val i = inputs[a.id]
            val owed = -a.balanceMinor
            Debt(a.id, a.label, owed, i?.aprPercent ?: if (a.type == AccountType.CREDIT) 22.0 else 7.0, i?.minPaymentMinor ?: maxOf(2_500L, owed / 50))
        }
        var monthlyText by remember(savedMonthly) { mutableStateOf(typed(savedMonthly ?: debts.sumOf { it.minPaymentMinor } + 10_000)) }
        val monthly = Money.parseUserInput(monthlyText)?.takeIf { it > 0 } ?: 0L
        val plan = remember(debts, monthly, strategy) { DebtPlanner.plan(debts, monthly, strategy) }
        val minOnly = remember(debts) { DebtPlanner.plan(debts, debts.sumOf { it.minPaymentMinor }, strategy) }
        val sc = LocalSproutColors.current
        LazyColumn {
            item {
                HeroCard(sc.hero) {
                    Text("Debt-free", style = MaterialTheme.typography.labelLarge, color = sc.onHero.copy(alpha = 0.8f))
                    Text(
                        plan.months?.let { YearMonth.now().plusMonths(it.toLong()).format(monthYear) } ?: "Not at this payment",
                        style = MaterialTheme.typography.headlineMedium, color = sc.onHero,
                    )
                    plan.months?.let { m ->
                        Text("$m months · ${Money.formatWhole(plan.totalInterestMinor)} interest", style = MaterialTheme.typography.bodyMedium, color = sc.onHero)
                        val saved = minOnly.totalInterestMinor - plan.totalInterestMinor
                        if (minOnly.months != null && saved > 0) {
                            Text("Saves ${Money.formatWhole(saved)} in interest vs paying only the minimums", style = MaterialTheme.typography.bodySmall, color = sc.onHero.copy(alpha = 0.85f))
                        }
                    }
                    if (plan.belowMinimums) Text("That's less than the minimums (${Money.format(debts.sumOf { it.minPaymentMinor })}).", style = MaterialTheme.typography.bodySmall, color = sc.onHero)
                }
            }
            item {
                SoftCard {
                    OutlinedTextField(
                        value = monthlyText,
                        onValueChange = { t -> monthlyText = t.take(10); Money.parseUserInput(t)?.let { v -> scope.launch { store.setMonthly(v) } } },
                        label = { Text("Total I can pay each month") }, prefix = { Text("$") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Strategy.entries.forEachIndexed { i, s ->
                            SegmentedButton(strategy == s, { strategy = s }, SegmentedButtonDefaults.itemShape(i, 2)) {
                                Text(if (s == Strategy.AVALANCHE) "Least interest" else "Quick wins")
                            }
                        }
                    }
                    Text(
                        if (strategy == Strategy.AVALANCHE) "Extra money goes to the highest interest rate first." else "Extra money goes to the smallest balance first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (plan.order.isNotEmpty()) {
                item { com.emotionalleaves.sprout.ui.common.SectionHeader("Order") }
                items(plan.order, key = { "o-" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("paid off ${YearMonth.now().plusMonths(p.month.toLong()).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))}",
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { com.emotionalleaves.sprout.ui.common.SectionHeader("Your debts") }
            items(debts, key = { it.id }) { d -> DebtRow(d) { apr, min -> scope.launch { store.set(d.id, apr, min) } } }
            item {
                Text("A plan only: Sprout never makes payments. Rates and minimums are your estimates; check your statements.",
                    Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DebtRow(d: Debt, onChange: (Double?, Long?) -> Unit) {
    var apr by remember(d.id) { mutableStateOf(d.aprPercent.toString().removeSuffix(".0")) }
    var min by remember(d.id) { mutableStateOf(typed(d.minPaymentMinor)) }
    LaunchedEffect(apr, min) {
        kotlinx.coroutines.delay(500)
        onChange(apr.toDoubleOrNull()?.coerceIn(0.0, 99.0), Money.parseUserInput(min)?.takeIf { it >= 0 })
    }
    SoftCard {
        Row {
            Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(Money.format(d.balanceMinor) + " owed", style = MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(apr, { apr = it.take(5) }, label = { Text("Interest %/yr") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(min, { min = it.take(8) }, label = { Text("Minimum") }, prefix = { Text("$") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
    }
}
