package com.emotionalleaves.sprout.domain.transfer

import com.emotionalleaves.sprout.domain.transfer.TransferPairing.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferPairingTest {
    private val day = 24 * 3600L

    @Test fun pairsOppositeAmountsAcrossAccounts() {
        val p = TransferPairing.pairs(listOf(
            Row("out", "checking", -25_000, 0), Row("in", "savings", 25_000, day),
        ))
        assertEquals(listOf("out" to "in"), p)
    }

    @Test fun sameAccountTooFarApartOrTinyIsNotATransfer() {
        assertTrue(TransferPairing.pairs(listOf(Row("a", "x", -5_000, 0), Row("b", "x", 5_000, day))).isEmpty())
        assertTrue(TransferPairing.pairs(listOf(Row("a", "x", -5_000, 0), Row("b", "y", 5_000, 6 * day))).isEmpty())
        assertTrue(TransferPairing.pairs(listOf(Row("a", "x", -50, 0), Row("b", "y", 50, 0))).isEmpty())
        assertTrue(TransferPairing.pairs(listOf(Row("a", "x", -5_000, 0), Row("b", "y", 5_001, 0))).isEmpty())
    }

    @Test fun eachRowUsedOnceClosestWins() {
        val p = TransferPairing.pairs(listOf(
            Row("o1", "chk", -10_000, 0), Row("o2", "chk", -10_000, 3 * day),
            Row("i1", "sav", 10_000, 3 * day), Row("i2", "sav", 10_000, day),
        ))
        assertEquals(listOf("o1" to "i2", "o2" to "i1"), p)
    }
}
