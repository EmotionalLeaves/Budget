package com.emotionalleaves.sprout.domain.debt

/** One debt: what you owe (positive cents), its yearly interest rate and the minimum monthly payment. */
data class Debt(val id: String, val name: String, val balanceMinor: Long, val aprPercent: Double, val minPaymentMinor: Long)

enum class Strategy {
    /** Highest interest first: least interest paid. */
    AVALANCHE,
    /** Smallest balance first: quickest wins. */
    SNOWBALL,
}

data class Payoff(val id: String, val name: String, val month: Int)

data class DebtPlan(
    /** Months until everything is paid off; null if the payment never catches up with interest (within 50 years). */
    val months: Int?,
    val totalInterestMinor: Long,
    val totalPaidMinor: Long,
    val order: List<Payoff>,
    /** The monthly payment is below the sum of minimums. */
    val belowMinimums: Boolean,
    /** Total still owed at the start and after each month, for the payoff chart. */
    val remainingByMonth: List<Long> = emptyList(),
)

/**
 * A payoff plan: every month each debt accrues interest (APR / 12) and gets its minimum; whatever is left of
 * [monthlyPaymentMinor] goes to the target debt (by [strategy]). When a debt is paid off its minimum rolls into
 * the next one. This is a planner only: Sprout never makes payments.
 */
object DebtPlanner {
    private const val MAX_MONTHS = 600

    fun plan(debts: List<Debt>, monthlyPaymentMinor: Long, strategy: Strategy): DebtPlan {
        val open = debts.filter { it.balanceMinor > 0 }
        if (open.isEmpty()) return DebtPlan(0, 0, 0, emptyList(), false)
        val minimums = open.sumOf { it.minPaymentMinor }
        val balances = open.associate { it.id to it.balanceMinor.toDouble() }.toMutableMap()
        var interest = 0.0
        var paid = 0.0
        val order = mutableListOf<Payoff>()
        var month = 0
        val remaining = mutableListOf(Math.round(balances.values.sum()))
        while (balances.values.any { it > 0.5 } && month < MAX_MONTHS) {
            month++
            open.forEach { d ->
                val b = balances.getValue(d.id)
                if (b > 0.5) {
                    val i = b * d.aprPercent / 100.0 / 12.0
                    interest += i
                    balances[d.id] = b + i
                }
            }
            var budget = monthlyPaymentMinor.toDouble()
            // Minimums first.
            open.forEach { d ->
                val b = balances.getValue(d.id)
                if (b > 0.5 && budget > 0) {
                    val p = minOf(b, d.minPaymentMinor.toDouble(), budget)
                    balances[d.id] = b - p; budget -= p; paid += p
                }
            }
            // The rest to the target, then the next, until it runs out.
            val targets = open.filter { balances.getValue(it.id) > 0.5 }.let { live ->
                when (strategy) {
                    Strategy.AVALANCHE -> live.sortedWith(compareByDescending<Debt> { it.aprPercent }.thenBy { balances.getValue(it.id) })
                    Strategy.SNOWBALL -> live.sortedWith(compareBy<Debt> { balances.getValue(it.id) }.thenByDescending { it.aprPercent })
                }
            }
            for (d in targets) {
                if (budget <= 0) break
                val b = balances.getValue(d.id)
                val p = minOf(b, budget)
                balances[d.id] = b - p; budget -= p; paid += p
            }
            open.forEach { d -> if (balances.getValue(d.id) <= 0.5 && order.none { it.id == d.id }) order += Payoff(d.id, d.name, month) }
            remaining += Math.round(balances.values.sumOf { maxOf(it, 0.0) })
        }
        val done = balances.values.all { it <= 0.5 }
        return DebtPlan(if (done) month else null, Math.round(interest), Math.round(paid), order, monthlyPaymentMinor < minimums, remaining)
    }
}
