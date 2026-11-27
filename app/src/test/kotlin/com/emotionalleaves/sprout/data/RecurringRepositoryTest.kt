package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.RecurringStatus
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.recurring.RecurringRepository
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.recurring.Cadence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RecurringRepositoryTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private lateinit var recurring: RecurringRepository
    private lateinit var budgets: BudgetRepository
    private val nov = YearMonth.of(2026, 11)

    private fun at(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toEpochSecond()

    @Before fun setUp() {
        env = TestEnv(nowSeconds = at(11, 10))
        recurring = RecurringRepository(env.db, env.clock, { zone })
        budgets = BudgetRepository(env.db, env.clock, { zone }, recurring::expectedCharges)
        runBlocking {
            env.secure.saveAccessUrl(env.accessUrl)
            val txs = (6..10).joinToString(",") { m -> """{"id":"n$m","posted":${at(m, 15)},"amount":"-15.49","description":"NETFLIX.COM"}""" } +
                "," + """{"id":"k1","posted":${at(11, 2)},"amount":"-40.00","description":"KROGER #1"}"""
            env.server.enqueue(MockResponse().setBody(
                """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
                   "accounts":[{"id":"A","name":"Checking","conn_id":"C","currency":"USD","balance":"1.00","balance-date":1,"transactions":[$txs]}]}""",
            ))
            env.repo().sync(SyncTrigger.MANUAL)
            recurring.detect()
        }
    }

    @After fun tearDown() = env.close()

    private fun cat(key: String) = runBlocking { env.db.categories().getByKey(key)!!.id }

    @Test fun detectsNetflixAndLinksTransactions() = runBlocking {
        val r = recurring.observeActive().first().single()
        assertEquals("NETFLIX", r.merchantKey)
        assertEquals(Cadence.MONTHLY, r.cadence)
        assertEquals(RecurringStatus.DETECTED, r.status)
        assertEquals(r.id, env.db.transactions().getById("sf:C:A:n10")!!.recurringChargeId)
        val up = recurring.observeUpcoming(30).first().single()
        assertEquals(LocalDate.of(2026, 11, 15), java.time.Instant.ofEpochSecond(up.dueAt).atZone(zone).toLocalDate())
    }

    @Test fun billInUnbudgetedCategoryReducesSafeToSpend() = runBlocking {
        budgets.setBudget(cat(DefaultCategories.GROCERIES), 30_000, false, nov)
        val s = budgets.observeMonth(nov).first().safeToSpend!!
        assertEquals(26_000L, s.budgetRemainingMinor)
        assertEquals(26_000L - 1_549L, s.amountMinor)
        assertEquals("Netflix", s.upcomingUnbudgeted.single().name)
    }

    @Test fun dismissedNeverComesBack() = runBlocking {
        val id = recurring.observeActive().first().single().id
        recurring.dismiss(id)
        recurring.detect()
        assertTrue(recurring.observeActive().first().isEmpty())
        assertEquals(RecurringStatus.DISMISSED, env.db.recurring().getById(id)!!.status)
    }

    @Test fun dismissCanBeUndone() = runBlocking {
        val id = recurring.observeActive().first().single().id
        val linked = env.db.recurring().linkedIds(id)
        assertTrue(linked.isNotEmpty())
        val undo = recurring.dismiss(id)
        assertTrue(env.db.recurring().linkedIds(id).isEmpty())
        undo()
        assertEquals("Netflix", recurring.observeActive().first().single().displayName)
        assertEquals(linked.toSet(), env.db.recurring().linkedIds(id).toSet())
    }

    @Test fun userEditsSurviveRedetection() = runBlocking {
        val id = recurring.observeActive().first().single().id
        recurring.edit(id, 2_000, Cadence.MONTHLY, LocalDate.of(2026, 11, 20), cat(DefaultCategories.ENTERTAINMENT))
        recurring.detect()
        val r = env.db.recurring().getById(id)!!
        assertEquals(2_000L, r.typicalAmountMinor)
        assertEquals(cat(DefaultCategories.ENTERTAINMENT), r.categoryId)
        assertEquals(RecurringStatus.CONFIRMED, r.status)
    }

    @Test fun manualBillAppearsInUpcoming() = runBlocking {
        recurring.addManual("Rent", 95_000, Cadence.MONTHLY, LocalDate.of(2026, 12, 1), cat(DefaultCategories.RENT))
        val names = recurring.observeUpcoming(30).first().map { it.charge.displayName }
        assertTrue(names.contains("Rent"))
        recurring.detect()
        assertTrue(recurring.observeActive().first().any { it.displayName == "Rent" && it.status == RecurringStatus.MANUAL })
    }

    @Test fun calendarShowsExpectedAndPaidBills() = runBlocking {
        val nov = recurring.observeCalendar(YearMonth.of(2026, 11)).first()
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), nov.map { it.date })
        assertTrue(nov.none { it.paid })
        val dec = recurring.observeCalendar(YearMonth.of(2026, 12)).first()
        assertEquals(listOf(LocalDate.of(2026, 12, 15)), dec.map { it.date })
    }
}
