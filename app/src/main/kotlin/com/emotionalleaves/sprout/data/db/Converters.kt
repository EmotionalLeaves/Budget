package com.emotionalleaves.sprout.data.db

import androidx.room.TypeConverter
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.domain.recurring.Cadence

/**
 * Enums are stored by name. Unknown names (from a future version after a downgrade) fall back to a safe value
 * instead of crashing.
 */
class Converters {
    @TypeConverter fun fromConnectionStatus(v: ConnectionStatus): String = v.name
    @TypeConverter fun toConnectionStatus(v: String): ConnectionStatus = enumOr(v, ConnectionStatus.NEEDS_ATTENTION)

    @TypeConverter fun fromAccountSource(v: AccountSource): String = v.name
    @TypeConverter fun toAccountSource(v: String): AccountSource = enumOr(v, AccountSource.MANUAL)

    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(v: String): AccountType = enumOr(v, AccountType.OTHER)

    @TypeConverter fun fromCategorySource(v: CategorySource): String = v.name
    @TypeConverter fun toCategorySource(v: String): CategorySource = enumOr(v, CategorySource.NONE)

    @TypeConverter fun fromCategoryKind(v: CategoryKind): String = v.name
    @TypeConverter fun toCategoryKind(v: String): CategoryKind = enumOr(v, CategoryKind.EXPENSE)

    @TypeConverter fun fromRuleType(v: RuleType): String = v.name
    @TypeConverter fun toRuleType(v: String): RuleType = enumOr(v, RuleType.EXACT)

    @TypeConverter fun fromRuleOrigin(v: RuleOrigin): String = v.name
    @TypeConverter fun toRuleOrigin(v: String): RuleOrigin = enumOr(v, RuleOrigin.USER_CREATED)

    @TypeConverter fun fromCadence(v: Cadence): String = v.name
    @TypeConverter fun toCadence(v: String): Cadence = enumOr(v, Cadence.MONTHLY)

    @TypeConverter fun fromRecurringStatus(v: RecurringStatus): String = v.name
    @TypeConverter fun toRecurringStatus(v: String): RecurringStatus = enumOr(v, RecurringStatus.DETECTED)

    @TypeConverter fun fromSyncTrigger(v: SyncTrigger): String = v.name
    @TypeConverter fun toSyncTrigger(v: String): SyncTrigger = enumOr(v, SyncTrigger.MANUAL)

    @TypeConverter fun fromSyncResult(v: SyncResult): String = v.name
    @TypeConverter fun toSyncResult(v: String): SyncResult = enumOr(v, SyncResult.FAILED)

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
