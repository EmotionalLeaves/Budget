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

    @Test fun autoTagRulesTagOldAndNewTransactions() = runBlocking {
        sync(Triple("1", "DELTA AIR 0061|-300.00", at(11, 5)), Triple("2", "KROGER|-10.00", at(11, 6)))
        val repo = TagRepository(env.db, env.clock)
        val travel = repo.getOrCreate("Travel")!!
        assertEquals(null, repo.addRule("x", travel))
        assertEquals(1, repo.addRule("delta", travel))
        sync(Triple("1", "DELTA AIR 0061|-300.00", at(11, 5)), Triple("3", "DELTA AIR 0099|-150.00", at(11, 20)))
        repo.applyRulesToNew()
        val tagged = env.db.backup().transactionTags().map { it.transactionId }.toSet()
        val ids = env.db.backup().transactions().associate { it.description to it.id }
        assertEquals(setOf(ids.getValue("DELTA AIR 0061"), ids.getValue("DELTA AIR 0099")), tagged)
        assertEquals(1, env.db.tags().rules().size)
        repo.delete(travel)
        assertEquals(0, env.db.tags().rules().size) // rules go with their tag
    }

    @Test fun amountRulesSplitOneMerchantIntoTwoCategories() = runBlocking {
        sync(Triple("1", "VENMO PAYMENT LANDLORD|-1200.00", at(11, 1)), Triple("2", "VENMO PAYMENT SAM|-20.00", at(11, 2)))
        val cats = com.emotionalleaves.sprout.data.categories.CategoryRepository(env.db, env.clock)
        val rent = env.db.categories().getAll().first { it.key == DefaultCategories.RENT }.id
        assertEquals(1, cats.previewContains("venmo", 50_000, null))
        assertEquals(2, cats.previewContains("venmo", null, null))
        assertEquals(1, TagRepository(env.db, env.clock).previewRule("landlord"))
        cats.addContainsRule("venmo", rent, minAbsMinor = 50_000)
        val byDesc = env.db.backup().transactions().associate { it.description to it.categoryId }
        assertEquals(rent, byDesc["VENMO PAYMENT LANDLORD"])
        assertTrue(byDesc["VENMO PAYMENT SAM"] != rent)
        val row = env.db.merchantRules().getAll().single()
        assertEquals(50_000L, row.minAbsMinor)
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

    @Test fun manualCashTransactions() = runBlocking {
        val m = com.emotionalleaves.sprout.data.manual.ManualRepository(env.db, env.clock) { zone }
        env.repo().ensureSeeded()
        val cash = m.ensureCashAccount()
        assertEquals(cash, m.ensureCashAccount()) // made once
        val cat = env.db.categories().getByKey(DefaultCategories.EATING_OUT)!!.id
        val id = m.add(cash, -1_250, "Farmers market", cat, java.time.LocalDate.of(2026, 11, 23), "tomatoes")
        m.add(cash, 5_000, "", env.db.categories().getByKey(DefaultCategories.INCOME)!!.id, java.time.LocalDate.of(2026, 11, 22), null)
        assertEquals(3_750L, env.db.accounts().getAll().single { it.id == cash }.balanceMinor)
        val tx = env.db.transactions().getById(id)!!
        assertEquals("tomatoes", tx.note)
        assertTrue(tx.reviewedAt != null)
        assertTrue(m.delete(id))
        assertEquals(5_000L, env.db.accounts().getAll().single { it.id == cash }.balanceMinor)
        // Synced transactions can't be deleted here.
        sync(Triple("1", "KROGER|-10.00", at(11, 2)))
        val synced = env.db.backup().transactions().first { it.description == "KROGER" }.id
        assertTrue(!m.delete(synced))
    }

    @Test fun searchFindsCategoryRenamedMerchantAndTag() = runBlocking {
        sync(Triple("1", "HY-VEE #1234|-80.00", at(11, 5)), Triple("2", "DELTA AIR 0061|-300.00", at(11, 6)), Triple("3", "SQ *JOES|-5.00", at(11, 7)))
        val ids = env.db.backup().transactions().associate { it.description to it.id }
        val travel = TagRepository(env.db, env.clock).getOrCreate("Travel")!!
        env.db.tags().attach(com.emotionalleaves.sprout.data.db.TransactionTagEntity(ids.getValue("DELTA AIR 0061"), travel))
        val joeKey = env.db.backup().transactions().first { it.description == "SQ *JOES" }.merchantKey
        env.db.merchantNames().upsert(com.emotionalleaves.sprout.data.db.MerchantNameEntity(joeKey, "Corner coffee", 1))
        suspend fun find(q: String): Set<String> {
            val page = env.db.transactions().pagingRows(q, null, false)
                .load(androidx.paging.PagingSource.LoadParams.Refresh(null, 50, false)) as androidx.paging.PagingSource.LoadResult.Page
            return page.data.map { it.description }.toSet()
        }
        assertEquals(setOf("HY-VEE #1234"), find("Groceries"))
        assertEquals(setOf("DELTA AIR 0061"), find("#trav"))
        assertEquals(setOf("DELTA AIR 0061"), find("travel"))
        assertEquals(setOf("SQ *JOES"), find("corner"))
        // "Select all" uses the same filters as the list.
        assertEquals(setOf(ids.getValue("DELTA AIR 0061")), env.db.transactions().filteredIds("travel", null, false).toSet())
        assertEquals(3, env.db.transactions().filteredIds("", null, false).size)
        // A date range keeps only what happened inside it (the end is exclusive).
        val dated = env.db.transactions().filteredIds("", null, false, fromSec = at(11, 6), toSec = at(11, 7))
        assertEquals(setOf(ids.getValue("DELTA AIR 0061")), dated.toSet())
    }

    @Test fun reorderCategories() = runBlocking {
        env.repo().ensureSeeded()
        val cats = com.emotionalleaves.sprout.data.categories.CategoryRepository(env.db, env.clock)
        val before = env.db.categories().getAll().filter { !it.archived }.map { it.id }
        cats.move(before[1], -1)
        val after = env.db.categories().getAll().filter { !it.archived }.map { it.id }
        assertEquals(listOf(before[1], before[0]) + before.drop(2), after)
        cats.move(after[0], -1) // already first: nothing happens
        assertEquals(after, env.db.categories().getAll().filter { !it.archived }.map { it.id })
    }

    @Test fun challengeIgnoresSpendingBeforeYouStarted() = runBlocking {
        val startOfToday = java.time.LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
        sync(Triple("1", "PIZZA HUT|-20.00", startOfToday))
        val ch = com.emotionalleaves.sprout.data.challenge.ChallengeRepository(env.context, env.db) { zone }
        val eating = env.db.backup().transactions().first { it.description.startsWith("PIZZA") }.categoryId
        ch.add(com.emotionalleaves.sprout.domain.challenge.ChallengeType.NO_SPEND_CATEGORY, "Fresh start", 7, eating)
        val p = ch.progress(java.time.LocalDate.now(zone)).first { it.challenge.title == "Fresh start" }
        assertEquals(com.emotionalleaves.sprout.domain.challenge.ChallengeStatus.ON_TRACK, p.status)
        ch.remove(p.challenge.id)
        Unit
    }

    @Test fun accountHistoryAndMonthInOut() = runBlocking {
        sync(Triple("1", "PAYROLL|500.00", at(11, 3)), Triple("2", "RENT|-300.00", at(11, 4)), Triple("3", "GAS|-50.00", at(10, 10)))
        val id = env.db.accounts().getAll().single().id
        val h = InsightsRepository(env.db, env.clock) { zone }.account(id)!!
        assertEquals(50_000L, h.inMinor)
        assertEquals(30_000L, h.outMinor)
        assertEquals(100_000L, h.points.last().second) // today = the current balance
        // October's month end: before November's +500 and −300.
        assertEquals(100_000L - 50_000L + 30_000L, h.points.first { it.first.monthValue == 10 }.second)
    }

    @Test fun challengesAndHealth() = runBlocking {
        sync(Triple("1", "ACME PAYROLL|3000.00", at(10, 1)), Triple("2", "TARGET|-200.00", at(10, 15)), Triple("3", "PIZZA HUT|-20.00", at(11, 23)))
        val ch = com.emotionalleaves.sprout.data.challenge.ChallengeRepository(env.context, env.db) { zone }
        env.db.categories().getAll().forEach { }
        val eating = env.db.backup().transactions().first { it.description.startsWith("PIZZA") }.categoryId
        ch.add(com.emotionalleaves.sprout.domain.challenge.ChallengeType.NO_SPEND_CATEGORY, "No pizza", 7, eating)
        // The challenge starts on the real today, so check it relative to that (this test once broke at midnight).
        val today = java.time.LocalDate.now(zone)
        val p = ch.progress(today).first { it.challenge.title == "No pizza" }
        assertEquals(com.emotionalleaves.sprout.domain.challenge.ChallengeStatus.ON_TRACK, p.status)
        val missed = ch.progress(today).first { it.challenge.title == "No pizza" }
        assertEquals(1, missed.daysIn)
        ch.remove(p.challenge.id)
        assertTrue(ch.progress().none { it.challenge.title == "No pizza" })
        val h = InsightsRepository(env.db, env.clock) { zone }.health()
        assertTrue(h.score != null && h.parts.any { it.name == "Savings rate" })
    }

    @Test fun editManualAndExportTagsAndSplits() = runBlocking {
        val m = com.emotionalleaves.sprout.data.manual.ManualRepository(env.db, env.clock) { zone }
        env.repo().ensureSeeded()
        val cash = m.ensureCashAccount()
        val cat = env.db.categories().getByKey(DefaultCategories.SHOPPING)!!.id
        val id = m.add(cash, -4_000, "Market", cat, java.time.LocalDate.of(2026, 11, 20), null)
        assertTrue(m.update(id, -4_500, "Farmers market", java.time.LocalDate.of(2026, 11, 21)))
        val tx = env.db.transactions().getById(id)!!
        assertEquals(-4_500L, tx.amountMinor)
        assertEquals("Farmers market", tx.description)
        assertEquals(-4_500L, env.db.accounts().getAll().single { it.id == cash }.balanceMinor)
        val tags = TagRepository(env.db, env.clock)
        tags.attach(id, tags.getOrCreate("Weekend")!!)
        com.emotionalleaves.sprout.data.categories.CategoryRepository(env.db, env.clock)
            .setSplits(id, listOf(com.emotionalleaves.sprout.domain.split.SplitPart(env.db.categories().getByKey(DefaultCategories.GIFTS)!!.id, 1_000)))
        val rows = env.db.transactions().exportRows(0, Long.MAX_VALUE)
        val r = rows.single { it.description == "Farmers market" }
        assertEquals("Weekend", r.tags)
        assertEquals("Gifts & Donations 10.00", r.splits)
    }
}
