package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class BudgetRepositoryTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private lateinit var repo: BudgetRepository
    private val nov = YearMonth.of(2026, 11)

    private fun at(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toEpochSecond()

    @Before fun setUp() {
        // "Now" is Nov 24, 2026, noon in Chicago.
        env = TestEnv(nowSeconds = at(11, 24))
        repo = BudgetRepository(env.db, env.clock, { zone })
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun syncTx(vararg t: Triple<String, String, Long>) = runBlocking {
        val txs = t.joinToString(",") { (id, desc, posted) -> """{"id":"$id","posted":$posted,"amount":"${desc.substringAfter('|')}","description":"${desc.substringBefore('|')}"}""" }
        env.server.enqueue(MockResponse().setBody(
            """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
               "accounts":[{"id":"A","name":"Checking","conn_id":"C","currency":"USD","balance":"1.00","balance-date":1,"transactions":[$txs]}]}""",
        ))
        env.repo().sync(SyncTrigger.MANUAL)
    }

    private fun cat(key: String) = runBlocking { env.db.categories().getByKey(key)!!.id }

    @Test fun monthUsesSyncedSpendingAndBudgets() = runBlocking {
        syncTx(
            Triple("1", "KROGER #1|-120.00", at(11, 5)),
            Triple("2", "KROGER #1|20.00", at(11, 6)),
            Triple("3", "ONLINE TRANSFER TO SAVINGS|-500.00", at(11, 7)),
            Triple("4", "KROGER #1|-75.00", at(10, 30)),
        )
        repo.setBudget(cat(DefaultCategories.GROCERIES), 30_000, thisMonthOnly = false, month = nov)
        val m = repo.observeMonth(nov).first()
        val g = m.budgeted.single()
        assertEquals(10_000L, g.spentMinor)
        assertEquals(20_000L, g.remainingMinor)
        assertEquals(10_000L, m.totalSpentMinor) // the transfer never counts
        assertEquals(20_000L, m.safeToSpend!!.amountMinor)
        assertEquals(7, m.safeToSpend!!.daysRemaining)
    }

    @Test fun thisMonthOnlyOverridesAndEveryMonthReplaces() = runBlocking {
        env.repo().ensureSeeded()
        val g = cat(DefaultCategories.GROCERIES)
        repo.setBudget(g, 30_000, thisMonthOnly = false, month = nov)
        repo.setBudget(g, 50_000, thisMonthOnly = true, month = nov)
        assertEquals(50_000L, repo.observeMonth(nov).first().budgeted.single().budgetMinor)
        assertEquals(30_000L, repo.observeMonth(nov.plusMonths(1)).first().budgeted.single().budgetMinor)
        repo.setBudget(g, 40_000, thisMonthOnly = false, month = nov)
        assertEquals(40_000L, repo.observeMonth(nov).first().budgeted.single().budgetMinor)
    }

    @Test fun removeBudget() = runBlocking {
        env.repo().ensureSeeded()
        val g = cat(DefaultCategories.GROCERIES)
        repo.setBudget(g, 30_000, thisMonthOnly = false, month = nov)
        repo.removeBudget(g, thisMonthOnly = false, month = nov)
        assertNull(repo.observeMonth(nov).first().safeToSpend)
    }

    @Test fun quickSetupSuggestsFromLastThreeFullMonths() = runBlocking {
        syncTx(
            Triple("a", "STARBUCKS|-31.00", at(8, 10)),
            Triple("b", "STARBUCKS|-29.50", at(9, 10)),
            Triple("c", "STARBUCKS|-35.25", at(10, 10)),
            Triple("d", "STARBUCKS|-500.00", at(11, 10)), // current month: ignored
        )
        val s = repo.suggestions().single { it.categoryId == cat(DefaultCategories.EATING_OUT) }
        assertEquals(4_000L, s.suggestedMinor) // avg $31.92 -> $40
        repo.applySuggestions(mapOf(s.categoryId to s.suggestedMinor))
        assertEquals(4_000L, repo.observeMonth(nov).first().budgeted.single().budgetMinor)
    }

    @Test fun historyHasSixMonthsOldestFirst() = runBlocking {
        syncTx(Triple("a", "STARBUCKS|-10.00", at(9, 3)), Triple("b", "STARBUCKS|-5.00", at(11, 3)))
        val h = repo.history(cat(DefaultCategories.EATING_OUT))
        assertEquals(6, h.size)
        assertEquals(YearMonth.of(2026, 6), h.first().first)
        assertEquals(nov, h.last().first)
        assertEquals(1_000L, h.first { it.first == YearMonth.of(2026, 9) }.second)
        assertEquals(500L, h.last().second)
    }

    @Test fun rolloverCarriesLeftoverIntoNextMonth() = runBlocking {
        syncTx(
            Triple("1", "KROGER #1|-120.00", at(10, 5)),
            Triple("2", "KROGER #1|-50.00", at(11, 3)),
        )
        val g = cat(DefaultCategories.GROCERIES)
        val oct = nov.minusMonths(1)
        repo.setBudget(g, 30_000, thisMonthOnly = false, month = oct)
        repo.setRollover(g, true, oct)
        val line = repo.observeMonth(nov).first().budgeted.single()
        assertEquals(18_000L, line.carryMinor)
        assertEquals(48_000L, line.budgetMinor)
        assertEquals(30_000L, line.baseBudgetMinor)
        assertEquals(43_000L, line.remainingMinor)
        // October itself gets no carry.
        assertEquals(30_000L, repo.observeMonth(oct).first().budgeted.single().budgetMinor)
        // A one-off October amount changes what carries.
        repo.setBudget(g, 10_000, thisMonthOnly = true, month = oct)
        assertEquals(-2_000L, repo.observeMonth(nov).first().budgeted.single().carryMinor)
        repo.setRollover(g, false, oct)
        val off = repo.observeMonth(nov).first().budgeted.single()
        assertEquals(30_000L, off.budgetMinor)
        assertEquals(false, off.rolloverOn)
    }

    @Test fun removingTheBudgetTurnsRolloverOff() = runBlocking {
        env.repo().ensureSeeded()
        val g = cat(DefaultCategories.GROCERIES)
        repo.setBudget(g, 30_000, thisMonthOnly = false, month = nov)
        repo.setRollover(g, true, nov)
        repo.removeBudget(g, thisMonthOnly = false, month = nov)
        assertEquals(0, env.db.backup().budgetRollovers().size)
    }
}
