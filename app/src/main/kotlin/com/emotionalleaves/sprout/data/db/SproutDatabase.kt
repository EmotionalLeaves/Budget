package com.emotionalleaves.sprout.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * The on-device database. Version 1 contains every entity from the spec so later milestones need fewer migrations.
 *
 * RULES (never break these):
 * - Every schema change bumps [VERSION], adds an explicit Migration to [MIGRATIONS] and a migration test.
 * - Destructive migration is forbidden. An update must never wipe the user's data.
 */
@Database(
    entities = [
        ConnectionEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        CategoryBudgetEntity::class,
        BudgetOverrideEntity::class,
        BudgetAlertEntity::class,
        MerchantRuleEntity::class,
        RecurringChargeEntity::class,
        SyncLogEntity::class,
        SavingsGoalEntity::class,
        BudgetRolloverEntity::class,
        TransactionSplitEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        ReceiptEntity::class,
    ],
    version = SproutDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SproutDatabase : RoomDatabase() {
    abstract fun connections(): ConnectionDao
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun merchantRules(): MerchantRuleDao
    abstract fun syncLog(): SyncLogDao
    abstract fun budgets(): BudgetDao
    abstract fun alerts(): AlertDao
    abstract fun recurring(): RecurringDao
    abstract fun backup(): BackupDao
    abstract fun goals(): GoalDao
    abstract fun splits(): SplitDao
    abstract fun tags(): TagDao
    abstract fun insights(): InsightDao
    abstract fun receipts(): ReceiptDao

    companion object {
        const val VERSION = 6
        const val NAME = "sprout.db"

        /** All migrations, oldest first. */
        val MIGRATIONS: Array<Migration> = arrayOf(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5, Migrations.MIGRATION_5_6)

        fun build(context: Context): SproutDatabase =
            Room.databaseBuilder(context.applicationContext, SproutDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
