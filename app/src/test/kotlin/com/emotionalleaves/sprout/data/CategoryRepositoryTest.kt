package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.categories.CategoryRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.RuleType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var env: TestEnv
    private lateinit var repo: CategoryRepository

    private fun account(id: String, txs: String) = """
        {"id":"$id","name":"Checking $id","conn_id":"C","currency":"USD","balance":"100.00","balance-date":1763800000,"transactions":[$txs]}
    """.trimIndent()

    private fun tx(id: String, desc: String, amount: String, posted: Long = 1763700000) =
        """{"id":"$id","posted":$posted,"amount":"$amount","description":"$desc"}"""

    private fun body(vararg txs: String) =
        """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
           "accounts":[${account("A", txs.joinToString(","))}]}"""

    @Before fun setUp() {
        env = TestEnv()
        repo = CategoryRepository(env.db, env.clock)
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun sync(vararg txs: String) = runBlocking {
        env.server.enqueue(MockResponse().setBody(body(*txs)))
        env.repo().sync(SyncTrigger.MANUAL)
    }

    private fun cat(key: String) = runBlocking { env.db.categories().getByKey(key)!!.id }
    private fun get(id: String) = runBlocking { env.db.transactions().getById("sf:C:A:$id")!! }

    @Test fun correctionLearnsMerchantRuleAndRecategorizesOthers() = runBlocking {
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"), tx("2", "CASEYS #2280 LINCOLN NE", "-12.00"), tx("3", "CASEYS #3021 LINCOLN NE", "-5.00"))
        assertEquals(cat(DefaultCategories.GAS), get("1").categoryId)
        // The user had already set #3 by hand: it must stay.
        env.db.transactions().setCategory("sf:C:A:3", cat(DefaultCategories.GIFTS), CategorySource.USER, 1)

        val others = repo.recategorize("sf:C:A:1", cat(DefaultCategories.EATING_OUT), alwaysForMerchant = true)

        assertEquals(1, others)
        assertEquals(CategorySource.USER, get("1").categorySource)
        assertEquals(cat(DefaultCategories.EATING_OUT), get("2").categoryId)
        assertEquals(CategorySource.MERCHANT_RULE, get("2").categorySource)
        assertEquals(cat(DefaultCategories.GIFTS), get("3").categoryId)
        val rule = env.db.merchantRules().getAll().single()
        assertEquals(RuleType.EXACT, rule.matchType)
        assertEquals("CASEYS LINCOLN", rule.pattern)
    }

    @Test fun correctionAppliesToNextSyncedTransactionFromMerchant() = runBlocking {
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"))
        repo.recategorize("sf:C:A:1", cat(DefaultCategories.EATING_OUT), alwaysForMerchant = true)
        // "App restart": a brand-new repository instance reads the rule from the database.
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"), tx("9", "CASEYS #4411 LINCOLN NE", "-7.50", posted = 1763750000))
        assertEquals(cat(DefaultCategories.EATING_OUT), get("9").categoryId)
        assertEquals(CategorySource.MERCHANT_RULE, get("9").categorySource)
        assertEquals(CategorySource.USER, get("1").categorySource)
    }

    @Test fun oneOffCorrectionDoesNotCreateRule() = runBlocking {
        sync(tx("1", "NETFLIX.COM", "-15.49"), tx("2", "NETFLIX.COM", "-15.49", posted = 1763710000))
        val others = repo.recategorize("sf:C:A:1", cat(DefaultCategories.ENTERTAINMENT), alwaysForMerchant = false)
        assertEquals(0, others)
        assertTrue(env.db.merchantRules().getAll().isEmpty())
        assertEquals(cat(DefaultCategories.SUBSCRIPTIONS), get("2").categoryId)
    }

    @Test fun keywordRuleAppliesToExistingAndDeletingRestoresBuiltIn() = runBlocking {
        sync(tx("1", "SQ *HAYMARKET FARMERS MARKET", "-22.00"))
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("1").categoryId)
        val ruleId = repo.addContainsRule("farmers market", cat(DefaultCategories.GROCERIES))!!
        assertEquals(cat(DefaultCategories.GROCERIES), get("1").categoryId)
        repo.deleteRule(ruleId)
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("1").categoryId)
        assertEquals(CategorySource.NONE, get("1").categorySource)
    }

    @Test fun tooShortKeywordIsRejected() = runBlocking {
        env.repo().ensureSeeded()
        assertNull(repo.addContainsRule(" a ", cat(DefaultCategories.OTHER)))
    }

    @Test fun archivingKeepsTransactionsCategory() = runBlocking {
        sync(tx("1", "NETFLIX.COM", "-15.49"))
        repo.setArchived(cat(DefaultCategories.SUBSCRIPTIONS), true)
        assertEquals(cat(DefaultCategories.SUBSCRIPTIONS), get("1").categoryId)
        assertTrue(env.db.categories().getById(cat(DefaultCategories.SUBSCRIPTIONS))!!.archived)
    }

    @Test fun notesAreSanitizedAndBlankClears() = runBlocking {
        sync(tx("1", "NETFLIX.COM", "-15.49"))
        repo.setNote("sf:C:A:1", "  family plan \u0007 ")
        assertEquals("family plan", get("1").note)
        repo.setNote("sf:C:A:1", "   ")
        assertNull(get("1").note)
    }

    @Test fun newCategoryCanBeUsed() = runBlocking {
        sync(tx("1", "ZQXV HOLDINGS", "-5.00"))
        val id = repo.addCategory("Pets", com.emotionalleaves.sprout.domain.categorize.CategoryKind.EXPENSE, 0xFF00838F.toInt())
        repo.recategorize("sf:C:A:1", id, alwaysForMerchant = true)
        assertEquals(id, get("1").categoryId)
    }
}
