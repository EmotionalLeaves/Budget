package com.emotionalleaves.sprout.ui

import com.emotionalleaves.sprout.ui.transactions.TxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSearchTest {
    @Test fun roundTrips() {
        val f = TxFilter(query = ">100", accountId = "sf:C:A", categoryId = 4, tagId = null, uncategorizedOnly = false, pendingOnly = true)
        assertEquals("Big ones" to f, TxFilter.decode(TxFilter.encode("Big ones", f)))
        assertEquals("x" to TxFilter(), TxFilter.decode(TxFilter.encode("x", TxFilter())))
    }

    @Test fun moneyInAndOlderSavedSearches() {
        val f = TxFilter(query = "payroll", incomingOnly = true)
        assertEquals("Pay" to f, TxFilter.decode(TxFilter.encode("Pay", f)))
        // Saved before "Money in" existed: 7 parts, still readable.
        val old = listOf("Old", "hy-vee", "", "", "", "", "1").joinToString("\u001F")
        assertEquals("Old" to TxFilter(query = "hy-vee", pendingOnly = true), TxFilter.decode(old))
    }

    @Test fun rejectsJunk() {
        assertNull(TxFilter.decode("nope"))
        assertNull(TxFilter.decode(TxFilter.encode(" ", TxFilter()).replaceFirst(" ", "")))
    }
}
