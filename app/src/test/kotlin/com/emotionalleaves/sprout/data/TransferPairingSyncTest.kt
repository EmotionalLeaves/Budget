package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferPairingSyncTest {
    private lateinit var env: TestEnv

    @Before fun setUp() {
        env = TestEnv()
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun tx(id: String, desc: String, amount: String, posted: Long) =
        """{"id":"$id","posted":$posted,"amount":"$amount","description":"$desc"}"""

    private fun account(id: String, txs: String) =
        """{"id":"$id","name":"Acct $id","conn_id":"C","currency":"USD","balance":"100.00","balance-date":1763800000,"transactions":[$txs]}"""

    @Test fun moneyBetweenYourAccountsBecomesATransferOnBothSides() = runBlocking {
        val body = """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
            "accounts":[
              ${account("CHK", listOf(tx("1", "WEB AUTHORIZED PMT 88213", "-250.00", 1763600000), tx("2", "HY-VEE LINCOLN", "-40.00", 1763600000)).joinToString(","))},
              ${account("SAV", tx("3", "DEPOSIT 88213", "250.00", 1763686400))}
            ]}"""
        env.server.enqueue(MockResponse().setBody(body))
        env.repo().sync(SyncTrigger.MANUAL)

        val transfers = env.db.categories().getByKey(DefaultCategories.TRANSFERS)!!.id
        val out = env.db.transactions().getById("sf:C:CHK:1")!!
        val inn = env.db.transactions().getById("sf:C:SAV:3")!!
        assertEquals(transfers, out.categoryId)
        assertEquals(transfers, inn.categoryId)
        assertEquals(CategorySource.PAIRED, inn.categorySource)
        // A real purchase is left alone.
        assertEquals(env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id, env.db.transactions().getById("sf:C:CHK:2")!!.categoryId)
        // Re-sorting doesn't undo the pairing.
        com.emotionalleaves.sprout.data.categories.CategoryRepository(env.db, env.clock).reapplyRules()
        assertEquals(CategorySource.PAIRED, env.db.transactions().getById("sf:C:CHK:1")!!.categorySource)
    }
}
