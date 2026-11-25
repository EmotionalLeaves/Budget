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
}
