package com.emotionalleaves.sprout.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every migration inserts realistic rows at the old version and proves they survive intact.
 * Add one test per new version.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SproutDatabase::class.java)

    private fun seedV1(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO categories (id, `key`, name, kind, iconKey, colorArgb, sortOrder, archived) VALUES (1, 'groceries', 'Groceries', 'EXPENSE', 'groceries', -11141291, 0, 0)")
        db.execSQL("INSERT INTO categories (id, `key`, name, kind, iconKey, colorArgb, sortOrder, archived) VALUES (2, NULL, 'Pets', 'EXPENSE', 'custom', -16743537, 21, 0)")
        db.execSQL("INSERT INTO connections (connId, name, orgId, orgUrl, sfinUrl, status, lastErrorCode, lastErrorMessage, updatedAt) VALUES ('C', 'Prairie Bank', 'O', NULL, 'https://bridge.example.test/simplefin', 'OK', NULL, NULL, 100)")
        db.execSQL("INSERT INTO accounts (id, source, remoteId, connId, name, displayName, type, currency, balanceMinor, availableBalanceMinor, balanceDate, includeInTotals, hidden, firstSyncCompleted, lastSyncedAt, createdAt, lastErrorCode, lastErrorMessage) VALUES ('sf:C:A', 'SIMPLEFIN', 'A', 'C', 'Checking', 'Bills account', 'CHECKING', 'USD', 152345, 150000, 100, 1, 0, 1, 100, 50, NULL, NULL)")
        db.execSQL("INSERT INTO transactions (id, accountId, remoteId, postedAt, transactedAt, occurredAt, amountMinor, currency, description, merchantKey, pending, categoryId, categorySource, note, excludedFromBudget, recurringChargeId, alertedAt, importedAt, updatedAt) VALUES ('sf:C:A:1', 'sf:C:A', '1', 1763500000, NULL, 1763500000, -5421, 'USD', 'HY-VEE #1234', 'HY-VEE', 0, 1, 'USER', 'weekly shop', 1, NULL, NULL, 90, 95)")
        db.execSQL("INSERT INTO category_budgets (categoryId, amountMinor, updatedAt) VALUES (1, 40000, 100)")
        db.execSQL("INSERT INTO budget_overrides (categoryId, yearMonth, amountMinor) VALUES (1, '2026-11', 50000)")
        db.execSQL("INSERT INTO merchant_rules (id, matchType, pattern, categoryId, origin, hitCount, createdAt, updatedAt) VALUES (7, 'EXACT', 'HY-VEE', 2, 'USER_CORRECTION', 3, 1, 2)")
    }

    @Test fun migrate1To2KeepsAllData() {
        helper.createDatabase(name, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(name, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT note, categorySource, excludedFromBudget, amountMinor FROM transactions WHERE id = 'sf:C:A:1'").use { c ->
                c.moveToFirst()
                assertEquals("weekly shop", c.getString(0))
                assertEquals("USER", c.getString(1))
                assertEquals(1, c.getInt(2))
                assertEquals(-5421L, c.getLong(3))
            }
            db.query("SELECT COUNT(*) FROM savings_goals").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
        // Open with the real database class and every migration: Room validates the final schema.
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), SproutDatabase::class.java, name)
            .addMigrations(*SproutDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("Bills account", room.accounts().getAll().single().displayName)
            assertEquals(2, room.categories().getAll().size)
            assertEquals(1, room.merchantRules().getAll().single().let { if (it.pattern == "HY-VEE") 1 else 0 })
            assertEquals(50000L, room.backup().budgetOverrides().single().amountMinor)
        }
        room.close()
    }

    @Test fun migrate2To3KeepsAllDataAndStartsWithRolloverOff() {
        helper.createDatabase(name, 2).use { db ->
            seedV1(db)
            db.execSQL("INSERT INTO savings_goals (id, name, targetMinor, targetDate, accountId, savedMinor, archived, createdAt, updatedAt) VALUES (1, 'Trip', 100000, '2027-06-01', NULL, 25000, 0, 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM budget_rollovers").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT amountMinor FROM category_budgets WHERE categoryId = 1").use { c -> c.moveToFirst(); assertEquals(40000L, c.getLong(0)) }
            db.query("SELECT savedMinor FROM savings_goals WHERE id = 1").use { c -> c.moveToFirst(); assertEquals(25000L, c.getLong(0)) }
        }
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), SproutDatabase::class.java, name)
            .addMigrations(*SproutDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("weekly shop", room.backup().transactions().single().note)
            assertEquals("Trip", room.goals().getAll().single().name)
        }
        room.close()
    }

    @Test fun migrate1To3InOneGo() {
        helper.createDatabase(name, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(name, 3, true, *SproutDatabase.MIGRATIONS).close()
    }
}
