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

    @Test fun rejectsJunk() {
        assertNull(TxFilter.decode("nope"))
        assertNull(TxFilter.decode(TxFilter.encode(" ", TxFilter()).replaceFirst(" ", "")))
    }
}
