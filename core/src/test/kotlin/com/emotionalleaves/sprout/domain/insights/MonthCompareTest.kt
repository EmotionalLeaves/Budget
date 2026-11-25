package com.emotionalleaves.sprout.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthCompareTest {
    @Test fun biggestChangeFirst() {
        val rows = MonthCompare.compare(
            now = mapOf("Groceries" to 40_000L, "Eating out" to 30_000L, "Gifts" to 5_000L),
            before = mapOf("Groceries" to 38_000L, "Eating out" to 10_000L, "Gas" to 6_000L, "Zero" to 0L),
        )
        assertEquals(listOf("Eating out", "Gas", "Gifts", "Groceries"), rows.map { it.name })
        assertEquals(200, rows[0].percent)
        assertEquals(-100, rows[1].percent)
        assertNull(rows[2].percent)
        assertEquals(2_000L, rows[3].deltaMinor)
    }
}
