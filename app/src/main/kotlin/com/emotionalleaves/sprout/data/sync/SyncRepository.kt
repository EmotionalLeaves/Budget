package com.emotionalleaves.sprout.data.sync

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.ConnectionEntity
import com.emotionalleaves.sprout.data.db.ConnectionStatus
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.SyncLogEntity
import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.secure.StoredSecret
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.simplefin.AccessUrl
import com.emotionalleaves.sprout.data.simplefin.AccountSet
import com.emotionalleaves.sprout.data.simplefin.AccountsQuery
import com.emotionalleaves.sprout.data.simplefin.FetchResult
import com.emotionalleaves.sprout.data.simplefin.ProtocolMode
import com.emotionalleaves.sprout.data.simplefin.SimpleFinClient
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.UserRule
import com.emotionalleaves.sprout.domain.sync.Ids
import com.emotionalleaves.sprout.domain.sync.StoredTx
import com.emotionalleaves.sprout.domain.sync.SyncErrors
import com.emotionalleaves.sprout.domain.sync.SyncMerger
import com.emotionalleaves.sprout.domain.sync.SyncOutcome
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import com.emotionalleaves.sprout.domain.sync.SyncWindow
import com.emotionalleaves.sprout.domain.text.Redactor
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock

/** What a sync run means for the worker. */
sealed interface SyncRunResult {
    data class Done(val result: SyncResult, val newCount: Int, val updatedCount: Int) : SyncRunResult
    /** Temporary failure (network, 5xx, 429): the worker may retry with backoff. */
    data class RetryLater(val retryAfterSeconds: Long?) : SyncRunResult
    /** Nothing to do: no connection, access revoked, or the daily request budget is used up. */
    data class Skipped(val reason: SkipReason) : SyncRunResult
}

enum class SkipReason { NOT_CONNECTED, REVOKED, NEEDS_RECONNECT, DAILY_LIMIT }

/**
 * Runs one SimpleFIN sync: fetch, merge, store, log. Never on the main thread; one at a time.
 */
class SyncRepository(
    private val db: SproutDatabase,
    private val secureStore: SecureStore,
    private val settings: SettingsStore,
    private val client: SimpleFinClient,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Inserts the default categories once. Safe to call on every start. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (db.categories().count() > 0) return@withContext
        val rows = DefaultCategories.all.mapIndexed { i, c ->
            CategoryEntity(
                key = c.key, name = c.name, kind = c.kind, iconKey = c.key,
                colorArgb = c.colorArgb.toInt(), sortOrder = i, archived = false,
            )
        }
        db.categories().insertIgnore(rows)
    }

    suspend fun sync(trigger: SyncTrigger): SyncRunResult = withContext(Dispatchers.IO) {
        mutex.withLock { syncLocked(trigger) }
    }

    private suspend fun syncLocked(trigger: SyncTrigger): SyncRunResult {
        ensureSeeded()
        val now = clock.instant().epochSecond
        val s = settings.current()

        val access = when (val secret = secureStore.readAccessUrl()) {
            StoredSecret.None -> return SyncRunResult.Skipped(SkipReason.NOT_CONNECTED)
            StoredSecret.Unreadable -> {
                log(now, trigger, SyncResult.FAILED, codes = "secure.unreadable",
                    message = "Saved connection can't be read on this phone. Paste a new setup token to reconnect.")
                return SyncRunResult.Skipped(SkipReason.NEEDS_RECONNECT)
            }
            is StoredSecret.Present -> AccessUrl.parse(secret.accessUrl)
                ?: return SyncRunResult.Skipped(SkipReason.NEEDS_RECONNECT)
        }
        if (s.accessRevoked) return SyncRunResult.Skipped(SkipReason.REVOKED)

        val used = db.syncLog().requestsSince(now - 86_400)
        if (used + 2 > SyncPolicy.DAILY_REQUEST_BUDGET) {
            log(now, trigger, SyncResult.FAILED, codes = "local.daily_limit",
                message = "Paused to stay under SimpleFIN's daily limit. Sprout will sync again later.")
            return SyncRunResult.Skipped(SkipReason.DAILY_LIMIT)
        }

        settings.setLastSyncAttempt(now)
        val window = SyncPolicy.window(clock, s.lastSuccessfulSyncAt, s.firstSyncDays)
        val fetch = client.fetchAccounts(access, AccountsQuery(window.startDate, window.endDate, pending = true))

        return when (fetch) {
            is FetchResult.Success -> {
                val backfill = backfillNewAccounts(access, fetch.accountSet, s.lastSuccessfulSyncAt, s.firstSyncDays, used + fetch.requestCount)
                val merged = backfill?.let { mergeBackfill(fetch.accountSet, it.set) } ?: fetch.accountSet
                val windows = backfill?.let { b -> b.set.accounts.associate { Ids.accountKey(it.connId, it.id) to b.window } }.orEmpty()
                store(merged, { key -> windows[key] ?: window }, now, trigger, fetch.requestCount + (backfill?.requests ?: 0))
            }
            is FetchResult.AuthFailed -> {
                db.connections().setAllStatus(ConnectionStatus.REVOKED, "http.403",
                    "Your SimpleFIN access was turned off. Paste a new setup token to reconnect.", now)
                settings.setAccessRevoked(true)
                log(now, trigger, SyncResult.FAILED, codes = "http.403", requests = fetch.requestCount,
                    message = "SimpleFIN access was turned off (403).")
                SyncRunResult.Skipped(SkipReason.REVOKED)
            }
            is FetchResult.PaymentRequired -> {
                settings.setPaymentRequired(true)
                log(now, trigger, SyncResult.FAILED, codes = "http.402", requests = fetch.requestCount,
                    message = "Your SimpleFIN Bridge subscription needs attention (402).")
                SyncRunResult.Done(SyncResult.FAILED, 0, 0)
            }
            is FetchResult.ServerBusy -> {
                log(now, trigger, SyncResult.FAILED, codes = "http.${fetch.httpCode}", requests = fetch.requestCount,
                    message = "SimpleFIN is busy (${fetch.httpCode}). Will retry later.")
                SyncRunResult.RetryLater(fetch.retryAfterSeconds)
            }
            is FetchResult.HttpError -> {
                log(now, trigger, SyncResult.FAILED, codes = "http.${fetch.httpCode}", requests = fetch.requestCount,
                    message = "SimpleFIN answered with an unexpected error (${fetch.httpCode}).")
                SyncRunResult.Done(SyncResult.FAILED, 0, 0)
            }
            is FetchResult.NetworkError -> {
                log(now, trigger, SyncResult.FAILED, codes = "net", requests = fetch.requestCount,
                    message = "Couldn't reach SimpleFIN (${Redactor.redact(fetch.message)}).")
                SyncRunResult.RetryLater(null)
            }
            is FetchResult.ParseError -> {
                log(now, trigger, SyncResult.FAILED, codes = "parse", requests = fetch.requestCount,
                    message = "The bank data couldn't be read. Your saved data is unchanged.")
                SyncRunResult.Done(SyncResult.FAILED, 0, 0)
            }
        }
    }

    private class Backfill(val set: AccountSet, val window: SyncWindow, val requests: Int)

    /**
     * Accounts we've never stored (for example a bank added later in SimpleFIN Bridge) only got the short
     * incremental window. Fetch their full first-sync history with one extra, account-filtered request (v2 only),
     * if the daily request budget allows. Failures here are ignored: the next sync tries again.
     */
    private suspend fun backfillNewAccounts(
        access: AccessUrl, set: AccountSet, lastSuccess: Long?, firstSyncDays: Int, usedRequests: Int,
    ): Backfill? {
        if (lastSuccess == null || set.mode != ProtocolMode.V2 || set.accounts.isEmpty()) return null
        val keys = set.accounts.map { Ids.accountKey(it.connId, it.id) }
        val known = keys.chunked(500).flatMap { db.accounts().getByIds(it) }.filter { it.firstSyncCompleted }.map { it.id }.toSet()
        val fresh = set.accounts.filter { Ids.accountKey(it.connId, it.id) !in known }
        if (fresh.isEmpty() || usedRequests + 1 > SyncPolicy.DAILY_REQUEST_BUDGET) return null
        val window = SyncPolicy.window(clock, null, firstSyncDays)
        val r = client.fetchAccounts(access, AccountsQuery(window.startDate, window.endDate, pending = true, accountIds = fresh.map { it.id }.distinct()))
        return when (r) {
            is FetchResult.Success -> {
                val wanted = fresh.map { Ids.accountKey(it.connId, it.id) }.toSet()
                Backfill(r.accountSet.copy(accounts = r.accountSet.accounts.filter { Ids.accountKey(it.connId, it.id) in wanted }), window, r.requestCount)
            }
            else -> null
        }
    }

    /** Uses the backfill's longer transaction history for the accounts it covers; keeps everything else. */
    private fun mergeBackfill(main: AccountSet, backfill: AccountSet): AccountSet {
        val byKey = backfill.accounts.associateBy { Ids.accountKey(it.connId, it.id) }
        val accounts = main.accounts.map { a ->
            val b = byKey[Ids.accountKey(a.connId, a.id)] ?: return@map a
            a.copy(transactions = (b.transactions + a.transactions).distinctBy { it.id })
        }
        return main.copy(accounts = accounts, errors = main.errors + backfill.errors.filter { it !in main.errors })
    }

    private suspend fun store(set: AccountSet, windowFor: (String) -> SyncWindow, now: Long, trigger: SyncTrigger, requests: Int): SyncRunResult {
        val plan = SyncErrors.plan(set)
        var newCount = 0
        var updatedCount = 0

        db.withTransaction {
            val categories = db.categories().getAll()
            val idByKey = categories.mapNotNull { c -> c.key?.let { it to c.id } }.toMap()
            val rules = db.merchantRules().getAll().map { UserRule(it.id, it.matchType, it.pattern, it.categoryId) }
            val tipIds = setOfNotNull(idByKey[DefaultCategories.EATING_OUT])
            val merger = SyncMerger(CategoryResolver(rules, idByKey), tipIds)
            val fallbackCategory = idByKey[DefaultCategories.UNCATEGORIZED]
                ?: categories.firstOrNull { it.kind == CategoryKind.EXPENSE }?.id
                ?: error("No categories")

            // Connections
            val oldConnections = db.connections().getAll().associateBy { it.connId }
            db.connections().upsert(set.connections.map { c ->
                val err = plan.connectionErrors[c.connId]
                ConnectionEntity(
                    connId = c.connId, name = c.name, orgId = c.orgId, orgUrl = c.orgUrl, sfinUrl = c.sfinUrl,
                    status = if (err != null) ConnectionStatus.NEEDS_ATTENTION else ConnectionStatus.OK,
                    lastErrorCode = err?.code,
                    lastErrorMessage = err?.let { Sanitize.text(it.msg.ifBlank { SyncErrors.explain(it.code) }, Sanitize.MAX_MESSAGE) },
                    updatedAt = now,
                )
            })
            // A connection named only in an error (not in "connections") still gets flagged if we know it.
            plan.connectionErrors.keys.filter { id -> set.connections.none { it.connId == id } }.forEach { id ->
                oldConnections[id]?.let { old ->
                    val e = plan.connectionErrors.getValue(id)
                    db.connections().upsert(listOf(old.copy(status = ConnectionStatus.NEEDS_ATTENTION, lastErrorCode = e.code,
                        lastErrorMessage = Sanitize.text(e.msg, Sanitize.MAX_MESSAGE), updatedAt = now)))
                }
            }

            // Accounts
            val accountIds = set.accounts.map { Ids.accountKey(it.connId, it.id) }
            val existingAccounts = if (accountIds.isEmpty()) emptyMap() else
                accountIds.chunked(500).flatMap { db.accounts().getByIds(it) }.associateBy { it.id }
            db.accounts().upsert(set.accounts.map { a ->
                val key = Ids.accountKey(a.connId, a.id)
                val old = existingAccounts[key]
                val err = plan.accountError(a.connId, a.id)
                AccountEntity(
                    id = key, source = AccountSource.SIMPLEFIN, remoteId = a.id, connId = a.connId,
                    name = a.name, displayName = old?.displayName,
                    type = old?.type ?: AccountTypes.infer(a.name, a.balanceMinor),
                    currency = a.currency, balanceMinor = a.balanceMinor, availableBalanceMinor = a.availableBalanceMinor,
                    balanceDate = a.balanceDate,
                    includeInTotals = old?.includeInTotals ?: AccountTypes.isUsd(a.currency),
                    hidden = old?.hidden ?: false,
                    firstSyncCompleted = true,
                    lastSyncedAt = now,
                    createdAt = old?.createdAt ?: now,
                    lastErrorCode = err?.code,
                    lastErrorMessage = err?.let { Sanitize.text(it.msg, Sanitize.MAX_MESSAGE) },
                )
            })

            // Transactions
            for (a in set.accounts) {
                val key = Ids.accountKey(a.connId, a.id)
                val ids = a.transactions.map { Ids.transactionId(key, it.id) }
                val existing = (ids.chunked(500).flatMap { db.transactions().getByIds(it) } +
                    db.transactions().pendingForAccount(key)).distinctBy { it.id }
                val result = merger.mergeAccount(
                    accountKey = key,
                    incoming = a.transactions,
                    existing = existing.map { it.toStored() },
                    window = windowFor(key),
                    syncTime = now,
                    allowPendingCleanup = plan.accountError(a.connId, a.id) == null,
                )
                val byId = existing.associateBy { it.id }
                if (result.deleteIds.isNotEmpty()) result.deleteIds.chunked(500).forEach { db.transactions().deleteByIds(it) }
                if (result.upserts.isNotEmpty()) {
                    db.transactions().upsert(result.upserts.map { st ->
                        st.toEntity(a.currency, fallbackCategory, byId[st.id], now)
                    })
                }
                newCount += result.newCount
                updatedCount += result.updatedCount
            }
        }

        val outcome = plan.outcome
        if (outcome != SyncOutcome.FAILED) {
            settings.setLastSuccessfulSync(now)
            settings.setPaymentRequired(false)
        }
        val msg = buildString {
            append("${set.accounts.size} accounts, $newCount new, $updatedCount updated")
            if (set.skippedRows > 0) append(", ${set.skippedRows} unreadable rows skipped")
            val general = plan.generalErrors.firstOrNull()
            if (general != null) append(". ").append(general.msg)
        }
        val result = when (outcome) {
            SyncOutcome.SUCCESS -> SyncResult.SUCCESS
            SyncOutcome.PARTIAL -> SyncResult.PARTIAL
            SyncOutcome.FAILED -> SyncResult.FAILED
        }
        log(now, trigger, result, newCount, updatedCount, plan.codes, msg, requests)
        return SyncRunResult.Done(result, newCount, updatedCount)
    }

    private suspend fun log(
        startedAt: Long, trigger: SyncTrigger, result: SyncResult, newCount: Int = 0, updatedCount: Int = 0,
        codes: String = "", message: String, requests: Int = 0,
    ) {
        db.syncLog().insert(
            SyncLogEntity(
                startedAt = startedAt, finishedAt = clock.instant().epochSecond, trigger = trigger, result = result,
                newCount = newCount, updatedCount = updatedCount, errorCodes = Sanitize.text(codes, 200),
                message = Sanitize.text(Redactor.redact(message), Sanitize.MAX_MESSAGE), requestCount = requests,
            ),
        )
        db.syncLog().trim(KEEP_LOG_ROWS)
    }

    companion object {
        const val KEEP_LOG_ROWS = 200
    }
}

internal fun TransactionEntity.toStored() = StoredTx(
    id = id, accountKey = accountId, remoteId = remoteId, amountMinor = amountMinor, description = description,
    merchantKey = merchantKey, postedAt = postedAt, transactedAt = transactedAt, pending = pending,
    effectiveAt = occurredAt, categoryId = categoryId, categorySource = categorySource, note = note,
    alertedAt = alertedAt, createdAt = importedAt, excludedFromBudget = excludedFromBudget,
)

internal fun StoredTx.toEntity(currency: String, fallbackCategory: Long, old: TransactionEntity?, now: Long) = TransactionEntity(
    id = id, accountId = accountKey, remoteId = remoteId, postedAt = postedAt, transactedAt = transactedAt,
    occurredAt = effectiveAt, amountMinor = amountMinor, currency = currency, description = description,
    merchantKey = merchantKey, pending = pending, categoryId = categoryId ?: fallbackCategory,
    categorySource = categorySource, note = note, excludedFromBudget = old?.excludedFromBudget ?: excludedFromBudget,
    recurringChargeId = old?.recurringChargeId, alertedAt = alertedAt, importedAt = createdAt, updatedAt = now,
)
