package com.emotionalleaves.sprout.domain.sync

import com.emotionalleaves.sprout.domain.sync.DuplicateAccounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateAccountsTest {
    private val txs = (1..10).map { DuplicateAccounts.signature(it.toLong(), -100L * it, "SHOP $it") }.toSet()

    @Test fun sameAccountConnectedTwiceKeepsTheFreshOne() {
        val p = DuplicateAccounts.find(
            listOf(Account("old", "Joint Checking (1273)", 100, 10), Account("new", "Joint Checking (1273)", 200, 10)),
            mapOf("old" to txs, "new" to txs),
        )
        assertEquals(listOf("old" to "new"), p)
    }

    @Test fun differentAccountsWithTheSameNameStay() {
        val other = (1..10).map { DuplicateAccounts.signature(it.toLong(), -7L * it, "OTHER $it") }.toSet()
        assertTrue(DuplicateAccounts.find(
            listOf(Account("a", "Checking", 1, 10), Account("b", "Checking", 2, 10)), mapOf("a" to txs, "b" to other),
        ).isEmpty())
        assertTrue(DuplicateAccounts.find(listOf(Account("a", "Checking", 1, 10), Account("b", "Savings", 2, 10)), mapOf("a" to txs, "b" to txs)).isEmpty())
    }
}
