package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.backup.BackupCrypto
import com.emotionalleaves.sprout.data.backup.BackupException
import com.emotionalleaves.sprout.data.backup.BackupRepository
import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.categories.CategoryRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var env: TestEnv
    private lateinit var repo: BackupRepository
    private val pw = "a long password".toCharArray()

    @Before fun setUp() {
        env = TestEnv()
        repo = BackupRepository(env.db, env.clock)
        runBlocking {
            env.secure.saveAccessUrl(env.accessUrl)
            env.server.enqueue(MockResponse().setBody(env.fixture("v2_basic.json")))
            env.repo().sync(SyncTrigger.MANUAL)
            val cats = CategoryRepository(env.db, env.clock)
            cats.recategorize("sf:CON-111:ACT-CHK-1:TX-1003", env.db.categories().getByKey(DefaultCategories.ENTERTAINMENT)!!.id, true)
            cats.setNote("sf:CON-111:ACT-CHK-1:TX-1001", "weekly shop")
            BudgetRepository(env.db, env.clock).setBudget(env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id, 40_000, false, YearMonth.of(2026, 11))
            cats.setSplits("sf:CON-111:ACT-CHK-1:TX-1001", listOf(com.emotionalleaves.sprout.domain.split.SplitPart(env.db.categories().getByKey(DefaultCategories.SHOPPING)!!.id, 500)))
            val tags = com.emotionalleaves.sprout.data.tags.TagRepository(env.db, env.clock)
            tags.attach("sf:CON-111:ACT-CHK-1:TX-1001", tags.getOrCreate("Groceries run")!!)
            BudgetRepository(env.db, env.clock).setRollover(env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id, true, YearMonth.of(2026, 10))
        }
    }

    @After fun tearDown() = env.close()

    private fun snapshot() = runBlocking {
        listOf(
            env.db.backup().transactions().sortedBy { it.id }.toString(),
            env.db.accounts().getAll().sortedBy { it.id }.toString(),
            env.db.categories().getAll().sortedBy { it.id }.toString(),
            env.db.merchantRules().getAll().sortedBy { it.id }.toString(),
            env.db.budgets().observeDefaults().first().toString(),
            env.db.backup().budgetRollovers().toString(),
            env.db.backup().transactionSplits().toString(),
            env.db.backup().tags().toString(),
            env.db.backup().transactionTags().toString(),
            env.db.connections().getAll().toString(),
        )
    }

    @Test fun backupRestoresEverythingExactly() = runBlocking {
        val before = snapshot()
        val file = repo.create(pw)
        DataManager(null, env.db, env.settings, env.secure, env.repo()).deleteAll()
        assertEquals(0, env.db.backup().transactions().size)
        val s = repo.restore(repo.read(file, pw))
        assertEquals(6, s.transactions)
        assertEquals(before, snapshot())
    }

    @Test fun backupNeverContainsCredentials() = runBlocking {
        val json = repo.toJson()
        assertFalse(json.contains("demo:demo"))
        assertFalse(json.contains("access"))
    }

    @Test fun wrongPasswordIsRejected() = runBlocking {
        val file = repo.create(pw)
        try { repo.read(file, "not the password".toCharArray()); fail() } catch (e: BackupException) {
            assertEquals("Wrong password, or the file was changed.", e.message)
        }
    }

    @Test fun newerSchemaIsRefused() = runBlocking {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(repo.toJson()).jsonObject
        val newer = buildJsonObject { obj.forEach { (k, v) -> if (k == "schema") put("schema", 99) else put(k, v) } }
        val file = BackupCrypto.encrypt(newer.toString().toByteArray(), pw, 2_000)
        try { repo.read(file, pw); fail() } catch (e: BackupException) {
            assertEquals("This backup was made by a newer version of Sprout. Update Sprout first.", e.message)
        }
    }

    @Test fun brokenBackupLeavesDataUnchanged() = runBlocking {
        val before = snapshot()
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(repo.toJson()).jsonObject
        // Transactions pointing at an account that isn't in the backup: the foreign key fails mid-restore.
        val broken = buildJsonObject {
            obj.forEach { (k, v) -> if (k == "accounts") put(k, kotlinx.serialization.json.JsonArray(emptyList())) else put(k, v) }
        }
        try { repo.restore(broken); fail() } catch (e: Exception) { /* expected */ }
        assertEquals(before, snapshot())
        assertEquals(JsonPrimitive(com.emotionalleaves.sprout.data.db.SproutDatabase.VERSION), obj["schema"])
    }
}
