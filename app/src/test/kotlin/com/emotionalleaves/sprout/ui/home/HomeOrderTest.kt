package com.emotionalleaves.sprout.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOrderTest {
    private val all = HomeCards.map { it.first }

    @Test fun emptyMeansDefaultOrder() = assertEquals(all, orderedHomeCards(""))

    @Test fun savedOrderKeptAndUnknownKeysDropped() {
        val saved = listOf("accounts", "stats", "nonsense") + all.filter { it != "accounts" && it != "stats" }
        val out = orderedHomeCards(saved.joinToString(","))
        assertEquals("accounts", out[0]); assertEquals("stats", out[1])
        assertEquals(all.toSet(), out.toSet()); assertEquals(all.size, out.size)
    }

    @Test fun cardsAddedLaterSlotInAfterTheirDefaultNeighbour() {
        // An older saved order that doesn't know "week" yet.
        val saved = all.filter { it != "week" }
        val out = orderedHomeCards(saved.joinToString(","))
        assertEquals(out.indexOf("stats") + 1, out.indexOf("week"))
    }

    @Test fun moving() {
        val o = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveCard(o, "b", -1))
        assertEquals(o, moveCard(o, "a", -1))
        assertEquals(listOf("a", "c", "b"), moveCard(o, "b", +1))
        assertEquals(o, moveCard(o, "zzz", +1))
    }
}
