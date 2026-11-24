package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.db.ConnectionStatus
import com.emotionalleaves.sprout.data.db.SyncResult
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.secure.StoredSecret
import com.emotionalleaves.sprout.data.sync.SkipReason
import com.emotionalleaves.sprout.data.sync.SyncRunResult
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.sync.SyncPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {
    private lateinit var env: TestEnv

    @Before fun setUp() {
        env = TestEnv()
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun ok(name: String) = env.server.enqueue(MockResponse().setBody(env.fixture(name)))

    private fun txCount() = runBlocking { env.db.transactions().observeCount().first() }

    @Test fun seedsDefaultCategoriesOnce() = runBlocking {
        env.repo().ensureSeeded()
        env.repo().ensureSeeded()
        val cats = env.db.categories().getAll()
        assertEquals(DefaultCategories.all.size, cats.size)
        assertEquals(DefaultCategories.all.map { it.key }.toSet(), cats.mapNotNull { it.key }.toSet())
    }

    @Test fun syncStoresEverythingAndIsIdempotent() = runBlocking {
        ok("v2_basic.json"); ok("v2_basic.json")
        val first = env.repo().sync(SyncTrigger.MANUAL)
        assertEquals(SyncRunResult.Done(SyncResult.SUCCESS, 6, 0), first)
        assertEquals(6, txCount())
        assertEquals(2, env.db.accounts().getAll().size)
        assertEquals(ConnectionStatus.OK, env.db.connections().getAll().single().status)
        assertNotNull(env.settings.current().lastSuccessfulSyncAt)

        val second = env.repo().sync(SyncTrigger.PERIODIC)
        assertEquals(SyncRunResult.Done(SyncResult.SUCCESS, 0, 0), second)
        assertEquals(6, txCount())
    }

    @Test fun balancesUpdateOnEverySync() = runBlocking {
        ok("pending_then_posted_1.json"); ok("pending_then_posted_2.json")
        env.repo().sync(SyncTrigger.MANUAL)
        assertEquals(50000L, env.db.accounts().getAll().single().balanceMinor)
        env.repo().sync(SyncTrigger.MANUAL)
        assertEquals(41500L, env.db.accounts().getAll().single().balanceMinor)
        assertEquals(2, txCount())
    }

    @Test fun partialErrorsStoreAllDataAndFlagAccount() = runBlocking {
        ok("v2_partial_errors.json")
        val r = env.repo().sync(SyncTrigger.MANUAL) as SyncRunResult.Done
        assertEquals(SyncResult.PARTIAL, r.result)
        assertEquals(2, txCount())
        val sav = env.db.accounts().getAll().single { it.remoteId == "ACT-SAV" }
        assertEquals("act.missingdata", sav.lastErrorCode)
        val log = env.db.syncLog().observeRecent(1).first().single()
        assertTrue(log.errorCodes.contains("act.missingdata"))
    }

    @Test fun conAuthMarksOnlyThatConnection() = runBlocking {
        ok("v2_con_auth.json")
        env.repo().sync(SyncTrigger.MANUAL)
        val byId = env.db.connections().getAll().associateBy { it.connId }
        assertEquals(ConnectionStatus.NEEDS_ATTENTION, byId.getValue("CON-A").status)
        assertEquals(ConnectionStatus.OK, byId.getValue("CON-B").status)
        assertEquals(1, txCount())
    }

    @Test fun accessRevokedKeepsDataAndStopsSyncing() = runBlocking {
        ok("v2_basic.json")
        env.repo().sync(SyncTrigger.MANUAL)
        env.server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(SyncRunResult.Skipped(SkipReason.REVOKED), env.repo().sync(SyncTrigger.PERIODIC))
        assertEquals(6, txCount())
        assertTrue(env.db.connections().getAll().all { it.status == ConnectionStatus.REVOKED })
        assertTrue(env.settings.current().accessRevoked)
        val before = env.server.requestCount
        assertEquals(SyncRunResult.Skipped(SkipReason.REVOKED), env.repo().sync(SyncTrigger.PERIODIC))
        assertEquals(before, env.server.requestCount)
    }

    @Test fun paymentRequiredSetsBanner() = runBlocking {
        env.server.enqueue(MockResponse().setResponseCode(402))
        assertEquals(SyncRunResult.Done(SyncResult.FAILED, 0, 0), env.repo().sync(SyncTrigger.PERIODIC))
        assertTrue(env.settings.current().paymentRequired)
    }

    @Test fun serverErrorAsksForRetry() = runBlocking {
        env.server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "60"))
        assertEquals(SyncRunResult.RetryLater(60), env.repo().sync(SyncTrigger.PERIODIC))
    }

    @Test fun malformedKeepsExistingData() = runBlocking {
        ok("v2_basic.json"); ok("malformed.json")
        env.repo().sync(SyncTrigger.MANUAL)
        assertEquals(SyncRunResult.Done(SyncResult.FAILED, 0, 0), env.repo().sync(SyncTrigger.MANUAL))
        assertEquals(6, txCount())
    }

    @Test fun v1FallbackWorksEndToEnd() = runBlocking {
        env.server.enqueue(MockResponse().setResponseCode(400))
        ok("v1_legacy_org_and_errors.json")
        val r = env.repo().sync(SyncTrigger.MANUAL) as SyncRunResult.Done
        assertEquals(SyncResult.PARTIAL, r.result)
        assertEquals(2, txCount())
        assertEquals(setOf("oldtownbank.example.test", "no domain credit union"), env.db.connections().getAll().map { it.connId }.toSet())
    }

    @Test fun customCurrencyIsExcludedFromTotals() = runBlocking {
        ok("custom_currency.json")
        env.repo().sync(SyncTrigger.MANUAL)
        assertFalse(env.db.accounts().getAll().single().includeInTotals)
    }

    @Test fun dailyRequestBudgetIsRespected() = runBlocking {
        repeat(SyncPolicy.DAILY_REQUEST_BUDGET) { ok("empty_accounts.json") }
        var made = 0
        repeat(SyncPolicy.DAILY_REQUEST_BUDGET) {
            if (env.repo().sync(SyncTrigger.MANUAL) is SyncRunResult.Done) made++
        }
        assertTrue(made < SyncPolicy.DAILY_REQUEST_BUDGET)
        assertEquals(SyncRunResult.Skipped(SkipReason.DAILY_LIMIT), env.repo().sync(SyncTrigger.MANUAL))
        assertTrue(env.server.requestCount <= SyncPolicy.DAILY_REQUEST_BUDGET)
    }

    @Test fun unreadableSecretNeedsReconnectAndNeverCrashes() = runBlocking {
        env.cipher.failDecrypt = true
        assertEquals(StoredSecret.Unreadable, env.secure.readAccessUrl())
        assertEquals(SyncRunResult.Skipped(SkipReason.NEEDS_RECONNECT), env.repo().sync(SyncTrigger.PERIODIC))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun notConnectedSkips() = runBlocking {
        env.secure.clear()
        assertTrue(env.cipher.deleted)
        assertEquals(SyncRunResult.Skipped(SkipReason.NOT_CONNECTED), env.repo().sync(SyncTrigger.PERIODIC))
        assertNull((env.secure.readAccessUrl() as? StoredSecret.Present))
    }

    @Test fun syncLogNeverContainsCredentials() = runBlocking {
        ok("v2_basic.json")
        env.server.enqueue(MockResponse().setResponseCode(500))
        env.repo().sync(SyncTrigger.MANUAL)
        env.repo().sync(SyncTrigger.MANUAL)
        val logs = env.db.syncLog().observeRecent(50).first()
        assertTrue(logs.isNotEmpty())
        logs.forEach { assertFalse(it.message, it.message.contains("demo:demo") || it.message.contains("https://")) }
    }
}
