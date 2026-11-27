package com.emotionalleaves.sprout.domain.sync

/**
 * Finds the same bank account stored twice. It happens when a bank is connected again (a new setup token, or the
 * bank added twice in SimpleFIN Bridge): the second connection has a new id, so its accounts look new and every
 * transaction shows up twice, doubling spending.
 *
 * Two accounts are the same when they have the same name and most of their transactions match exactly (same day,
 * amount and description). The one that synced most recently is kept; the other is the duplicate.
 */
object DuplicateAccounts {
    data class Account(val id: String, val name: String, val lastSyncedAt: Long, val transactions: Int)

    /** Share of the smaller account's transactions that must have an exact twin in the other. */
    const val MIN_OVERLAP = 0.6
    /** Fewer matching transactions than this could be coincidence. */
    const val MIN_MATCHES = 3

    /** (duplicate id, kept id) pairs. [signatures] maps account id to its transactions' "day|amount|description". */
    fun find(accounts: List<Account>, signatures: Map<String, Set<String>>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val dropped = HashSet<String>()
        val byName = accounts.groupBy { it.name.trim().lowercase() }
        for ((_, group) in byName) {
            if (group.size < 2) continue
            // Best first: the most recently synced, then the one with more history.
            val sorted = group.sortedWith(compareByDescending<Account> { it.lastSyncedAt }.thenByDescending { it.transactions })
            for (i in sorted.indices) {
                val keep = sorted[i]
                if (keep.id in dropped) continue
                for (j in i + 1 until sorted.size) {
                    val dup = sorted[j]
                    if (dup.id in dropped) continue
                    val a = signatures[keep.id].orEmpty()
                    val b = signatures[dup.id].orEmpty()
                    val common = a.count { it in b }
                    val smaller = minOf(a.size, b.size)
                    if (smaller > 0 && common >= MIN_MATCHES && common.toDouble() / smaller >= MIN_OVERLAP) {
                        out += dup.id to keep.id
                        dropped += dup.id
                    }
                }
            }
        }
        return out
    }

    fun signature(day: Long, amountMinor: Long, description: String): String =
        "$day|$amountMinor|${description.trim().uppercase().replace(Regex("\\s+"), " ")}"
}
