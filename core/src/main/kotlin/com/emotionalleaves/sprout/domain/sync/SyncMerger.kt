package com.emotionalleaves.sprout.domain.sync

import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.data.simplefin.SfTransaction
import java.time.Duration
import kotlin.math.abs

/** A transaction as stored on the device. Mirrors the Room entity, kept here so merge logic stays pure. */
data class StoredTx(
    val id: String,
    val accountKey: String,
    val remoteId: String,
    val amountMinor: Long,
    val description: String,
    val merchantKey: String,
    /** Epoch seconds; 0 while pending. */
    val postedAt: Long,
    val transactedAt: Long?,
    val pending: Boolean,
    /** The time used for display and month assignment. */
    val effectiveAt: Long,
    val categoryId: Long?,
    val categorySource: CategorySource,
    val note: String?,
    val alertedAt: Long?,
    val createdAt: Long,
    val excludedFromBudget: Boolean = false,
)

data class MergeResult(
    val upserts: List<StoredTx>,
    val deleteIds: List<String>,
    val newCount: Int,
    val updatedCount: Int,
)

/**
 * Merges one account's fresh SimpleFIN transactions into what is stored. Pure: no database, no clock.
 *
 * - Primary key "{accountKey}:{remoteTxId}" makes repeated syncs idempotent.
 * - Updates never overwrite a USER or MERCHANT_RULE category, and never touch notes.
 * - A newly posted transaction takes over the category, note and alert state of the matching pending one.
 * - Pending transactions inside the requested window that the server no longer returns are removed.
 */
class SyncMerger(
    private val resolver: CategoryResolver,
    /** Category ids where a pending amount may change when it posts (tips): Eating Out. */
    private val tipCategoryIds: Set<Long>,
) {

    fun mergeAccount(
        accountKey: String,
        incoming: List<SfTransaction>,
        /** Stored transactions with the same ids as [incoming], plus every stored pending one for this account. */
        existing: List<StoredTx>,
        window: SyncWindow,
        syncTime: Long,
        /** False when the server reported an error for this account: never delete on incomplete data. */
        allowPendingCleanup: Boolean,
    ): MergeResult {
        val byId = existing.associateBy { it.id }.toMutableMap()
        val upserts = linkedMapOf<String, StoredTx>()
        val deletes = linkedSetOf<String>()
        var newCount = 0
        var updatedCount = 0

        val incomingIds = incoming.map { Ids.transactionId(accountKey, it.id) }.toSet()
        // Pending rows that a posted transaction could replace: stored, pending, not returned again under their own id.
        val pendingPool = existing
            .filter { it.pending && it.accountKey == accountKey && it.id !in incomingIds }
            .toMutableList()

        // Process posted first so pending matches are claimed in posting order; stable for equal times.
        val ordered = incoming.sortedWith(compareBy<SfTransaction> { it.pending }.thenBy { it.postedAt })

        for (tx in ordered) {
            val id = Ids.transactionId(accountKey, tx.id)
            val merchantKey = MerchantNormalizer.normalize(tx.description)
            val effectiveAt = effectiveTime(tx, syncTime)
            val old = byId[id]

            if (old != null) {
                val keepCategory = old.categorySource == CategorySource.USER ||
                    old.categorySource == CategorySource.MERCHANT_RULE
                val resolved = if (keepCategory) null else resolver.resolve(tx.description, merchantKey, tx.amountMinor)
                val updated = old.copy(
                    amountMinor = tx.amountMinor,
                    description = tx.description,
                    merchantKey = merchantKey,
                    postedAt = tx.postedAt,
                    transactedAt = tx.transactedAt,
                    pending = tx.pending,
                    // A pending row without transacted_at keeps the time it was first seen, so it doesn't drift.
                    effectiveAt = if (tx.pending && tx.transactedAt == null) old.effectiveAt else effectiveAt,
                    categoryId = resolved?.categoryId ?: old.categoryId,
                    categorySource = resolved?.source ?: old.categorySource,
                )
                if (updated != old) {
                    upserts[id] = updated
                    byId[id] = updated
                    updatedCount++
                }
                continue
            }

            var fresh = run {
                val r = resolver.resolve(tx.description, merchantKey, tx.amountMinor)
                StoredTx(
                    id = id, accountKey = accountKey, remoteId = tx.id, amountMinor = tx.amountMinor,
                    description = tx.description, merchantKey = merchantKey, postedAt = tx.postedAt,
                    transactedAt = tx.transactedAt, pending = tx.pending, effectiveAt = effectiveAt,
                    categoryId = r.categoryId, categorySource = r.source, note = null, alertedAt = null,
                    createdAt = syncTime,
                )
            }

            if (!tx.pending) {
                val match = findPendingMatch(fresh, pendingPool)
                if (match != null) {
                    pendingPool.remove(match)
                    deletes += match.id
                    fresh = fresh.copy(
                        categoryId = match.categoryId,
                        categorySource = match.categorySource,
                        note = match.note,
                        alertedAt = match.alertedAt,
                        createdAt = match.createdAt,
                        excludedFromBudget = match.excludedFromBudget,
                    )
                }
            }
            upserts[id] = fresh
            byId[id] = fresh
            newCount++
        }

        if (allowPendingCleanup) {
            for (p in pendingPool) {
                if (p.id in deletes) continue
                if (p.effectiveAt >= window.startDate && p.effectiveAt < window.endDate) deletes += p.id
            }
        }
        deletes.removeAll(upserts.keys)
        return MergeResult(upserts.values.toList(), deletes.toList(), newCount, updatedCount)
    }

    private fun findPendingMatch(posted: StoredTx, pool: List<StoredTx>): StoredTx? =
        pool.filter { p ->
            p.merchantKey == posted.merchantKey &&
                amountMatches(p, posted) &&
                abs(posted.postedAt - p.effectiveAt) <= PENDING_MATCH_WINDOW.seconds
        }.minWithOrNull(
            compareBy<StoredTx> { abs(it.amountMinor - posted.amountMinor) }
                .thenBy { abs(posted.postedAt - it.effectiveAt) },
        )

    private fun amountMatches(pending: StoredTx, posted: StoredTx): Boolean {
        if (pending.amountMinor == posted.amountMinor) return true
        val isTipCategory = pending.categoryId in tipCategoryIds || posted.categoryId in tipCategoryIds
        if (!isTipCategory) return false
        if (pending.amountMinor == 0L || (pending.amountMinor < 0) != (posted.amountMinor < 0)) return false
        // Within ±25% of the pending amount, using integer math only.
        return abs(posted.amountMinor - pending.amountMinor) * 4 <= abs(pending.amountMinor)
    }

    companion object {
        val PENDING_MATCH_WINDOW: Duration = Duration.ofDays(7)

        /** Posted: the posted time. Pending: transacted_at, else the sync time. */
        fun effectiveTime(tx: SfTransaction, syncTime: Long): Long =
            if (!tx.pending && tx.postedAt > 0) tx.postedAt else tx.transactedAt ?: syncTime
    }
}
