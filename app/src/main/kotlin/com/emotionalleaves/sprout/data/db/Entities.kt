package com.emotionalleaves.sprout.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.RuleType

enum class ConnectionStatus { OK, NEEDS_ATTENTION, REVOKED }
enum class AccountSource { SIMPLEFIN, MANUAL }
enum class RuleOrigin { USER_CORRECTION, USER_CREATED }
enum class Cadence { WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY }
enum class RecurringStatus { DETECTED, CONFIRMED, DISMISSED, MANUAL }
enum class SyncTrigger { PERIODIC, MANUAL, ONBOARDING }
enum class SyncResult { SUCCESS, PARTIAL, FAILED }

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val connId: String,
    val name: String,
    val orgId: String?,
    val orgUrl: String?,
    val sfinUrl: String?,
    val status: ConnectionStatus,
    val lastErrorCode: String?,
    /** Sanitized server message, shown as plain text. */
    val lastErrorMessage: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["connId"],
            childColumns = ["connId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("connId")],
)
data class AccountEntity(
    /** "sf:{connId}:{remoteId}" or "manual:{uuid}". */
    @PrimaryKey val id: String,
    val source: AccountSource,
    val remoteId: String?,
    val connId: String?,
    val name: String,
    val displayName: String?,
    val type: AccountType,
    val currency: String,
    val balanceMinor: Long,
    val availableBalanceMinor: Long?,
    val balanceDate: Long,
    val includeInTotals: Boolean,
    val hidden: Boolean,
    val firstSyncCompleted: Boolean,
    val lastSyncedAt: Long?,
    val createdAt: Long,
    /** act.* problem reported by the server for this account, if any (sanitized). */
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: name
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("postedAt"),
        Index("occurredAt"),
        Index("merchantKey"),
        Index("categoryId"),
        Index("pending"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val remoteId: String,
    /** Epoch seconds; 0 while pending. */
    val postedAt: Long,
    val transactedAt: Long?,
    /**
     * The time used for display and month assignment: postedAt when posted, otherwise transactedAt,
     * otherwise the time the pending row was first seen.
     */
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val merchantKey: String,
    val pending: Boolean,
    val categoryId: Long,
    val categorySource: CategorySource,
    val note: String?,
    val excludedFromBudget: Boolean,
    val recurringChargeId: Long?,
    val alertedAt: Long?,
    val importedAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["key"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable key for seeded categories; null for ones the user adds. */
    val key: String?,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorArgb: Int,
    val sortOrder: Int,
    val archived: Boolean,
)

@Entity(
    tableName = "category_budgets",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
)
data class CategoryBudgetEntity(
    @PrimaryKey val categoryId: Long,
    val amountMinor: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "budget_overrides",
    primaryKeys = ["categoryId", "yearMonth"],
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
)
data class BudgetOverrideEntity(
    val categoryId: Long,
    /** "YYYY-MM". */
    val yearMonth: String,
    val amountMinor: Long,
)

@Entity(
    tableName = "budget_alerts",
    primaryKeys = ["categoryId", "yearMonth", "threshold"],
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
)
data class BudgetAlertEntity(
    val categoryId: Long,
    val yearMonth: String,
    /** 80 or 100. */
    val threshold: Int,
    val firedAt: Long,
)

@Entity(
    tableName = "merchant_rules",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["matchType", "pattern"], unique = true), Index("categoryId")],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: RuleType,
    val pattern: String,
    val categoryId: Long,
    val origin: RuleOrigin,
    val hitCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "recurring_charges", indices = [Index("merchantKey")])
data class RecurringChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,
    val displayName: String,
    val categoryId: Long,
    val accountId: String?,
    val cadence: Cadence,
    val typicalAmountMinor: Long,
    val lastChargeAt: Long,
    val nextDueAt: Long,
    val status: RecurringStatus,
    val userEdited: Boolean,
    val confidence: Float,
    val occurrences: Int,
    val updatedAt: Long,
)

@Entity(tableName = "sync_log", indices = [Index("startedAt")])
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val trigger: SyncTrigger,
    val result: SyncResult,
    val newCount: Int,
    val updatedCount: Int,
    /** Comma-separated codes, e.g. "con.auth,http.503". */
    val errorCodes: String,
    /** Sanitized and redacted. Never contains URLs or credentials. */
    val message: String,
    /** HTTP requests this sync made to SimpleFIN; used to stay under the Bridge's daily limit. */
    @ColumnInfo(defaultValue = "0") val requestCount: Int = 0,
)
