package com.emotionalleaves.sprout.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.RuleType
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Upsert suspend fun upsert(items: List<ConnectionEntity>)
    @Query("SELECT * FROM connections ORDER BY name") fun observeAll(): Flow<List<ConnectionEntity>>
    @Query("SELECT * FROM connections") suspend fun getAll(): List<ConnectionEntity>
    @Query("DELETE FROM connections") suspend fun deleteAll()
    @Query("UPDATE connections SET status = :status, lastErrorCode = :code, lastErrorMessage = :message, updatedAt = :now")
    suspend fun setAllStatus(status: ConnectionStatus, code: String?, message: String?, now: Long)
}

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(items: List<AccountEntity>)
    @Query("SELECT * FROM accounts WHERE hidden = 0 ORDER BY type, name") fun observeVisible(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts ORDER BY type, name") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts") suspend fun getAll(): List<AccountEntity>
    @Query("SELECT * FROM accounts WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<AccountEntity>
    @Query("UPDATE accounts SET includeInTotals = :include WHERE id = :id") suspend fun setIncludeInTotals(id: String, include: Boolean)
    @Query("UPDATE accounts SET hidden = :hidden WHERE id = :id") suspend fun setHidden(id: String, hidden: Boolean)
    @Query("SELECT * FROM accounts WHERE source = 'MANUAL' ORDER BY name") fun observeManual(): Flow<List<AccountEntity>>
    @Query("UPDATE accounts SET displayName = :name WHERE id = :id") suspend fun rename(id: String, name: String?)
    @Query("UPDATE accounts SET type = :type WHERE id = :id") suspend fun setType(id: String, type: com.emotionalleaves.sprout.domain.accounts.AccountType)
    /** Disconnect + delete: removes bank accounts (their transactions cascade) and connections. Manual accounts stay. */
    @Query("DELETE FROM accounts WHERE source = 'SIMPLEFIN'") suspend fun deleteSimpleFinAccounts()
    @Query("UPDATE accounts SET balanceMinor = (SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :id), balanceDate = :now, lastSyncedAt = :now WHERE id = :id")
    suspend fun recomputeManualBalance(id: String, now: Long)
}

/** A transaction joined with what the list needs to show. */
data class TransactionRow(
    val id: String,
    val accountId: String,
    val description: String,
    val merchantKey: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val pending: Boolean,
    val note: String?,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: Int,
    val categorySource: CategorySource,
    val accountName: String,
)

/** Everything the detail sheet shows. */
data class TransactionDetail(
    val id: String,
    val accountId: String,
    val description: String,
    val merchantKey: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val postedAt: Long,
    val transactedAt: Long?,
    val pending: Boolean,
    val note: String?,
    val excludedFromBudget: Boolean,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: Int,
    val categorySource: CategorySource,
    val accountName: String,
    val recurringChargeId: Long? = null,
)

/** The few columns needed to re-run category rules. */
data class RuleInput(
    val id: String,
    val description: String,
    val merchantKey: String,
    val amountMinor: Long,
    val categoryId: Long,
    val categorySource: CategorySource,
)

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(items: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getById(id: String): TransactionEntity?

    @Query(
        """
        SELECT t.id, t.accountId, t.description, t.merchantKey, t.amountMinor, t.occurredAt, t.postedAt, t.transactedAt,
               t.pending, t.note, t.excludedFromBudget, t.categoryId, c.name AS categoryName, c.colorArgb AS categoryColor,
               t.categorySource, COALESCE(a.displayName, a.name) AS accountName, t.recurringChargeId
        FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
        WHERE t.id = :id
        """,
    )
    fun observeDetail(id: String): Flow<TransactionDetail?>

    @Query("UPDATE transactions SET categoryId = :categoryId, categorySource = :source, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: Long, source: CategorySource, now: Long)

    /** Re-dates posted transactions after the "purchase date" option changes. Pending rows keep their date. */
    @Query(
        """
        UPDATE transactions SET occurredAt = CASE WHEN :purchase = 1 AND transactedAt IS NOT NULL THEN transactedAt ELSE postedAt END
        WHERE pending = 0 AND postedAt > 0 AND id NOT LIKE '%:csv:%'
        """,
    )
    suspend fun redatePosted(purchase: Boolean): Int

    @Query("UPDATE transactions SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun setNote(id: String, note: String?, now: Long)

    @Query("UPDATE transactions SET excludedFromBudget = :excluded, updatedAt = :now WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean, now: Long)

    /** Applies a learned merchant rule to every transaction from that merchant the user hasn't set by hand. */
    @Query(
        """
        UPDATE transactions SET categoryId = :categoryId, categorySource = 'MERCHANT_RULE', updatedAt = :now
        WHERE merchantKey = :merchantKey AND categorySource != 'USER'
        """,
    )
    suspend fun applyMerchantRule(merchantKey: String, categoryId: Long, now: Long): Int

    @Query("SELECT id, description, merchantKey, amountMinor, categoryId, categorySource FROM transactions WHERE merchantKey = :merchantKey")
    suspend fun ruleInputsForMerchant(merchantKey: String): List<RuleInput>

    @Query("SELECT id, description, merchantKey, amountMinor, categoryId, categorySource FROM transactions WHERE categorySource NOT IN ('USER', 'IMPORT')")
    suspend fun ruleInputs(): List<RuleInput>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId = :uncategorizedId AND a.hidden = 0
        """,
    )
    fun observeCountInCategory(uncategorizedId: Long): Flow<Int>

    @Query("DELETE FROM transactions WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM transactions WHERE id IN (:ids)") suspend fun getByIds(ids: List<String>): List<TransactionEntity>
    @Query("SELECT id FROM transactions WHERE id IN (:ids)") suspend fun existingIds(ids: List<String>): List<String>

    @Query(
        """
        SELECT t.occurredAt AS occurredAt, t.description AS description, t.amountMinor AS amountMinor, c.name AS categoryName,
               COALESCE(a.displayName, a.name) AS accountName, t.pending AS pending, t.note AS note, t.merchantKey AS merchantKey
        FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
        WHERE t.occurredAt >= :start AND t.occurredAt < :end
        ORDER BY t.occurredAt, t.id
        """,
    )
    suspend fun exportRows(start: Long, end: Long): List<ExportRow>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND pending = 1")
    suspend fun pendingForAccount(accountId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions") fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT t.id, t.accountId, t.description, t.merchantKey, t.amountMinor, t.occurredAt, t.pending, t.note,
               t.categoryId, c.name AS categoryName, c.colorArgb AS categoryColor, t.categorySource,
               COALESCE(a.displayName, a.name) AS accountName
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        JOIN accounts a ON a.id = t.accountId
        WHERE a.hidden = 0
          AND (:start IS NULL OR (t.occurredAt >= :start AND t.occurredAt < :end))
          AND (:query = '' OR t.description LIKE '%' || :query || '%' OR t.merchantKey LIKE '%' || :query || '%'
               OR t.note LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR t.categoryId = :categoryId
               OR EXISTS (SELECT 1 FROM transaction_splits s WHERE s.transactionId = t.id AND s.categoryId = :categoryId))
        ORDER BY t.occurredAt DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRows(query: String, limit: Int, categoryId: Long? = null, start: Long? = null, end: Long? = null): Flow<List<TransactionRow>>

    /** The full list, paged so it stays smooth with tens of thousands of rows. */
    @Query(
        """
        SELECT t.id, t.accountId, t.description, t.merchantKey, t.amountMinor, t.occurredAt, t.pending, t.note,
               t.categoryId, c.name AS categoryName, c.colorArgb AS categoryColor, t.categorySource,
               COALESCE(a.displayName, a.name) AS accountName
        FROM transactions t
        JOIN categories c ON c.id = t.categoryId
        JOIN accounts a ON a.id = t.accountId
        WHERE a.hidden = 0
          AND (:query = '' OR t.description LIKE '%' || :query || '%' OR t.merchantKey LIKE '%' || :query || '%'
               OR t.note LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR t.categoryId = :categoryId
               OR EXISTS (SELECT 1 FROM transaction_splits s WHERE s.transactionId = t.id AND s.categoryId = :categoryId))
          AND (:pendingOnly = 0 OR t.pending = 1)
          AND (:accountId IS NULL OR t.accountId = :accountId)
        ORDER BY t.occurredAt DESC, t.id DESC
        """,
    )
    fun pagingRows(query: String, categoryId: Long?, pendingOnly: Boolean, accountId: String? = null): PagingSource<Int, TransactionRow>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(items: List<CategoryEntity>): List<Long>
    @Insert suspend fun insert(item: CategoryEntity): Long
    @Update suspend fun update(item: CategoryEntity)
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun getById(id: Long): CategoryEntity?
    @Query("SELECT * FROM categories WHERE `key` = :key") suspend fun getByKey(key: String): CategoryEntity?
    @Query("SELECT * FROM categories ORDER BY archived, sortOrder, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories") suspend fun maxSortOrder(): Int
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") suspend fun getAll(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder, name") fun observeActive(): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
}

data class RuleRow(
    val id: Long,
    val matchType: RuleType,
    val pattern: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: Int,
    val origin: RuleOrigin,
)

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC") suspend fun getAll(): List<MerchantRuleEntity>
    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC") fun observeAll(): Flow<List<MerchantRuleEntity>>
    @Query("SELECT * FROM merchant_rules WHERE matchType = :type AND pattern = :pattern")
    suspend fun find(type: RuleType, pattern: String): MerchantRuleEntity?
    @Query("SELECT * FROM merchant_rules WHERE id = :id") suspend fun getById(id: Long): MerchantRuleEntity?
    @Insert suspend fun insert(rule: MerchantRuleEntity): Long
    @Update suspend fun update(rule: MerchantRuleEntity)
    @Query("DELETE FROM merchant_rules WHERE id = :id") suspend fun delete(id: Long)
    @Query(
        """
        SELECT r.id, r.matchType, r.pattern, r.categoryId, c.name AS categoryName, c.colorArgb AS categoryColor, r.origin
        FROM merchant_rules r JOIN categories c ON c.id = r.categoryId
        ORDER BY r.matchType, r.pattern
        """,
    )
    fun observeRows(): Flow<List<RuleRow>>
}

@Dao
interface SyncLogDao {
    @Insert suspend fun insert(item: SyncLogEntity): Long
    @Query("SELECT * FROM sync_log ORDER BY startedAt DESC, id DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<SyncLogEntity>>
    @Query("SELECT COALESCE(SUM(requestCount), 0) FROM sync_log WHERE startedAt >= :since") suspend fun requestsSince(since: Long): Int
    /** Keeps the newest [keep] rows. */
    @Query("DELETE FROM sync_log WHERE id NOT IN (SELECT id FROM sync_log ORDER BY startedAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

data class CategorySum(val categoryId: Long, val total: Long)
data class MerchantSum(val merchantKey: String, val total: Long, val count: Int)
data class SpendRow(val categoryId: Long, val amountMinor: Long, val occurredAt: Long)

/**
 * Budgets and the spending they're compared with. Spending queries skip hidden accounts and transactions the
 * user excluded from budgets.
 */
@Dao
interface BudgetDao {
    @Query("SELECT * FROM category_budgets") fun observeDefaults(): Flow<List<CategoryBudgetEntity>>
    @Query("SELECT * FROM budget_overrides WHERE yearMonth = :yearMonth") fun observeOverrides(yearMonth: String): Flow<List<BudgetOverrideEntity>>
    @Query("SELECT * FROM budget_overrides ORDER BY yearMonth, categoryId") suspend fun allOverrides(): List<BudgetOverrideEntity>
    @Query("SELECT * FROM budget_overrides WHERE yearMonth < :beforeMonth") fun observeOverridesBefore(beforeMonth: String): Flow<List<BudgetOverrideEntity>>
    @Query("SELECT * FROM budget_rollovers") fun observeRollovers(): Flow<List<BudgetRolloverEntity>>
    @Upsert suspend fun upsertRollover(item: BudgetRolloverEntity)
    @Query("DELETE FROM budget_rollovers WHERE categoryId = :categoryId") suspend fun deleteRollover(categoryId: Long)
    @Upsert suspend fun upsertDefault(item: CategoryBudgetEntity)
    @Upsert suspend fun upsertDefaults(items: List<CategoryBudgetEntity>)
    @Query("DELETE FROM category_budgets WHERE categoryId = :categoryId") suspend fun deleteDefault(categoryId: Long)
    @Upsert suspend fun upsertOverride(item: BudgetOverrideEntity)
    @Query("DELETE FROM budget_overrides WHERE categoryId = :categoryId AND yearMonth = :yearMonth")
    suspend fun deleteOverride(categoryId: Long, yearMonth: String)

    @Query(
        """
        SELECT categoryId, SUM(amt) AS total FROM (
            SELECT t.categoryId AS categoryId,
                   t.amountMinor - COALESCE((SELECT SUM(s.amountMinor) FROM transaction_splits s WHERE s.transactionId = t.id), 0) AS amt
            FROM transactions t JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :start AND t.occurredAt < :end AND t.excludedFromBudget = 0 AND a.hidden = 0
            UNION ALL
            SELECT s.categoryId AS categoryId, s.amountMinor AS amt
            FROM transaction_splits s JOIN transactions t ON t.id = s.transactionId JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :start AND t.occurredAt < :end AND t.excludedFromBudget = 0 AND a.hidden = 0
        )
        GROUP BY categoryId
        """,
    )
    fun observeSums(start: Long, end: Long): Flow<List<CategorySum>>

    @Query(
        """
        SELECT categoryId, amountMinor, occurredAt FROM (
            SELECT t.categoryId AS categoryId, t.occurredAt AS occurredAt,
                   t.amountMinor - COALESCE((SELECT SUM(s.amountMinor) FROM transaction_splits s WHERE s.transactionId = t.id), 0) AS amountMinor
            FROM transactions t JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :start AND t.occurredAt < :end AND t.excludedFromBudget = 0 AND a.hidden = 0
            UNION ALL
            SELECT s.categoryId AS categoryId, t.occurredAt AS occurredAt, s.amountMinor AS amountMinor
            FROM transaction_splits s JOIN transactions t ON t.id = s.transactionId JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :start AND t.occurredAt < :end AND t.excludedFromBudget = 0 AND a.hidden = 0
        )
        WHERE :categoryId IS NULL OR categoryId = :categoryId
        """,
    )
    suspend fun spendRows(start: Long, end: Long, categoryId: Long? = null): List<SpendRow>

    @Query(
        """
        SELECT merchantKey, SUM(amt) AS total, COUNT(*) AS count FROM (
            SELECT t.merchantKey AS merchantKey,
                   t.amountMinor - COALESCE((SELECT SUM(s.amountMinor) FROM transaction_splits s WHERE s.transactionId = t.id), 0) AS amt
            FROM transactions t JOIN accounts a ON a.id = t.accountId
            WHERE t.categoryId = :categoryId AND t.occurredAt >= :start AND t.occurredAt < :end
              AND t.excludedFromBudget = 0 AND a.hidden = 0
            UNION ALL
            SELECT t.merchantKey AS merchantKey, s.amountMinor AS amt
            FROM transaction_splits s JOIN transactions t ON t.id = s.transactionId JOIN accounts a ON a.id = t.accountId
            WHERE s.categoryId = :categoryId AND t.occurredAt >= :start AND t.occurredAt < :end
              AND t.excludedFromBudget = 0 AND a.hidden = 0
        )
        GROUP BY merchantKey ORDER BY total ASC
        """,
    )
    fun observeMerchantSums(categoryId: Long, start: Long, end: Long): Flow<List<MerchantSum>>

    @Query("SELECT MIN(occurredAt) FROM transactions") suspend fun firstOccurredAt(): Long?
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM budget_alerts WHERE yearMonth = :yearMonth") suspend fun firedIn(yearMonth: String): List<BudgetAlertEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun record(items: List<BudgetAlertEntity>)

    /** The merchant's earlier charges, newest first, for "unusual for this merchant". */
    @Query(
        """
        SELECT amountMinor FROM transactions
        WHERE merchantKey = :merchantKey AND id != :excludeId AND occurredAt <= :before
        ORDER BY occurredAt DESC LIMIT :limit
        """,
    )
    suspend fun priorAmounts(merchantKey: String, excludeId: String, before: Long, limit: Int = 50): List<Long>

    @Query("UPDATE transactions SET alertedAt = :at WHERE id = :id") suspend fun markAlerted(id: String, at: Long)
}

data class ChargeRow(val merchantKey: String, val amountMinor: Long, val occurredAt: Long, val categoryId: Long, val accountId: String)

data class RecurringRow(
    val id: Long,
    val merchantKey: String,
    val displayName: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: Int,
    val accountId: String?,
    val cadence: com.emotionalleaves.sprout.domain.recurring.Cadence,
    val typicalAmountMinor: Long,
    val lastChargeAt: Long,
    val nextDueAt: Long,
    val status: RecurringStatus,
    val userEdited: Boolean,
    val confidence: Float,
    val occurrences: Int,
)

@Dao
interface RecurringDao {
    /** Posted outflows in EXPENSE categories (no transfers or income), for detection. */
    @Query(
        """
        SELECT t.merchantKey AS merchantKey, t.amountMinor AS amountMinor, t.occurredAt AS occurredAt,
               t.categoryId AS categoryId, t.accountId AS accountId
        FROM transactions t JOIN categories c ON c.id = t.categoryId
        WHERE t.amountMinor < 0 AND t.pending = 0 AND t.occurredAt >= :from AND c.kind = 'EXPENSE'
        """,
    )
    suspend fun outflowsSince(from: Long): List<ChargeRow>

    /** Posted inflows in INCOME categories, for paycheck detection. */
    @Query(
        """
        SELECT t.merchantKey AS merchantKey, t.amountMinor AS amountMinor, t.occurredAt AS occurredAt,
               t.categoryId AS categoryId, t.accountId AS accountId
        FROM transactions t JOIN categories c ON c.id = t.categoryId
        WHERE t.amountMinor > 0 AND t.pending = 0 AND t.occurredAt >= :from AND c.kind = 'INCOME'
        """,
    )
    suspend fun inflowsSince(from: Long): List<ChargeRow>

    /** Everyday spending (EXPENSE, not part of a bill, not excluded, visible accounts) since [from], positive cents. */
    @Query(
        """
        SELECT COALESCE(-SUM(amt), 0) FROM (
            SELECT t.amountMinor - COALESCE((SELECT SUM(s.amountMinor) FROM transaction_splits s WHERE s.transactionId = t.id), 0) AS amt
            FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :from AND t.occurredAt < :to AND c.kind = 'EXPENSE' AND t.recurringChargeId IS NULL
              AND t.excludedFromBudget = 0 AND a.hidden = 0
            UNION ALL
            SELECT s.amountMinor AS amt
            FROM transaction_splits s JOIN transactions t ON t.id = s.transactionId
            JOIN categories c ON c.id = s.categoryId JOIN accounts a ON a.id = t.accountId
            WHERE t.occurredAt >= :from AND t.occurredAt < :to AND c.kind = 'EXPENSE' AND t.recurringChargeId IS NULL
              AND t.excludedFromBudget = 0 AND a.hidden = 0
        )
        """,
    )
    suspend fun everydaySpending(from: Long, to: Long): Long

    @Query("SELECT * FROM recurring_charges") suspend fun getAll(): List<RecurringChargeEntity>
    @Query("SELECT * FROM recurring_charges WHERE id = :id") suspend fun getById(id: Long): RecurringChargeEntity?
    @Insert suspend fun insert(item: RecurringChargeEntity): Long
    @Update suspend fun update(item: RecurringChargeEntity)
    @Query("DELETE FROM recurring_charges WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)

    @Query(
        """
        SELECT r.id, r.merchantKey, r.displayName, r.categoryId, c.name AS categoryName, c.colorArgb AS categoryColor,
               r.accountId, r.cadence, r.typicalAmountMinor, r.lastChargeAt, r.nextDueAt, r.status, r.userEdited,
               r.confidence, r.occurrences
        FROM recurring_charges r JOIN categories c ON c.id = r.categoryId
        WHERE r.status != 'DISMISSED'
        ORDER BY r.nextDueAt
        """,
    )
    fun observeActive(): Flow<List<RecurringRow>>

    @Query("UPDATE transactions SET recurringChargeId = :id WHERE merchantKey = :merchantKey AND amountMinor < 0")
    suspend fun linkTransactions(id: Long, merchantKey: String)

    @Query("UPDATE transactions SET recurringChargeId = NULL WHERE recurringChargeId IN (:ids)")
    suspend fun unlink(ids: List<Long>)

    /** Whether a charge from this merchant posted in [start, end) — "Paid ✓". */
    @Query("SELECT COUNT(*) FROM transactions WHERE merchantKey = :merchantKey AND amountMinor < 0 AND occurredAt >= :start AND occurredAt < :end")
    suspend fun chargesBetween(merchantKey: String, start: Long, end: Long): Int
}

data class ExportRow(
    val occurredAt: Long,
    val description: String,
    val amountMinor: Long,
    val categoryName: String,
    val accountName: String,
    val pending: Boolean,
    val note: String?,
    val merchantKey: String,
)

/** Full-table reads and writes for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM category_budgets") suspend fun categoryBudgets(): List<CategoryBudgetEntity>
    @Query("SELECT * FROM budget_overrides") suspend fun budgetOverrides(): List<BudgetOverrideEntity>
    @Query("SELECT * FROM budget_alerts") suspend fun budgetAlerts(): List<BudgetAlertEntity>
    @Query("SELECT * FROM budget_rollovers") suspend fun budgetRollovers(): List<BudgetRolloverEntity>
    @Query("SELECT * FROM transaction_splits") suspend fun transactionSplits(): List<TransactionSplitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTransactionSplits(items: List<TransactionSplitEntity>)
    @Query("DELETE FROM transaction_splits") suspend fun wipeTransactionSplits()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgetRollovers(items: List<BudgetRolloverEntity>)
    @Query("DELETE FROM budget_rollovers") suspend fun wipeBudgetRollovers()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putConnections(items: List<ConnectionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(items: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTransactions(items: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategoryBudgets(items: List<CategoryBudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgetOverrides(items: List<BudgetOverrideEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgetAlerts(items: List<BudgetAlertEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMerchantRules(items: List<MerchantRuleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(items: List<RecurringChargeEntity>)

    // Children first, so foreign keys never block the wipe.
    @Query("DELETE FROM transactions") suspend fun wipeTransactions()
    @Query("DELETE FROM budget_alerts") suspend fun wipeBudgetAlerts()
    @Query("DELETE FROM budget_overrides") suspend fun wipeBudgetOverrides()
    @Query("DELETE FROM category_budgets") suspend fun wipeCategoryBudgets()
    @Query("DELETE FROM merchant_rules") suspend fun wipeMerchantRules()
    @Query("DELETE FROM recurring_charges") suspend fun wipeRecurring()
    @Query("DELETE FROM accounts") suspend fun wipeAccounts()
    @Query("DELETE FROM connections") suspend fun wipeConnections()
    @Query("DELETE FROM categories") suspend fun wipeCategories()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM savings_goals WHERE archived = 0 ORDER BY createdAt") fun observeActive(): Flow<List<SavingsGoalEntity>>
    @Query("SELECT * FROM savings_goals") suspend fun getAll(): List<SavingsGoalEntity>
    @Insert suspend fun insert(goal: SavingsGoalEntity): Long
    @Update suspend fun update(goal: SavingsGoalEntity)
    @Query("DELETE FROM savings_goals WHERE id = :id") suspend fun delete(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAll(items: List<SavingsGoalEntity>)
    @Query("DELETE FROM savings_goals") suspend fun wipe()
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId ORDER BY id")
    fun observeFor(transactionId: String): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits WHERE transactionId = :transactionId ORDER BY id")
    suspend fun getFor(transactionId: String): List<TransactionSplitEntity>

    @Query("DELETE FROM transaction_splits WHERE transactionId = :transactionId") suspend fun deleteFor(transactionId: String)
    @Insert suspend fun insert(items: List<TransactionSplitEntity>)

    /** Replaces a transaction's parts in one step. */
    @Transaction
    suspend fun replace(transactionId: String, items: List<TransactionSplitEntity>) {
        deleteFor(transactionId)
        if (items.isNotEmpty()) insert(items)
    }
}
