package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.alerts.AlertRepository
import com.emotionalleaves.sprout.data.alerts.Notifier
import com.emotionalleaves.sprout.data.alerts.SproutAlert
import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.alerts.UnusualReason
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AlertRepositoryTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private lateinit var alerts: AlertRepository
    private lateinit var budgets: BudgetRepository
    private val shown = mutableListOf<SproutAlert>()
    private val notifier = object : Notifier {
        override fun show(alert: SproutAlert): Boolean { shown += alert; return true }
    }
    private val nov = YearMonth.of(2026, 11)

    private fun at(d: Int) = LocalDateTime.of(2026, 11, d, 12, 0).atZone(zone).toEpochSecond()

    @Before fun setUp() {
        env = TestEnv(nowSeconds = at(24))
        budgets = BudgetRepository(env.db, env.clock, { zone })
        alerts = AlertRepository(env.db, env.settings, budgets, notifier, env.clock)
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun tx(id: String, desc: String, amount: String, day: Int) = """{"id":"$id","posted":${at(day)},"amount":"$amount","description":"$desc"}"""

    private fun body(vararg txs: String, errlist: String = "") =
        """{"errlist":[$errlist],"connections":[{"conn_id":"C","name":"Prairie Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
           "accounts":[{"id":"A","name":"Checking","conn_id":"C","currency":"USD","balance":"1.00","balance-date":1,"transactions":[${txs.joinToString(",")}]}]}"""

    private fun sync(vararg txs: String, errlist: String = "") = runBlocking {
        env.server.enqueue(MockResponse().setBody(body(*txs, errlist = errlist)))
        env.repo(alerts).sync(SyncTrigger.PERIODIC)
    }

    private fun cat(key: String) = runBlocking { env.db.categories().getByKey(key)!!.id }

    @Test fun budgetThresholdsFireOnceEach() = runBlocking {
        sync(tx("0", "KROGER #1", "-10.00", 2))
        budgets.setBudget(cat(DefaultCategories.GROCERIES), 10_000, false, nov)
        shown.clear()
        sync(tx("0", "KROGER #1", "-10.00", 2), tx("1", "KROGER #1", "-75.00", 10))
        assertEquals(listOf(80), shown.filterIsInstance<SproutAlert.Budget>().map { it.threshold })
        sync(tx("0", "KROGER #1", "-10.00", 2), tx("1", "KROGER #1", "-75.00", 10), tx("2", "KROGER #1", "-5.00", 11))
        assertEquals(1, shown.filterIsInstance<SproutAlert.Budget>().size)
        sync(tx("0", "KROGER #1", "-10.00", 2), tx("1", "KROGER #1", "-75.00", 10), tx("2", "KROGER #1", "-5.00", 11), tx("3", "KROGER #1", "-20.00", 12))
        assertEquals(listOf(80, 100), shown.filterIsInstance<SproutAlert.Budget>().map { it.threshold })
    }

    @Test fun crossingBothAtOnceFiresOnly100() = runBlocking {
        sync(tx("0", "KROGER #1", "-1.00", 2))
        budgets.setBudget(cat(DefaultCategories.GROCERIES), 10_000, false, nov)
        shown.clear()
        sync(tx("0", "KROGER #1", "-1.00", 2), tx("1", "KROGER #1", "-150.00", 10))
        assertEquals(listOf(100), shown.filterIsInstance<SproutAlert.Budget>().map { it.threshold })
        alerts.evaluateBudgets()
        assertEquals(1, shown.filterIsInstance<SproutAlert.Budget>().size)
    }

    @Test fun firstSyncNeverAlertsOnTransactions() {
        sync(tx("1", "BEST BUY #12", "-900.00", 5))
        assertTrue(shown.filterIsInstance<SproutAlert.Transaction>().isEmpty())
    }

    @Test fun largeChargeOnLaterSyncAlertsOnce() = runBlocking {
        sync(tx("1", "STARBUCKS #1", "-5.00", 5))
        sync(tx("1", "STARBUCKS #1", "-5.00", 5), tx("2", "BEST BUY #12", "-300.00", 20))
        val t = shown.filterIsInstance<SproutAlert.Transaction>().single()
        assertEquals(UnusualReason.LARGE, t.reason)
        assertEquals("sf:C:A:2", t.transactionId)
        assertNotNull(env.db.transactions().getById("sf:C:A:2")!!.alertedAt)
        sync(tx("1", "STARBUCKS #1", "-5.00", 5), tx("2", "BEST BUY #12", "-300.00", 20))
        assertEquals(1, shown.filterIsInstance<SproutAlert.Transaction>().size)
    }

    @Test fun unusualForMerchantOnLaterSync() {
        sync(tx("1", "CASEYS #1", "-20.00", 3), tx("2", "CASEYS #1", "-22.00", 6), tx("3", "CASEYS #1", "-18.00", 9))
        sync(tx("1", "CASEYS #1", "-20.00", 3), tx("2", "CASEYS #1", "-22.00", 6), tx("3", "CASEYS #1", "-18.00", 9), tx("4", "CASEYS #1", "-60.00", 21))
        assertEquals(UnusualReason.UNUSUAL_FOR_MERCHANT, shown.filterIsInstance<SproutAlert.Transaction>().single().reason)
    }

    @Test fun togglesOffMeansNoAlertsButCrossingsAreRecorded() = runBlocking {
        env.settings.setNotifyBudget(false)
        env.settings.setNotifyUnusual(false)
        sync(tx("0", "KROGER #1", "-1.00", 2))
        budgets.setBudget(cat(DefaultCategories.GROCERIES), 1_000, false, nov)
        sync(tx("0", "KROGER #1", "-1.00", 2), tx("1", "BEST BUY #2", "-400.00", 20), tx("2", "KROGER #1", "-50.00", 21))
        assertTrue(shown.isEmpty())
        env.settings.setNotifyBudget(true)
        alerts.evaluateBudgets()
        assertTrue("old crossing must not replay", shown.isEmpty())
    }

    @Test fun connectionNeedingAttentionNotifiesOnce() {
        val err = """{"code":"con.auth","msg":"Sign in again in Bridge","conn_id":"C"}"""
        sync(tx("1", "X", "-1.00", 2))
        sync(tx("1", "X", "-1.00", 2), errlist = err)
        sync(tx("1", "X", "-1.00", 2), errlist = err)
        val problems = shown.filterIsInstance<SproutAlert.SyncTrouble>()
        assertEquals(1, problems.size)
        assertTrue(problems.single().title.contains("Prairie Bank"))
    }
}
