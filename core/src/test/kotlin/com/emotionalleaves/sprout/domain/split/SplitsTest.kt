package com.emotionalleaves.sprout.domain.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SplitsTest {
    @Test fun normalizeAppliesTheSignAndMergesCategories() {
        val parts = Splits.normalize(-10_000, listOf(SplitPart(2, 3_000), SplitPart(3, 1_000), SplitPart(2, 500), SplitPart(4, 0)))
        assertEquals(listOf(SplitPart(2, -3_500), SplitPart(3, -1_000)), parts)
        assertEquals(listOf(SplitPart(2, 2_000)), Splits.normalize(5_000, listOf(SplitPart(2, 2_000))))
    }

    @Test fun validSplit() {
        val parts = Splits.normalize(-10_000, listOf(SplitPart(2, 4_000)))
        assertNull(Splits.problem(-10_000, false, 1, parts))
        assertEquals(-6_000L, Splits.remainder(-10_000, parts))
    }

    @Test fun movingEverythingIsAllowed() =
        assertNull(Splits.problem(-10_000, false, 1, listOf(SplitPart(2, -6_000), SplitPart(3, -4_000))))

    @Test fun problems() {
        assertNotNull(Splits.problem(-10_000, true, 1, listOf(SplitPart(2, -1))))
        assertNotNull(Splits.problem(-10_000, false, 1, listOf(SplitPart(1, -1))))
        assertNotNull(Splits.problem(-10_000, false, 1, listOf(SplitPart(2, -10_001))))
        assertNotNull(Splits.problem(-10_000, false, 1, listOf(SplitPart(2, 100))))
        assertNotNull(Splits.problem(0, false, 1, listOf(SplitPart(2, -1))))
        assertNotNull(Splits.problem(-10_000, false, 1, (2L..12L).map { SplitPart(it, -1) }))
        assertNull(Splits.problem(-10_000, true, 1, emptyList()))
    }
}
