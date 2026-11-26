package com.emotionalleaves.sprout.data.csv

import com.emotionalleaves.sprout.data.csv.CsvCategories
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.categorize.UserRule
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

data class ImportSummary(val imported: Int, val duplicates: Int, val failed: List<CsvRowError>)

/**
 * Stores parsed CSV rows. Deterministic ids make re-imports and overlapping files safe. Imported rows are
 * categorized like synced ones and feed recurring detection and budget alerts, but never unusual-charge alerts.
 */
class ImportRepository(
    private val db: SproutDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Runs after an import that added rows: recurring detection, then budget alerts. */
    private val afterImport: suspend () -> Unit = {},
) {
    suspend fun createManualAccount(name: String, type: AccountType): String = withContext(Dispatchers.IO) {
        val now = clock.instant().epochSecond
        val id = "manual:" + UUID.randomUUID()
        db.accounts().upsert(listOf(AccountEntity(
            id = id, source = AccountSource.MANUAL, remoteId = null, connId = null,
            name = Sanitize.text(name, 60).ifBlank { "Imported account" }, displayName = null, type = type, currency = "USD",
            balanceMinor = 0, availableBalanceMinor = null, balanceDate = now, includeInTotals = true, hidden = false,
            firstSyncCompleted = true, lastSyncedAt = now, createdAt = now,
        )))
        id
    }

    suspend fun import(accountId: String, result: CsvParseResult): ImportSummary = withContext(Dispatchers.IO) {
        val now = clock.instant().epochSecond
        val z = zone()
        val ids = CsvImport.ids(accountId, result.rows)
        val summary = db.withTransaction {
            val existing = ids.chunked(500).flatMap { db.transactions().existingIds(it) }.toSet()
            val categories = db.categories().getAll()
            val idByKey = categories.mapNotNull { c -> c.key?.let { it to c.id } }.toMap()
            val learned = com.emotionalleaves.sprout.domain.categorize.LearnedCategorizer.train(
                db.transactions().learningExamples().map {
                    com.emotionalleaves.sprout.domain.categorize.LearnedCategorizer.Example(it.description, it.merchantKey, it.categoryId)
                },
            )
            val resolver = CategoryResolver(db.merchantRules().getAll().map { it.toUserRule() }, idByKey, learned)
            val fallback = idByKey[DefaultCategories.UNCATEGORIZED] ?: categories.first { it.kind == CategoryKind.EXPENSE }.id
            val byName = categories.filter { !it.archived }.associate { it.name to it.id }
            val fresh = result.rows.zip(ids).filter { (_, id) -> id !in existing }.distinctBy { it.second }.map { (row, id) ->
                val at = row.date.atTime(12, 0).atZone(z).toEpochSecond()
                val key = MerchantNormalizer.normalize(row.description)
                val resolved = resolver.resolve(row.description, key, row.amountMinor)
                // Your own merchant rules win; then the bank's category column; then built-in rules.
                val bank = if (resolved.source == CategorySource.MERCHANT_RULE) null else CsvCategories.match(row.category, byName, idByKey)
                val r = if (bank != null) com.emotionalleaves.sprout.domain.categorize.Resolution(bank, CategorySource.IMPORT) else resolved
                TransactionEntity(
                    id = id, accountId = accountId, remoteId = id.substringAfterLast(':'), postedAt = at, transactedAt = null,
                    occurredAt = at, amountMinor = row.amountMinor, currency = "USD", description = row.description,
                    merchantKey = key, pending = false, categoryId = r.categoryId ?: fallback, categorySource = r.source,
                    note = null, excludedFromBudget = false, recurringChargeId = null,
                    // Imports never trigger unusual-charge alerts: mark them as already handled.
                    alertedAt = now, importedAt = now, updatedAt = now, reviewedAt = now,
                )
            }
            fresh.chunked(500).forEach { db.transactions().upsert(it) }
            db.accounts().recomputeManualBalance(accountId, now)
            ImportSummary(fresh.size, result.rows.size - fresh.size, result.errors)
        }
        if (summary.imported > 0) runCatching { afterImport() }
        summary
    }
}
