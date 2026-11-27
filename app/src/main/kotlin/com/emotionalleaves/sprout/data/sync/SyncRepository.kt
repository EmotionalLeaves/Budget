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
import com.emotionalleaves.sprout.domain.categorize.CategorySource
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

enum class SkipReason { NOT_CONNECTED, REVOKED, NEEDS_RECONNECT, DAILY_LIMIT, RATE_LIMITED }

/** A problem worth a "Sync problems" notification. [message] is sanitized plain text. */
data class SyncProblem(val key: String, val title: String, val message: String)

/** Receives what a sync found, for notifications. Must never throw into the sync. */
interface AlertSink {
    /** [newOutflowIds]: transactions newly inserted by a non-first sync, money out. */
    suspend fun afterSync(newOutflowIds: List<String>, problems: List<SyncProblem>)
}

/**
 * Runs one SimpleFIN sync: fetch, merge, store, log. Never on the main thread; one at a time.
 */
class SyncRepository(
    private val db: SproutDatabase,
    private val secureStore: SecureStore,
    private val settings: SettingsStore,
    private val client: SimpleFinClient,
    private val clock: Clock,
    private val alerts: AlertSink? = null,
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

    /**
     * Money that left one of your accounts and arrived in another (same amount, within a few days) is a
     * transfer on both sides. Looks back 120 days so a first sync catches its history too.
     */
    private suspend fun pairTransfersLocked(transfersId: Long, now: Long): Int {
        val since = now - 120L * 24 * 3600
        val rows = db.transactions().pairCandidates(since, transfersId).map {
            com.emotionalleaves.sprout.domain.transfer.TransferPairing.Row(it.id, it.accountId, it.amountMinor, it.at)
        }
        val pairs = com.emotionalleaves.sprout.domain.transfer.TransferPairing.pairs(rows)
        pairs.forEach { (a, b) ->
            db.transactions().setCategory(a, transfersId, CategorySource.PAIRED, now)
            db.transactions().setCategory(b, transfersId, CategorySource.PAIRED, now)
        }
        return pairs.size
    }

    /**
     * Hides bank accounts stored twice (a bank connected again gets new ids, so every transaction appeared twice),
     * keeping the copy that still syncs. Your categories and notes on the hidden copy carry over to matching
     * transactions in the kept one. Returns how many accounts were hidden. An account you un-hide stays visible.
     */
    suspend fun hideDuplicateAccounts(): Int = withContext(Dispatchers.IO) {
        switchStaleCopies()
        val s = settings.current()
        val accounts = db.accounts().getAll().filter { it.source == AccountSource.SIMPLEFIN && !it.hidden && it.id !in s.dedupedAccounts }
        if (accounts.size < 2) return@withContext 0
        val rows = db.transactions().sigRows()
        fun sig(r: com.emotionalleaves.sprout.data.db.SigRow) =
            com.emotionalleaves.sprout.domain.sync.DuplicateAccounts.signature(r.occurredAt, r.amountMinor, r.description)
        val byAccount = rows.groupBy { it.accountId }
        val pairs = com.emotionalleaves.sprout.domain.sync.DuplicateAccounts.find(
            accounts.map { com.emotionalleaves.sprout.domain.sync.DuplicateAccounts.Account(it.id, it.label, it.lastSyncedAt ?: 0, byAccount[it.id]?.size ?: 0) },
            byAccount.mapValues { (_, l) -> l.map(::sig).toSet() },
        )
        if (pairs.isEmpty()) return@withContext 0
        val now = clock.instant().epochSecond
        db.withTransaction {
            for ((dup, keep) in pairs) {
                val keepBySig = byAccount[keep].orEmpty().associateBy(::sig)
                for (r in byAccount[dup].orEmpty()) {
                    val k = keepBySig[sig(r)] ?: continue
                    if (r.categorySource == CategorySource.USER && k.categorySource != CategorySource.USER) {
                        db.transactions().setCategory(k.id, r.categoryId, CategorySource.USER, now)
                    }
                    if (!r.note.isNullOrBlank() && k.note.isNullOrBlank()) db.transactions().setNote(k.id, r.note, now)
                }
                db.accounts().setHidden(dup, true)
                db.accounts().setIncludeInTotals(dup, false)
            }
        }
        pairs.forEach { (dup, keep) -> settings.setDedupPair(dup, keep) }
        pairs.size
    }

    /** Two days without an update while the hidden copy keeps syncing = that login stopped; show the live copy. */
    private val STALE_COPY_SECONDS = 2L * 24 * 3600

    /**
     * Shared accounts seen through two logins (yours and a partner's): if the login whose copy Sprout kept stops
     * syncing (expired token, removed from Bridge), switch to the copy that's still updating, carrying your
     * categories and notes over, so the account never goes stale.
     */
    private suspend fun switchStaleCopies() {
        val s = settings.current()
        if (s.dedupPairs.isEmpty()) return
        val accounts = db.accounts().getAll().associateBy { it.id }
        val now = clock.instant().epochSecond
        for (pair in s.dedupPairs) {
            val (hiddenId, keptId) = pair.split('|').takeIf { it.size == 2 } ?: continue
            val hidden = accounts[hiddenId] ?: continue
            val kept = accounts[keptId] ?: continue
            if (!hidden.hidden || kept.hidden) continue // you changed these yourself: leave them
            val keptLast = kept.lastSyncedAt ?: 0
            val hiddenLast = hidden.lastSyncedAt ?: 0
            if (hiddenLast - keptLast < STALE_COPY_SECONDS) continue
            val rows = db.transactions().sigRowsFor(listOf(hiddenId, keptId))
            fun sig(r: com.emotionalleaves.sprout.data.db.SigRow) =
                com.emotionalleaves.sprout.domain.sync.DuplicateAccounts.signature(r.occurredAt, r.amountMinor, r.description)
            val target = rows.filter { it.accountId == hiddenId }.associateBy(::sig)
            db.withTransaction {
                for (r in rows.filter { it.accountId == keptId }) {
                    val t = target[sig(r)] ?: continue
                    if (r.categorySource == CategorySource.USER && t.categorySource != CategorySource.USER) {
                        db.transactions().setCategory(t.id, r.categoryId, CategorySource.USER, now)
                    }
                    if (!r.note.isNullOrBlank() && t.note.isNullOrBlank()) db.transactions().setNote(t.id, r.note, now)
                }
                db.accounts().setHidden(hiddenId, false)
                db.accounts().setIncludeInTotals(hiddenId, true)
                db.accounts().setHidden(keptId, true)
                db.accounts().setIncludeInTotals(keptId, false)
            }
            settings.setDedupPair(keptId, hiddenId)
        }
    }

    suspend fun sync(trigger: SyncTrigger): SyncRunResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            pendingOutflows.clear()
            pendingProblems.clear()
            val result = syncLocked(trigger)
            if (alerts != null && (pendingOutflows.isNotEmpty() || pendingProblems.isNotEmpty() || result is SyncRunResult.Done)) {
                runCatching { alerts.afterSync(pendingOutflows.toList(), pendingProblems.toList()) }
            }
            result
        }
    }

    // Collected during one locked sync run, handed to [alerts] afterwards.
    private val pendingOutflows = mutableListOf<String>()
    private val pendingProblems = mutableListOf<SyncProblem>()

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
        if ((s.rateLimitedUntil ?: 0L) > now) return SyncRunResult.Skipped(SkipReason.RATE_LIMITED)

        val used = db.syncLog().requestsSince(now - 86_400)
        val budget = if (trigger == SyncTrigger.FORCED) SyncPolicy.FORCED_REQUEST_BUDGET else SyncPolicy.DAILY_REQUEST_BUDGET
        if (used + 2 > budget) {
            log(now, trigger, SyncResult.FAILED, codes = "local.daily_limit",
                message = if (trigger == SyncTrigger.FORCED) "SimpleFIN's daily limit is nearly used up. Try again in a few hours (going over can turn your access off)."
                else "Paused to stay under SimpleFIN's daily limit. Sprout will sync again later.")
            return SyncRunResult.Skipped(SkipReason.DAILY_LIMIT)
        }

        settings.setLastSyncAttempt(now)
        val window = SyncPolicy.window(clock, s.lastSuccessfulSyncAt, s.firstSyncDays)
        val fetch = client.fetchAccounts(access, AccountsQuery(window.startDate, window.endDate, pending = true))

        return when (fetch) {
            is FetchResult.Success -> {
                val backfill = backfillNewAccounts(access, fetch.accountSet, s.lastSuccessfulSyncAt, s.firstSyncDays, used + fetch.requestCount)
                var merged = backfill?.let { mergeBackfill(fetch.accountSet, it.set) } ?: fetch.accountSet
                val windows = backfill?.let { b -> b.set.accounts.associate { Ids.accountKey(it.connId, it.id) to b.window } }.orEmpty()
                val soFar = used + fetch.requestCount + (backfill?.requests ?: 0)
                val history = fetchHistory(access, window, s, soFar, trigger)
                history.sets.forEach { merged = mergeBackfill(merged, it) }
                val result = store(merged, { key -> windows[key] ?: window }, now, trigger,
                    fetch.requestCount + (backfill?.requests ?: 0) + history.requests, windows.keys, historyBefore = window.startDate)
                if (result is SyncRunResult.Done && result.result != SyncResult.FAILED && history.oldest != null) {
                    settings.setHistoryProgress(history.oldest, history.done)
                }
                runCatching { hideDuplicateAccounts() }
                result
            }
            is FetchResult.AuthFailed -> {
                pendingProblems += SyncProblem("revoked", "SimpleFIN access turned off",
                    "Paste a new setup token in Sprout to reconnect. Your data is kept.")
                db.connections().setAllStatus(ConnectionStatus.REVOKED, "http.403",
                    "Your SimpleFIN access was turned off. Paste a new setup token to reconnect.", now)
                settings.setAccessRevoked(true)
                log(now, trigger, SyncResult.FAILED, codes = "http.403", requests = fetch.requestCount,
                    message = "SimpleFIN access was turned off (403).")
                SyncRunResult.Skipped(SkipReason.REVOKED)
            }
            is FetchResult.PaymentRequired -> {
                if (!s.paymentRequired) {
                    pendingProblems += SyncProblem("payment", "SimpleFIN Bridge needs attention",
                        "Your SimpleFIN Bridge subscription needs attention.")
                }
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

    private class History(val sets: List<AccountSet>, val oldest: Long?, val done: Boolean, val requests: Int)

    /**
     * SimpleFIN gives at most 90 days per request, so older history comes in 90-day steps, oldest last, until
     * the "History" setting is reached or the bank has nothing older (two empty steps). A few steps per sync,
     * within a request budget that always leaves room for regular syncs; the rest continue next time.
     */
    private suspend fun fetchHistory(access: AccessUrl, main: SyncWindow, s: com.emotionalleaves.sprout.data.settings.Settings, usedRequests: Int, trigger: SyncTrigger): History {
        if (s.historyDone || s.firstSyncDays <= SyncPolicy.MAX_FIRST_SYNC_DAYS) return History(emptyList(), null, s.historyDone, 0)
        var oldest = s.historyOldest
            ?: if (s.lastSuccessfulSyncAt == null) main.startDate else (db.transactions().oldestSynced() ?: main.startDate)
        val budget = if (trigger == SyncTrigger.FORCED) SyncPolicy.FORCED_REQUEST_BUDGET else SyncPolicy.HISTORY_REQUEST_BUDGET
        val sets = ArrayList<AccountSet>()
        var requests = 0
        var empties = 0
        var done = false
        repeat(SyncPolicy.MAX_HISTORY_CHUNKS_PER_SYNC) {
            if (done || usedRequests + requests + 1 > budget) return@repeat
            val chunk = SyncPolicy.nextHistoryChunk(clock, oldest, s.firstSyncDays)
            if (chunk == null) { done = true; return@repeat }
            val r = client.fetchAccounts(access, AccountsQuery(chunk.startDate, chunk.endDate, pending = false))
            requests += when (r) {
                is FetchResult.Success -> r.requestCount
                is FetchResult.AuthFailed -> r.requestCount
                is FetchResult.PaymentRequired -> r.requestCount
                is FetchResult.ServerBusy -> r.requestCount
                is FetchResult.HttpError -> r.requestCount
                is FetchResult.NetworkError -> r.requestCount
                is FetchResult.ParseError -> r.requestCount
            }
            if (r !is FetchResult.Success) return History(sets, oldest, false, requests)
            sets += r.accountSet
            oldest = chunk.startDate
            empties = if (r.accountSet.accounts.all { it.transactions.isEmpty() }) empties + 1 else 0
            if (empties >= SyncPolicy.EMPTY_CHUNKS_TO_STOP) done = true
        }
        if (SyncPolicy.nextHistoryChunk(clock, oldest, s.firstSyncDays) == null) done = true
        return History(sets, oldest, done, requests)
    }

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

    private suspend fun store(
        set: AccountSet, windowFor: (String) -> SyncWindow, now: Long, trigger: SyncTrigger, requests: Int,
        backfilled: Set<String> = emptySet(),
        /** Rows older than this are history (older steps): no review inbox, no unusual-charge alerts. */
        historyBefore: Long? = null,
    ): SyncRunResult {
        val plan = SyncErrors.plan(set)
        var newCount = 0
        var updatedCount = 0

        db.withTransaction {
            val categories = db.categories().getAll()
            val idByKey = categories.mapNotNull { c -> c.key?.let { it to c.id } }.toMap()
            val rules = db.merchantRules().getAll().map { it.toUserRule() }
            val tipIds = setOfNotNull(idByKey[DefaultCategories.EATING_OUT])
            val learned = com.emotionalleaves.sprout.domain.categorize.LearnedCategorizer.train(
                db.transactions().learningExamples().map {
                    com.emotionalleaves.sprout.domain.categorize.LearnedCategorizer.Example(it.description, it.merchantKey, it.categoryId)
                },
            )
            val merger = SyncMerger(CategoryResolver(rules, idByKey, learned), tipIds, settings.current().usePurchaseDate)
            val fallbackCategory = idByKey[DefaultCategories.UNCATEGORIZED]
                ?: categories.firstOrNull { it.kind == CategoryKind.EXPENSE }?.id
                ?: error("No categories")

            // Connections
            val oldConnections = db.connections().getAll().associateBy { it.connId }
            plan.connectionErrors.forEach { (id, e) ->
                if (oldConnections[id]?.status != ConnectionStatus.NEEDS_ATTENTION) {
                    val name = set.connections.firstOrNull { it.connId == id }?.name ?: oldConnections[id]?.name ?: "A bank"
                    pendingProblems += SyncProblem("conn:$id", "$name needs attention",
                        Sanitize.text(e.msg.ifBlank { SyncErrors.explain(e.code) }, Sanitize.MAX_MESSAGE))
                }
            }
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
                    // History from a first sync or backfill doesn't go to the review inbox; only new activity does.
                    val historical = existingAccounts[key]?.firstSyncCompleted != true || key in backfilled
                    db.transactions().upsert(result.upserts.map { st ->
                        val e = st.toEntity(a.currency, fallbackCategory, byId[st.id], now)
                        val old = historyBefore != null && !st.pending && st.effectiveAt < historyBefore
                        if ((historical || old) && e.reviewedAt == null) e.copy(reviewedAt = now) else e
                    })
                }
                newCount += result.newCount
                updatedCount += result.updatedCount
                // Unusual-charge alerts only for accounts past their first (backfill) sync.
                if (existingAccounts[key]?.firstSyncCompleted == true && key !in backfilled) {
                    val byUpsert = result.upserts.associateBy { it.id }
                    pendingOutflows += result.newIds.filter { id ->
                        val u = byUpsert[id] ?: return@filter false
                        u.amountMinor < 0L && (historyBefore == null || u.pending || u.effectiveAt >= historyBefore)
                    }
                }
            }
            idByKey[DefaultCategories.TRANSFERS]?.let { pairTransfersLocked(it, now) }
        }

        if (SyncErrors.isRateLimitWarning(set.errors)) {
            // The Bridge disables tokens that keep going past its warning. Pause all syncing for a while.
            settings.setRateLimitedUntil(now + SyncErrors.RATE_LIMIT_BACKOFF_SECONDS)
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
    reviewedAt = reviewedAt,
)

internal fun StoredTx.toEntity(currency: String, fallbackCategory: Long, old: TransactionEntity?, now: Long) = TransactionEntity(
    id = id, accountId = accountKey, remoteId = remoteId, postedAt = postedAt, transactedAt = transactedAt,
    occurredAt = effectiveAt, amountMinor = amountMinor, currency = currency, description = description,
    merchantKey = merchantKey, pending = pending, categoryId = categoryId ?: fallbackCategory,
    categorySource = categorySource, note = note, excludedFromBudget = old?.excludedFromBudget ?: excludedFromBudget,
    recurringChargeId = old?.recurringChargeId, alertedAt = alertedAt, importedAt = createdAt, updatedAt = now,
    reviewedAt = old?.reviewedAt ?: reviewedAt,
)
