package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.forecast.ForecastRepository
import com.emotionalleaves.sprout.data.recurring.RecurringRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ForecastRepositoryTest {
    private val zone = ZoneId.of("America/Chicago")
    private lateinit var env: TestEnv
    private lateinit var forecast: ForecastRepository

    private fun at(date: LocalDate) = LocalDateTime.of(date, java.time.LocalTime.NOON).atZone(zone).toEpochSecond()

    @Before fun setUp() {
        env = TestEnv(nowSeconds = at(LocalDate.of(2026, 11, 21)))
        val recurring = RecurringRepository(env.db, env.clock, { zone })
        val budgets = BudgetRepository(env.db, env.clock, { zone }, recurring::expectedCharges)
        forecast = ForecastRepository(env.db, budgets, recurring, env.clock, { zone })
        runBlocking {
            env.secure.saveAccessUrl(env.accessUrl)
            val pay = (0..7).map { LocalDate.of(2026, 8, 7).plusWeeks(2L * it) } // ..., Nov 13; next Nov 27
            val txs = pay.mapIndexed { i, d -> """{"id":"p$i","posted":${at(d)},"amount":"1850.00","description":"ACME CORP PAYROLL"}""" } +
                (6..10).map { m -> """{"id":"n$m","posted":${at(LocalDate.of(2026, m, 25))},"amount":"-15.49","description":"NETFLIX.COM"}""" } +
                listOf(
                    """{"id":"g1","posted":${at(LocalDate.of(2026, 10, 10))},"amount":"-100.00","description":"KROGER #1"}""",
                    """{"id":"g2","posted":${at(LocalDate.of(2026, 11, 10))},"amount":"-150.00","description":"KROGER #1"}""",
                    """{"id":"g3","posted":${at(LocalDate.of(2026, 11, 2))},"amount":"-30.00","description":"KROGER #1"}""",
                )
            env.server.enqueue(MockResponse().setBody(
                """{"errlist":[],"connections":[{"conn_id":"C","name":"Bank","org_id":"O","sfin_url":"https://bridge.example.test/simplefin"}],
                   "accounts":[{"id":"A","name":"Everyday Checking","conn_id":"C","currency":"USD","balance":"1000.00","balance-date":1,
                   "transactions":[${txs.joinToString(",")}]}]}""",
            ))
            env.repo().sync(SyncTrigger.MANUAL)
            recurring.detect()
        }
    }

    @After fun tearDown() = env.close()

    @Test fun forecastIncludesPaycheckBillAndEverydaySpending() = runBlocking {
        val f = assertNotNull(forecast.outlook().forecast).let { forecast.outlook().forecast!! }
        assertEquals(100_000L, f.startMinor)
        assertEquals(LocalDate.of(2026, 11, 27), f.nextIncome!!.date)
        assertEquals(185_000L, f.incomeMinor)
        assertEquals(1_549L, f.billsMinor) // Netflix on Nov 25
        // No budgets: everyday spending is the last 30 days' pace (Kroger $180) over the 10 remaining days.
        assertEquals(18_000L * 10 / 30, f.spendingMinor)
        assertEquals(100_000L + 185_000L - 1_549L - f.spendingMinor, f.endMinor)
    }

    @Test fun trendComparesSameDaysLastMonth() = runBlocking {
        val t = forecast.outlook().trend!!
        // Nov 1–21: Kroger $150 + $30 + Netflix not yet; Oct 1–21: Kroger $100.
        assertEquals(18_000L, t.thisMonthMinor)
        assertEquals(10_000L, t.lastMonthSameDaysMinor)
        assertEquals(80, t.percent)
    }
}
