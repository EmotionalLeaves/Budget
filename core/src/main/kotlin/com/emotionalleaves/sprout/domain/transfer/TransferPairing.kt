package com.emotionalleaves.sprout.domain.transfer

import kotlin.math.abs

/**
 * Finds money moving between your own accounts: -$250.00 out of checking and +$250.00 into savings a day
 * later is one transfer, not spending plus income. Both sides then go to Transfers so they don't count
 * against a budget or as income.
 *
 * Deliberately cautious: only rows Sprout couldn't place anyway (fallback Income/Uncategorized, or already
 * Transfers by a built-in rule) are paired; the amounts must match to the cent, the accounts must differ,
 * and the two must be within [MAX_GAP_SECONDS] of each other. Anything you or a rule categorized is never
 * touched.
 */
object TransferPairing {
    data class Row(val id: String, val accountId: String, val amountMinor: Long, val at: Long)

    /** Banks can take a few business days (a Friday transfer may land Tuesday). */
    const val MAX_GAP_SECONDS = 4L * 24 * 3600
    /** Below $1, matching amounts are too likely to be coincidence (fees, rounding, tests from a bank). */
    const val MIN_ABS_MINOR = 100L

    /** Pairs of (money out id, money in id). Each row is used at most once; the closest in time wins. */
    fun pairs(rows: List<Row>): List<Pair<String, String>> {
        val inflows = rows.filter { it.amountMinor >= MIN_ABS_MINOR }.groupBy { it.amountMinor }
        val used = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (o in rows.filter { it.amountMinor <= -MIN_ABS_MINOR }.sortedBy { it.at }) {
            val match = inflows[-o.amountMinor].orEmpty()
                .filter { it.id !in used && it.accountId != o.accountId && abs(it.at - o.at) <= MAX_GAP_SECONDS }
                .minByOrNull { abs(it.at - o.at) }
                ?: continue
            used += match.id
            used += o.id
            out += o.id to match.id
        }
        return out
    }
}
