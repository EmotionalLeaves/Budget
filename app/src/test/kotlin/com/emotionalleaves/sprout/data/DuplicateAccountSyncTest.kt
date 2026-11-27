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
class DuplicateAccountSyncTest {
    private lateinit var env: TestEnv

    @Before fun setUp() {
        env = TestEnv()
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun body(conn: String) = """{"errlist":[],"connections":[{"conn_id":"$conn","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
        "accounts":[{"id":"A","name":"Joint Checking (1273)","conn_id":"$conn","currency":"USD","balance":"100.00","balance-date":1763800000,"transactions":[
          {"id":"1","posted":1763600000,"amount":"-3.00","description":"18370 VISA DEBIT PURC Carriage Park 402-4418304 NE"},
          {"id":"2","posted":1763610000,"amount":"-20.37","description":"45020 VISA DEBIT PURC SALVAGE WAREHOUS LINCOLN NE"},
          {"id":"3","posted":1763620000,"amount":"-6.01","description":"48206 VISA DEBIT PURC McDonalds 6062 LINCOLN NE"}]}]}"""

    @Test fun theSameBankConnectedTwiceIsShownOnce() = runBlocking {
        env.server.enqueue(MockResponse().setBody(body("C")))
        env.repo().sync(SyncTrigger.MANUAL)
        val gifts = env.db.categories().getByKey(DefaultCategories.GIFTS)!!.id
        env.db.transactions().setCategory("sf:C:A:1", gifts, CategorySource.USER, 1)
        // Reconnected: the same account arrives under a new connection id.
        env.server.enqueue(MockResponse().setBody(body("D")))
        env.repo().sync(SyncTrigger.MANUAL)

        val accounts = env.db.accounts().getAll()
        assertEquals(2, accounts.size)
        assertEquals(1, accounts.count { it.hidden })
        val kept = accounts.single { !it.hidden }
        // Your pick carried over to the copy that's kept.
        assertEquals(gifts, env.db.transactions().getById("${kept.id}:1")!!.categoryId)
        // Shown again by you: left alone afterwards.
        val hidden = accounts.single { it.hidden }
        env.db.accounts().setHidden(hidden.id, false)
        assertEquals(0, env.repo().hideDuplicateAccounts())
    }

    @Test fun ifTheKeptLoginStopsSyncingTheLiveCopyTakesOver() = runBlocking {
        env.server.enqueue(MockResponse().setBody(body("C")))
        env.repo().sync(SyncTrigger.MANUAL)
        env.server.enqueue(MockResponse().setBody(body("D")))
        env.repo().sync(SyncTrigger.MANUAL)
        val kept = env.db.accounts().getAll().single { !it.hidden }
        val hidden = env.db.accounts().getAll().single { it.hidden }
        val gifts = env.db.categories().getByKey(DefaultCategories.GIFTS)!!.id
        env.db.transactions().setCategory("${kept.id}:2", gifts, CategorySource.USER, 1)
        // The kept login stopped three days ago; the other one is still syncing.
        val now = env.clock.instant().epochSecond
        env.db.openHelper.writableDatabase.execSQL("UPDATE accounts SET lastSyncedAt = ${now - 3 * 86_400} WHERE id = '${kept.id}'")
        env.db.openHelper.writableDatabase.execSQL("UPDATE accounts SET lastSyncedAt = $now WHERE id = '${hidden.id}'")

        env.repo().hideDuplicateAccounts()

        val after = env.db.accounts().getAll().associateBy { it.id }
        assertEquals(true, after.getValue(kept.id).hidden)
        assertEquals(false, after.getValue(hidden.id).hidden)
        assertEquals(gifts, env.db.transactions().getById("${hidden.id}:2")!!.categoryId)
    }
}
