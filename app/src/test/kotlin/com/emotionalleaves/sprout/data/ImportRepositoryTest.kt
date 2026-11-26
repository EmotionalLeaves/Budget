package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.csv.Csv
import com.emotionalleaves.sprout.data.csv.CsvImport
import com.emotionalleaves.sprout.data.csv.CsvParseResult
import com.emotionalleaves.sprout.data.csv.ImportRepository
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ImportRepositoryTest {
    private lateinit var env: TestEnv
    private lateinit var repo: ImportRepository
    private var afterImportCalls = 0

    @Before fun setUp() {
        env = TestEnv()
        repo = ImportRepository(env.db, env.clock, { ZoneId.of("America/Chicago") }, afterImport = { afterImportCalls++ })
        runBlocking { env.repo().ensureSeeded() }
    }

    @After fun tearDown() = env.close()

    private fun parsed(name: String): CsvParseResult {
        val text = requireNotNull(javaClass.getResource("/csv/$name")).readText()
        val rows = Csv.parse(text)
        return CsvImport.parse(rows, CsvImport.detect(rows)!!)
    }

    @Test fun importsCategorizesAndSetsBalance() = runBlocking {
        val account = repo.createManualAccount("Old Card", AccountType.CREDIT)
        val s = repo.import(account, parsed("card_chase_style.csv"))
        assertEquals(4, s.imported)
        assertEquals(0, s.duplicates)
        assertEquals(1, afterImportCalls)
        val all = env.db.transactions().getByIds(CsvImport.ids(account, parsed("card_chase_style.csv").rows))
        val starbucks = all.single { it.description.startsWith("STARBUCKS") }
        assertEquals(env.db.categories().getByKey(DefaultCategories.EATING_OUT)!!.id, starbucks.categoryId)
        // Imports never trigger unusual-charge alerts.
        assertTrue(all.all { it.alertedAt != null })
        val acct = env.db.accounts().getAll().single()
        assertEquals(-575L - 4210L + 30000L - 6103L, acct.balanceMinor)
    }

    @Test fun reimportingTheSameFileAddsNothing() = runBlocking {
        val account = repo.createManualAccount("Card", AccountType.CREDIT)
        repo.import(account, parsed("card_chase_style.csv"))
        val again = repo.import(account, parsed("card_chase_style.csv"))
        assertEquals(0, again.imported)
        assertEquals(4, again.duplicates)
        assertEquals(4, env.db.transactions().observeCount().first())
        assertEquals(1, afterImportCalls)
    }

    @Test fun overlappingFileOnlyAddsNewRows() = runBlocking {
        val account = repo.createManualAccount("Card", AccountType.CREDIT)
        val full = parsed("card_chase_style.csv")
        repo.import(account, full.copy(rows = full.rows.take(2)))
        val s = repo.import(account, full)
        assertEquals(2, s.imported)
        assertEquals(2, s.duplicates)
    }

    @Test fun failedRowsAreReported() = runBlocking {
        val account = repo.createManualAccount("Checking", AccountType.CHECKING)
        val s = repo.import(account, parsed("malformed_rows.csv"))
        assertEquals(2, s.imported)
        assertEquals(4, s.failed.size)
        assertNotNull(env.db.accounts().getAll().single().lastSyncedAt)
    }

    @Test fun bankCategoryColumnIsUsedButYourRulesWin() = runBlocking {
        val account = repo.createManualAccount("Old Card", AccountType.CREDIT)
        // A rule of yours: Amazon is Hobbies & Art. The bank says Shopping; your rule wins.
        val hobbies = env.db.categories().getByKey(DefaultCategories.HOBBIES)!!.id
        com.emotionalleaves.sprout.data.categories.CategoryRepository(env.db, env.clock).addContainsRule("AMAZON", hobbies)
        repo.import(account, parsed("card_chase_style.csv"))
        val all = env.db.transactions().getByIds(CsvImport.ids(account, parsed("card_chase_style.csv").rows))
        assertEquals(hobbies, all.single { it.description.startsWith("AMAZON") }.categoryId)
        val joes = all.single { it.description.startsWith("TRADER") }
        assertEquals(env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id, joes.categoryId)
        assertEquals(com.emotionalleaves.sprout.domain.categorize.CategorySource.IMPORT, joes.categorySource)
    }

    @Test fun europeanFileImportsWithBankCategories() = runBlocking {
        val account = repo.createManualAccount("Konto", AccountType.CHECKING)
        val s = repo.import(account, parsed("european_comma_decimals.csv"))
        assertEquals(4, s.imported)
        val all = env.db.transactions().getByIds(CsvImport.ids(account, parsed("european_comma_decimals.csv").rows))
        assertEquals(env.db.categories().getByKey(DefaultCategories.TRAVEL)!!.id, all.single { it.description.startsWith("BAHN") }.categoryId)
        assertEquals(-4_510L - 123_456L - 350L + 245_000L, env.db.accounts().getAll().single().balanceMinor)
    }
}
