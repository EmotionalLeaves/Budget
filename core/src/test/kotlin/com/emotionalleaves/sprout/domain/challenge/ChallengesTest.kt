package com.emotionalleaves.sprout.domain.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ChallengesTest {
    private val start = LocalDate.of(2026, 11, 20)
    private fun d(n: Int) = start.plusDays(n.toLong())

    @Test fun noSpendCategory() {
        val ch = Challenge("1", ChallengeType.NO_SPEND_CATEGORY, "No eating out", start, 7, categoryId = 5)
        assertEquals(ChallengeStatus.ON_TRACK, Challenges.evaluate(ch, mapOf(d(1) to mapOf(2L to 500L)), d(3)).status)
        assertEquals(4, Challenges.evaluate(ch, emptyMap(), d(3)).daysIn)
        assertEquals(ChallengeStatus.MISSED, Challenges.evaluate(ch, mapOf(d(2) to mapOf(5L to 900L)), d(3)).status)
        assertEquals(ChallengeStatus.WON, Challenges.evaluate(ch, emptyMap(), d(8)).status)
    }

    @Test fun underAmount() {
        val ch = Challenge("2", ChallengeType.UNDER_AMOUNT, "Groceries under $100", start, 7, categoryId = 3, amountMinor = 10_000)
        val p = Challenges.evaluate(ch, mapOf(d(0) to mapOf(3L to 4_000L), d(1) to mapOf(3L to 2_000L)), d(2))
        assertEquals(ChallengeStatus.ON_TRACK, p.status)
        assertEquals(0.6f, p.fraction, 0.001f)
        assertEquals(ChallengeStatus.MISSED, Challenges.evaluate(ch, mapOf(d(0) to mapOf(3L to 12_000L)), d(2)).status)
    }

    @Test fun noSpendDays() {
        val ch = Challenge("3", ChallengeType.NO_SPEND_DAYS, "3 no-spend days", start, 7, goal = 3)
        val spend = mapOf(d(0) to mapOf(1L to 100L), d(2) to mapOf(1L to 100L))
        // Days 1 and 3 had no spending (today, day 4, doesn't count until it's over).
        val p = Challenges.evaluate(ch, spend, d(4))
        assertEquals(ChallengeStatus.ON_TRACK, p.status)
        assertEquals("2 of 3 no-spend days", p.detail)
        assertEquals(ChallengeStatus.WON, Challenges.evaluate(ch, spend, d(5)).status)
    }

    @Test fun healthScore() {
        val r = HealthScore.compute(20, 3.0, 30, true)
        assertEquals(100, r.score)
        val half = HealthScore.compute(10, null, 15, null)
        assertEquals(48, half.score) // 12 + 12 of 50
        assertNull(HealthScore.compute(null, null, null, null).score)
    }
}
