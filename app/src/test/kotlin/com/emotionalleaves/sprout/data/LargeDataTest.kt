package com.emotionalleaves.sprout.data

import androidx.paging.PagingSource
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.budget.MonthWindow
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth
import java.time.ZoneId

/** 20,000 generated transactions: queries stay correct and use indexes instead of scanning. */
@RunWith(RobolectricTestRunner::class)
class LargeDataTest {
    private lateinit var env: TestEnv
    private val zone = ZoneId.of("America/Chicago")
    private val count = 20_000

    @Before fun setUp() = runBlocking {
        env = TestEnv()
        env.repo().ensureSeeded()
        val groceries = env.db.categories().getByKey(DefaultCategories.GROCERIES)!!.id
        env.db.accounts().upsert(listOf(AccountEntity(
            id = "sf:C:A", source = AccountSource.SIMPLEFIN, remoteId = "A", connId = null, name = "Checking", displayName = null,
            type = AccountType.CHECKING, currency = "USD", balanceMinor = 0, availableBalanceMinor = null, balanceDate = 0,
            includeInTotals = true, hidden = false, firstSyncCompleted = true, lastSyncedAt = null, createdAt = 0,
        )))
        val start = 1_700_000_000L
        (0 until count).chunked(1_000).forEach { chunk ->
            env.db.transactions().upsert(chunk.map { i ->
                TransactionEntity(
                    id = "sf:C:A:$i", accountId = "sf:C:A", remoteId = "$i", postedAt = start + i * 3_600L, transactedAt = null,
                    occurredAt = start + i * 3_600L, amountMinor = -100, currency = "USD", description = "STORE $i",
                    merchantKey = "STORE ${i % 50}", pending = false, categoryId = groceries, categorySource = CategorySource.BUILT_IN,
                    note = null, excludedFromBudget = false, recurringChargeId = null, alertedAt = null, importedAt = 0, updatedAt = 0,
                )
            })
        }
    }

    @After fun tearDown() = env.close()

    private fun plan(sql: String): String = env.db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { c ->
        buildString { while (c.moveToNext()) append(c.getString(c.columnCount - 1)).append('\n') }
    }

    @Test fun countAndFirstPage() = runBlocking {
        assertEquals(count, env.db.transactions().observeCount().first())
        val source = env.db.transactions().pagingRows("", null, false)
        val page = source.load(PagingSource.LoadParams.Refresh(null, 60, false)) as PagingSource.LoadResult.Page
        assertEquals(60, page.data.size)
        assertEquals("sf:C:A:${count - 1}", page.data.first().id) // newest first
    }

    @Test fun monthSumsAreCorrect() = runBlocking {
        val month = MonthWindow.monthOf(1_700_000_000L + 10_000 * 3_600L, zone)
        val w = MonthWindow.of(month, zone)
        val expected = (0 until count).count { (1_700_000_000L + it * 3_600L) in w } * -100L
        val sums = env.db.budgets().observeSums(w.start, w.end).first()
        assertEquals(expected, sums.single().total)
    }

    @Test fun hotQueriesUseIndexes() {
        val byDate = plan("SELECT * FROM transactions WHERE occurredAt >= 0 AND occurredAt < 10 ORDER BY occurredAt DESC")
        assertTrue(byDate, byDate.contains("index_transactions_occurredAt"))
        val byMerchant = plan("SELECT * FROM transactions WHERE merchantKey = 'X'")
        assertTrue(byMerchant, byMerchant.contains("index_transactions_merchantKey"))
        val byCategory = plan("SELECT COUNT(*) FROM transactions WHERE categoryId = 1")
        assertTrue(byCategory, byCategory.contains("index_transactions_categoryId"))
    }

    @Test fun budgetMonthForLargeData() = runBlocking {
        val repo = com.emotionalleaves.sprout.data.budget.BudgetRepository(env.db, env.clock, { zone })
        val m = repo.observeMonth(YearMonth.from(java.time.Instant.ofEpochSecond(1_700_000_000L + 100 * 3_600L).atZone(zone))).first()
        assertTrue(m.totalSpentMinor > 0)
    }
}
