package com.emotionalleaves.sprout.data.manual

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Transactions you add by hand (cash, a friend you paid back). They live in manual accounts, which Sprout keeps
 * the balance of itself. Bank-synced transactions can't be edited or deleted here: the bank is the source of truth.
 */
class ManualRepository(private val db: SproutDatabase, private val clock: Clock, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    companion object {
        const val CASH_ACCOUNT_ID = "manual:cash"
        fun isManual(transactionId: String) = transactionId.startsWith("manual:")
    }

    /** The "Cash" wallet, made the first time you need it. */
    suspend fun ensureCashAccount(): String = withContext(Dispatchers.IO) {
        if (db.accounts().getByIds(listOf(CASH_ACCOUNT_ID)).isEmpty()) {
            val now = clock.instant().epochSecond
            db.accounts().upsert(listOf(AccountEntity(
                id = CASH_ACCOUNT_ID, source = AccountSource.MANUAL, remoteId = null, connId = null, name = "Cash", displayName = null,
                type = AccountType.CASH, currency = "USD", balanceMinor = 0, availableBalanceMinor = null, balanceDate = now,
                includeInTotals = true, hidden = false, firstSyncCompleted = true, lastSyncedAt = now, createdAt = now,
            )))
        }
        CASH_ACCOUNT_ID
    }

    /**
     * Adds a transaction. [amountMinor] is signed: negative for spending, positive for money in.
     * Returns its id. Manual entries count as already reviewed (you just entered them).
     */
    suspend fun add(accountId: String, amountMinor: Long, description: String, categoryId: Long, date: LocalDate, note: String?): String =
        withContext(Dispatchers.IO) {
            val now = clock.instant().epochSecond
            val at = date.atTime(java.time.LocalTime.now(clock.withZone(zone()))).atZone(zone()).toEpochSecond()
            val desc = Sanitize.text(description, 120).ifBlank { if (amountMinor < 0) "Cash spending" else "Cash in" }
            val id = "manual:" + UUID.randomUUID()
            db.withTransaction {
                db.transactions().upsert(listOf(TransactionEntity(
                    id = id, accountId = accountId, remoteId = id.removePrefix("manual:"), postedAt = at, transactedAt = at, occurredAt = at,
                    amountMinor = amountMinor, currency = "USD", description = desc, merchantKey = MerchantNormalizer.normalize(desc),
                    pending = false, categoryId = categoryId, categorySource = CategorySource.USER, note = note?.let { Sanitize.text(it, 500) }?.ifBlank { null },
                    excludedFromBudget = false, recurringChargeId = null, alertedAt = now, importedAt = now, updatedAt = now, reviewedAt = now,
                )))
                db.accounts().recomputeManualBalance(accountId, now)
            }
            id
        }

    /** Fixes a transaction you added by hand: amount (signed), what it was, and its date. */
    suspend fun update(transactionId: String, amountMinor: Long, description: String, date: LocalDate): Boolean = withContext(Dispatchers.IO) {
        if (!isManual(transactionId)) return@withContext false
        val tx = db.transactions().getById(transactionId) ?: return@withContext false
        val now = clock.instant().epochSecond
        val old = java.time.Instant.ofEpochSecond(tx.occurredAt).atZone(zone())
        val at = date.atTime(old.toLocalTime()).atZone(zone()).toEpochSecond()
        val desc = Sanitize.text(description, 120).ifBlank { tx.description }
        db.withTransaction {
            db.transactions().upsert(listOf(tx.copy(amountMinor = amountMinor, description = desc, merchantKey = MerchantNormalizer.normalize(desc),
                postedAt = at, transactedAt = at, occurredAt = at, updatedAt = now)))
            db.accounts().recomputeManualBalance(tx.accountId, now)
        }
        true
    }

    /** Deletes a transaction you added by hand (never a synced one). Returns false if it isn't manual. */
    /**
     * Deletes a hand-added transaction and returns an undo that puts it back with its split, tags and note,
     * or null when it isn't one you added (bank transactions can't be deleted).
     */
    suspend fun deleteWithUndo(transactionId: String): (suspend () -> Unit)? = withContext(Dispatchers.IO) {
        if (!isManual(transactionId)) return@withContext null
        val tx = db.transactions().getById(transactionId) ?: return@withContext null
        val splits = db.splits().getFor(transactionId)
        val tagIds = db.tags().tagIdsFor(transactionId)
        if (!delete(transactionId)) return@withContext null
        val undo: suspend () -> Unit = {
            withContext(Dispatchers.IO) {
                db.withTransaction {
                    if (db.transactions().getById(tx.id) == null && db.accounts().getByIds(listOf(tx.accountId)).isNotEmpty()) {
                        db.transactions().upsert(listOf(tx))
                        if (splits.isNotEmpty()) db.splits().insert(splits.map { it.copy(id = 0) })
                        val liveTags = tagIds.filter { db.tags().getById(it) != null }
                        liveTags.forEach { db.tags().attach(com.emotionalleaves.sprout.data.db.TransactionTagEntity(tx.id, it)) }
                        db.accounts().recomputeManualBalance(tx.accountId, clock.instant().epochSecond)
                    }
                }
            }
        }
        undo
    }

    suspend fun delete(transactionId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isManual(transactionId)) return@withContext false
        val tx = db.transactions().getById(transactionId) ?: return@withContext false
        db.withTransaction {
            db.transactions().deleteByIds(listOf(transactionId))
            db.accounts().recomputeManualBalance(tx.accountId, clock.instant().epochSecond)
        }
        true
    }
}
