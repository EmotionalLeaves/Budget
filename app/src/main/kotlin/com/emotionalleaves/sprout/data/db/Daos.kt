package com.emotionalleaves.sprout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
               t.categorySource, COALESCE(a.displayName, a.name) AS accountName
        FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
        WHERE t.id = :id
        """,
    )
    fun observeDetail(id: String): Flow<TransactionDetail?>

    @Query("UPDATE transactions SET categoryId = :categoryId, categorySource = :source, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: Long, source: CategorySource, now: Long)

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
          AND (:query = '' OR t.description LIKE '%' || :query || '%' OR t.merchantKey LIKE '%' || :query || '%'
               OR t.note LIKE '%' || :query || '%')
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
        ORDER BY t.occurredAt DESC, t.id DESC
        LIMIT :limit
        """,
    )
    fun observeRows(query: String, limit: Int, categoryId: Long? = null): Flow<List<TransactionRow>>
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
