package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.csv.Csv
import com.emotionalleaves.sprout.data.csv.CsvImport
import com.emotionalleaves.sprout.data.csv.ExportKind
import com.emotionalleaves.sprout.data.csv.ExportRepository
import com.emotionalleaves.sprout.data.csv.ImportRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.secure.StoredSecret
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DataManagementTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private lateinit var manager: DataManager
    private lateinit var export: ExportRepository
    private lateinit var import: ImportRepository
    private lateinit var manualId: String

    @Before fun setUp() { runBlocking {
        env = TestEnv()
        manager = DataManager(null, env.db, env.settings, env.secure, env.repo())
        export = ExportRepository(env.db) { zone }
        import = ImportRepository(env.db, env.clock, { zone })
        env.secure.saveAccessUrl(env.accessUrl)
        env.server.enqueue(MockResponse().setBody(env.fixture("v2_basic.json")))
        env.repo().sync(SyncTrigger.MANUAL)
        manualId = import.createManualAccount("Cash jar", AccountType.CASH)
        val rows = Csv.parse(requireNotNull(javaClass.getResource("/csv/card_chase_style.csv")).readText())
        import.import(manualId, CsvImport.parse(rows, CsvImport.detect(rows)!!))
        env.settings.setOnboardingComplete(true)
    } }

    @After fun tearDown() = env.close()

    private fun count() = runBlocking { env.db.transactions().observeCount().first() }

    @Test fun disconnectKeepsData(): Unit = runBlocking {
        manager.disconnect(alsoDeleteBankData = false)
        assertEquals(StoredSecret.None, env.secure.readAccessUrl())
        assertTrue(env.cipher.deleted)
        assertEquals(10, count())
        assertEquals(1, env.db.connections().getAll().size)
    }

    @Test fun disconnectCanDeleteBankDataButKeepsImports(): Unit = runBlocking {
        manager.disconnect(alsoDeleteBankData = true)
        assertEquals(4, count())
        assertEquals(listOf(manualId), env.db.accounts().getAll().map { it.id })
        assertTrue(env.db.connections().getAll().isEmpty())
    }

    @Test fun deleteAllWipesEverythingAndReseeds(): Unit = runBlocking {
        env.db.merchantRules().insert(com.emotionalleaves.sprout.data.db.MerchantRuleEntity(
            matchType = com.emotionalleaves.sprout.domain.categorize.RuleType.EXACT, pattern = "X",
            categoryId = env.db.categories().getByKey(DefaultCategories.OTHER)!!.id,
            origin = com.emotionalleaves.sprout.data.db.RuleOrigin.USER_CREATED, hitCount = 1, createdAt = 0, updatedAt = 0))
        manager.deleteAll()
        assertEquals(0, count())
        assertTrue(env.db.accounts().getAll().isEmpty())
        assertTrue(env.db.merchantRules().getAll().isEmpty())
        assertEquals(StoredSecret.None, env.secure.readAccessUrl())
        assertFalse(env.settings.current().onboardingComplete)
        assertEquals(DefaultCategories.all.size, env.db.categories().getAll().size)
    }

    @Test fun transactionExportReimportsIdentically(): Unit = runBlocking {
        val text = export.export(ExportKind.TRANSACTIONS)
        val rows = Csv.parse(text)
        val back = CsvImport.parse(rows, CsvImport.detect(rows)!!)
        assertTrue(back.errors.isEmpty())
        val target = import.createManualAccount("Round trip", AccountType.CHECKING)
        assertEquals(10, import.import(target, back).imported)
        val original = env.db.transactions().exportRows(Long.MIN_VALUE, Long.MAX_VALUE)
            .filter { it.accountName != "Round trip" }.map { Triple(it.occurredAt.let { t -> java.time.Instant.ofEpochSecond(t).atZone(zone).toLocalDate() }, it.amountMinor, it.description) }
        val copied = env.db.transactions().exportRows(Long.MIN_VALUE, Long.MAX_VALUE)
            .filter { it.accountName == "Round trip" }.map { Triple(java.time.Instant.ofEpochSecond(it.occurredAt).atZone(zone).toLocalDate(), it.amountMinor, it.description) }
        assertEquals(original.sortedBy { it.toString() }, copied.sortedBy { it.toString() })
    }

    @Test fun noExportContainsCredentials(): Unit = runBlocking {
        for (kind in ExportKind.entries) {
            val text = export.export(kind)
            assertFalse(kind.name, text.contains("demo:demo") || text.contains("://"))
            assertTrue(kind.name, Csv.parse(text).isNotEmpty())
        }
    }
}
