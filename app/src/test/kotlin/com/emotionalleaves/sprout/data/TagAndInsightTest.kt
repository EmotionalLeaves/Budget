package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.insights.InsightsRepository
import com.emotionalleaves.sprout.data.tags.TagRepository
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
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class TagAndInsightTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private fun at(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toEpochSecond()

    @Before fun setUp() {
        env = TestEnv(nowSeconds = at(11, 24))
        runBlocking { env.secure.saveAccessUrl(env.accessUrl) }
    }

    @After fun tearDown() = env.close()

    private fun sync(vararg t: Triple<String, String, Long>) = runBlocking {
        val txs = t.joinToString(",") { (id, desc, posted) -> """{"id":"$id","posted":$posted,"amount":"${desc.substringAfter('|')}","description":"${desc.substringBefore('|')}"}""" }
        env.server.enqueue(MockResponse().setBody(
            """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
               "accounts":[{"id":"A","name":"Checking","conn_id":"C","currency":"USD","balance":"1000.00","balance-date":1,"transactions":[$txs]}]}""",
        ))
        env.repo().sync(SyncTrigger.MANUAL)
    }

    @Test fun tagsAttachDedupeRenameAndFilter() = runBlocking {
        sync(Triple("1", "HILTON CHICAGO|-200.00", at(11, 5)), Triple("2", "PIZZA PLACE|-40.00", at(11, 6)), Triple("3", "KROGER|-10.00", at(11, 7)))
        val repo = TagRepository(env.db, env.clock)
        val trip = repo.getOrCreate("#Chicago trip")!!
        assertEquals(trip, repo.getOrCreate("chicago TRIP"))
        assertEquals(null, repo.getOrCreate("   "))
        val ids = env.db.backup().transactions().associate { it.description to it.id }
        repo.attach(ids.getValue("HILTON CHICAGO"), trip)
        repo.attach(ids.getValue("PIZZA PLACE"), trip)
        repo.attach(ids.getValue("PIZZA PLACE"), trip) // twice is fine
        val summary = env.db.tags().observeSummaries().first().single()
        assertEquals("Chicago trip", summary.name)
        assertEquals(2, summary.count)
        assertEquals(-24_000L, summary.total)
        val other = repo.getOrCreate("Work")!!
        assertFalse(repo.rename(other, "chicago trip"))
        assertTrue(repo.rename(trip, "Chicago 2026"))
        repo.delete(trip)
        assertEquals(3, env.db.backup().transactions().size) // transactions stay
        assertEquals(0, env.db.backup().transactionTags().size)
    }

    @Test fun insightsNetWorthYearAndSubscriptions() = runBlocking {
        sync(
            Triple("1", "ACME PAYROLL|2000.00", at(11, 1)),
            Triple("2", "TARGET|-120.00", at(11, 3)),
            Triple("3", "TARGET|-80.00", at(10, 3)),
            Triple("4", "KROGER|-50.00", at(9, 20)),
        )
        val repo = InsightsRepository(env.db, env.clock) { zone }
        val (months, rows) = repo.yearGrid(12)
        assertEquals(12, months.size)
        val total = rows.sumOf { it.total }
        assertEquals(25_000L, total)
        val series = repo.netWorth(12)
        assertEquals(100_000L, series.last().second)
        // End of October: today's 1,000 minus November's +2,000 and −120 → −880.
        assertEquals(-88_000L, series.first { it.first.monthValue == 10 }.second)
        assertTrue(series.none { it.first.monthValue < 9 && it.first.year == 2026 })
        val insights = repo.insights()
        assertTrue(insights.any { it.key == "merchant" && it.title.contains("Target") })
        assertEquals(0, repo.subscriptions().count)
    }

    @Test fun merchantHistory() = runBlocking {
        sync(Triple("1", "TARGET 1234|-40.00", at(11, 3)), Triple("2", "TARGET 1234|-60.00", at(10, 3)), Triple("3", "KROGER|-5.00", at(11, 4)))
        val key = env.db.backup().transactions().first { it.description.startsWith("TARGET") }.merchantKey
        val rows = env.db.insights().observeMerchant(key).first()
        assertEquals(2, rows.size)
        assertEquals(-4_000L, rows.first().amountMinor) // newest first
    }

    @Test fun recapCalendarAndTemplate() = runBlocking {
        sync(
            Triple("1", "ACME PAYROLL|3000.00", at(10, 1)),
            Triple("2", "RENT CO|-1000.00", at(10, 2)),
            Triple("3", "TARGET|-200.00", at(10, 15)),
            Triple("4", "TARGET|-50.00", at(9, 15)),
            Triple("5", "KROGER|-20.00", at(11, 2)),
        )
        val repo = InsightsRepository(env.db, env.clock) { zone }
        val r = repo.recap(java.time.YearMonth.of(2026, 10))
        assertEquals(120_000L, r.spentMinor)
        assertEquals(300_000L, r.incomeMinor)
        assertEquals(5_000L, r.previousSpentMinor)
        assertEquals(29, r.noSpendDays)
        assertEquals(100_000L, r.biggest?.second)
        val cal = repo.calendar()
        assertEquals(2_000L, cal.weeks.flatten().first { it?.date?.dayOfMonth == 2 }!!.spentMinor)
        val (income, plan) = com.emotionalleaves.sprout.data.budget.BudgetRepository(env.db, env.clock, { zone }).fiftyThirtyTwenty()
        assertEquals(300_000L, income) // averaged over the months that had income
        assertTrue(plan.values.sum() <= 240_000L)
    }

    @Test fun inboxOnlyHoldsNewActivityAfterTheFirstSync() = runBlocking {
        sync(Triple("1", "KROGER|-10.00", at(11, 2)))
        assertEquals(0, env.db.transactions().observeInboxCount().first())
        sync(Triple("1", "KROGER|-10.00", at(11, 2)), Triple("2", "TARGET|-20.00", at(11, 20)))
        val inbox = env.db.transactions().observeInbox().first()
        assertEquals(listOf("TARGET"), inbox.map { it.description })
        env.db.transactions().markReviewed(inbox.map { it.id }, 5)
        assertEquals(0, env.db.transactions().observeInboxCount().first())
        // A re-sync never puts it back.
        sync(Triple("1", "KROGER|-10.00", at(11, 2)), Triple("2", "TARGET|-20.00", at(11, 20)))
        assertEquals(0, env.db.transactions().observeInboxCount().first())
    }

    @Test fun plannedExpenseComesOutOfSafeToSpend() = runBlocking {
        sync(Triple("1", "KROGER|-10.00", at(11, 2)))
        val goals = com.emotionalleaves.sprout.data.goals.GoalRepository(env.db, env.clock) { zone }
        val budgets = com.emotionalleaves.sprout.data.budget.BudgetRepository(env.db, env.clock, { zone }, plannedSetAside = { goals.observePlannedSetAside() })
        val g = env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id
        val nov = java.time.YearMonth.of(2026, 11)
        budgets.setBudget(g, 30_000, false, nov)
        assertEquals(29_000L, budgets.observeMonth(nov).first().safeToSpend!!.amountMinor)
        goals.save(null, "Car registration", 24_000, java.time.LocalDate.of(2027, 6, 1), null, 0, com.emotionalleaves.sprout.data.goals.GoalRepository.KIND_PLANNED)
        val safe = budgets.observeMonth(nov).first().safeToSpend!!
        assertEquals(3_000L, safe.plannedMinor)
        assertEquals(26_000L, safe.amountMinor)
    }

    @Test fun flexModeReportsAndYearReview() = runBlocking {
        sync(
            Triple("1", "ACME PAYROLL|3000.00", at(11, 1)), Triple("2", "RENT CO|-1000.00", at(11, 2)),
            Triple("3", "TARGET|-200.00", at(11, 15)), Triple("4", "TARGET|-50.00", at(10, 15)),
        )
        env.settings.setFlexBudget(50_000)
        val flex = com.emotionalleaves.sprout.data.budget.FlexRepository(
            env.db, env.settings, com.emotionalleaves.sprout.data.budget.BudgetRepository(env.db, env.clock, { zone }),
            { kotlinx.coroutines.flow.flowOf(emptyList()) }, { kotlinx.coroutines.flow.flowOf(0L) }, { zone },
        )
        val rent = env.db.backup().transactions().first { it.description == "RENT CO" }.categoryId
        env.settings.setFixedCategories(setOf(rent))
        val m = flex.observe(java.time.YearMonth.of(2026, 11)).first().month
        assertEquals(100_000L, m.fixedSpentMinor)
        assertEquals(20_000L, m.flexSpentMinor)
        assertEquals(30_000L, m.safeMinor)
        val repo = InsightsRepository(env.db, env.clock) { zone }
        val r = repo.reports(3)
        assertEquals(300_000L, r.incomeByMonth.sum())
        assertEquals(125_000L, r.spendingByMonth.sum())
        val y = repo.yearReview(2026)
        assertEquals(125_000L, y.spentMinor)
        assertEquals(java.time.YearMonth.of(2026, 11), y.priciestMonth?.first)
        assertTrue(repo.watch(setOf("c:$rent", "m:TARGET")).isNotEmpty())
    }
}
