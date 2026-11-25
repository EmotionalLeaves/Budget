package com.emotionalleaves.sprout.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, data-preserving migrations. Each one only adds what changed and never drops user data.
 * SQL matches the exported schema JSON in app/schemas exactly; SchemaTest and MigrationTest check it.
 */
object Migrations {

    /** v2: savings goals. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `targetDate` TEXT, `accountId` TEXT, " +
                    "`savedMinor` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_accountId` ON `savings_goals` (`accountId`)")
        }
    }

    /** v3: budget rollover. A new table only; existing budgets are untouched and rollover starts off. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budget_rollovers` (`categoryId` INTEGER NOT NULL, `sinceMonth` TEXT NOT NULL, " +
                    "PRIMARY KEY(`categoryId`), FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** v4: split transactions. A new table only; no existing row changes. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transaction_splits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`transactionId` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_transactionId` ON `transaction_splits` (`transactionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_categoryId` ON `transaction_splits` (`categoryId`)")
        }
    }

    /** v5: tags. Two new tables; no existing row changes. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`colorArgb` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transaction_tags` (`transactionId` TEXT NOT NULL, `tagId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`transactionId`, `tagId`), " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tags_tagId` ON `transaction_tags` (`tagId`)")
        }
    }

    /**
     * v6: review inbox, planned expenses, receipts. Adds columns and a table; every existing transaction counts as
     * already reviewed (so the inbox starts empty instead of holding your whole history).
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reviewedAt` INTEGER")
            db.execSQL("UPDATE `transactions` SET `reviewedAt` = `updatedAt`")
            db.execSQL("ALTER TABLE `savings_goals` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'SAVINGS'")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` TEXT NOT NULL, " +
                    "`fileName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipts_transactionId` ON `receipts` (`transactionId`)")
        }
    }

    /** v7: your own merchant names. A new table only. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `merchant_names` (`merchantKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`merchantKey`))",
            )
        }
    }
}
