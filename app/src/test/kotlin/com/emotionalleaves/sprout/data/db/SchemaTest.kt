package com.emotionalleaves.sprout.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Guards the "never wipe user data" rule. From version 2 on, each migration gets its own test here that inserts
 * realistic rows at the old version and checks they survive.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SproutDatabase::class.java)

    @Test fun everyVersionHasCommittedSchema() {
        for (v in 1..SproutDatabase.VERSION) {
            assertTrue("app/schemas/.../$v.json missing", File("schemas/${SproutDatabase::class.java.name}/$v.json").exists())
        }
    }

    @Test fun migrationsCoverEveryVersionStep() {
        val steps = SproutDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until SproutDatabase.VERSION).map { it to it + 1 }
        assertEquals(expected, steps)
    }

    @Test fun version1CanBeCreatedFromCommittedSchema() {
        helper.createDatabase("schema-v1.db", 1).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, `key`, name, kind, iconKey, colorArgb, sortOrder, archived) " +
                    "VALUES (1, 'groceries', 'Groceries', 'EXPENSE', 'groceries', 0, 0, 0)",
            )
            db.query("SELECT COUNT(*) FROM categories").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test fun sourceNeverUsesDestructiveMigration() {
        val banned = "fallbackTo" + "DestructiveMigration"
        val hits = File("src/main").walkTopDown().filter { it.isFile && it.readText().contains(banned) }.toList()
        assertTrue("Destructive migration found in $hits", hits.isEmpty())
    }
}
