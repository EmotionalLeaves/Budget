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
        sync(tx("1", "SQ *HAYMARKET ZQX VENDORS", "-22.00"))
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("1").categoryId)
        val ruleId = repo.addContainsRule("zqx vendors", cat(DefaultCategories.GROCERIES))!!
        assertEquals(cat(DefaultCategories.GROCERIES), get("1").categoryId)
        val undo = repo.deleteRule(ruleId)
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("1").categoryId)
        assertEquals(CategorySource.NONE, get("1").categorySource)
        // Undo brings the same rule back and re-sorts.
        undo()
        assertEquals(cat(DefaultCategories.GROCERIES), get("1").categoryId)
        assertEquals(ruleId, env.db.merchantRules().getAll().single().id)
        undo() // twice is harmless
        assertEquals(1, env.db.merchantRules().getAll().size)
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

    @Test fun undoPutsEverythingBack() = runBlocking {
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"), tx("2", "CASEYS #2280 LINCOLN NE", "-12.00"))
        val before = listOf(get("1"), get("2")).map { it.categoryId to it.categorySource }
        val change = repo.recategorizeWithUndo("sf:C:A:1", cat(DefaultCategories.EATING_OUT), alwaysForMerchant = true)
        assertEquals(1, change.others)
        change.undo()
        assertEquals(before, listOf(get("1"), get("2")).map { it.categoryId to it.categorySource })
        assertTrue(env.db.merchantRules().getAll().isEmpty())
    }

    @Test fun undoRestoresAnEarlierRule() = runBlocking {
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"))
        repo.recategorize("sf:C:A:1", cat(DefaultCategories.GIFTS), alwaysForMerchant = true)
        val change = repo.recategorizeWithUndo("sf:C:A:1", cat(DefaultCategories.EATING_OUT), alwaysForMerchant = true)
        change.undo()
        assertEquals(cat(DefaultCategories.GIFTS), env.db.merchantRules().getAll().single().categoryId)
        assertEquals(cat(DefaultCategories.GIFTS), get("1").categoryId)
    }

    @Test fun onePickTeachesSimilarPurchases() = runBlocking {
        sync(
            tx("1", "SQ *PRAIRIE ART CLASS", "-30.00"), tx("2", "WESTSIDE ART CLASS", "-25.00", posted = 1763710000),
            tx("3", "NORTHSTAR ART CLASS", "-20.00", posted = 1763720000), tx("4", "ZQX 4471 HOLDINGS", "-9.00", posted = 1763730000),
            tx("5", "ACME WIDGET SUPPLY CO", "-11.00", posted = 1763740000),
        )
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("3").categoryId)
        val hobbies = cat(DefaultCategories.HOBBIES)
        repo.recategorize("sf:C:A:1", hobbies, alwaysForMerchant = false)
        repo.recategorize("sf:C:A:5", cat(DefaultCategories.SHOPPING), alwaysForMerchant = false)
        val change = repo.recategorizeWithUndo("sf:C:A:2", hobbies, alwaysForMerchant = false)
        // Two art classes taught it: the third is sorted without being touched.
        assertEquals(hobbies, get("3").categoryId)
        assertEquals(CategorySource.LEARNED, get("3").categorySource)
        assertTrue(change.learned >= 1)
        // Nothing similar: left for you.
        assertEquals(cat(DefaultCategories.UNCATEGORIZED), get("4").categoryId)
        // Undo puts the guess back too.
        change.undo()
        assertEquals(CategorySource.NONE, get("3").categorySource)
    }

    @Test fun learnedGuessesNeverOverrideYourPicks() = runBlocking {
        sync(tx("1", "WALMART 1234", "-30.00"), tx("2", "WALMART 5678", "-25.00", posted = 1763710000), tx("3", "WALMART 9999", "-5.00", posted = 1763720000))
        env.db.transactions().setCategory("sf:C:A:3", cat(DefaultCategories.GIFTS), CategorySource.USER, 1)
        repo.recategorize("sf:C:A:1", cat(DefaultCategories.GROCERIES), alwaysForMerchant = false)
        repo.recategorize("sf:C:A:2", cat(DefaultCategories.GROCERIES), alwaysForMerchant = false)
        assertEquals(cat(DefaultCategories.GIFTS), get("3").categoryId)
        assertEquals(CategorySource.USER, get("3").categorySource)
    }

    @Test fun upgradeAddsNewCategoriesAndResortsHistoryOnce() = runBlocking {
        sync(tx("1", "PETSMART #0412", "-30.00"), tx("2", "HAMPTON INN OMAHA", "-120.00", posted = 1763710000))
        // Simulate an install from before Travel/Pets existed.
        listOf(DefaultCategories.TRAVEL, DefaultCategories.PETS).forEach { k ->
            val id = cat(k)
            env.db.openHelper.writableDatabase.execSQL("UPDATE transactions SET categoryId = (SELECT id FROM categories WHERE `key` = 'uncategorized'), categorySource = 'NONE' WHERE categoryId = $id")
            env.db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = $id")
        }
        assertNull(env.db.categories().getByKey(DefaultCategories.PETS))

        val moved = repo.upgradeCatalog(0)

        assertEquals(2, moved)
        assertEquals(cat(DefaultCategories.PETS), get("1").categoryId)
        assertEquals(cat(DefaultCategories.TRAVEL), get("2").categoryId)
        assertEquals(0, repo.upgradeCatalog(CategoryRepository.CATALOG_VERSION))
    }

    @Test fun letSproutDecideForgetsYourPickAndUndoRestoresIt() = runBlocking {
        sync(tx("1", "NETFLIX.COM", "-15.49"))
        repo.recategorize("sf:C:A:1", cat(DefaultCategories.GIFTS), alwaysForMerchant = false)
        val change = repo.resetToAuto(listOf("sf:C:A:1"))
        assertEquals(1, change.others)
        assertEquals(cat(DefaultCategories.SUBSCRIPTIONS), get("1").categoryId)
        assertEquals(CategorySource.BUILT_IN, get("1").categorySource)
        change.undo()
        assertEquals(cat(DefaultCategories.GIFTS), get("1").categoryId)
        assertEquals(CategorySource.USER, get("1").categorySource)
    }

    @Test fun bankWordingIsNeverTreatedAsOnePlace() = runBlocking {
        sync(
            tx("1", "18370 VISA DEBIT PURC Carriage Park 402-4418304 NE 10000971 018370", "-3.00"),
            tx("2", "45020 VISA DEBIT PURC SALVAGE WAREHOUS LINCOLN NE 00000000 045020", "-20.37", posted = 1763710000),
        )
        assertEquals("CARRIAGE PARK", get("1").merchantKey)
        // Simulate an install from before the fix: both had the key "VISA DEBIT" and an "always" rule for it.
        env.db.transactions().setMerchantKey("sf:C:A:1", "VISA DEBIT")
        env.db.transactions().setMerchantKey("sf:C:A:2", "VISA DEBIT")
        env.db.merchantRules().insert(com.emotionalleaves.sprout.data.db.MerchantRuleEntity(matchType = RuleType.EXACT, pattern = "VISA DEBIT",
            categoryId = cat(DefaultCategories.GIFTS), origin = com.emotionalleaves.sprout.data.db.RuleOrigin.USER_CORRECTION, hitCount = 1, createdAt = 1, updatedAt = 1))
        repo.reapplyRules()
        assertEquals(cat(DefaultCategories.GIFTS), get("2").categoryId)

        repo.upgradeCatalog(3)

        assertEquals("SALVAGE WAREHOUS LINCOLN", get("2").merchantKey)
        assertTrue(env.db.merchantRules().getAll().isEmpty())
        assertEquals(cat(DefaultCategories.SHOPPING), get("2").categoryId)
        // Picking one with "always" on no longer sweeps up the other.
        repo.recategorize("sf:C:A:1", cat(DefaultCategories.EATING_OUT), alwaysForMerchant = true)
        assertEquals(cat(DefaultCategories.SHOPPING), get("2").categoryId)
    }

    @Test fun guessesCategoryForTypedInPurchases() = runBlocking {
        sync(tx("1", "CASEYS #3021 LINCOLN NE", "-40.00"))
        assertEquals(cat(DefaultCategories.GROCERIES), repo.guessCategory("Hy-Vee", -2_500))
        assertEquals(cat(DefaultCategories.GAS), repo.guessCategory("caseys", -2_000))
        // No idea means no guess, never "Uncategorized".
        assertEquals(null, repo.guessCategory("zxqv blorp", -100))
        assertEquals(null, repo.guessCategory("  ", -100))
        // Your own corrections win for places the built-in list doesn't know.
        repo.addContainsRule("blorp", cat(DefaultCategories.GIFTS))
        assertEquals(cat(DefaultCategories.GIFTS), repo.guessCategory("zxqv blorp", -100))
    }
}
