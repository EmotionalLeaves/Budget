package com.emotionalleaves.sprout.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekSummaryTest {
    @Test fun phrasing() {
        assertEquals("You spent $412 this week, $88 less than last week. Biggest: Hy-Vee, $87.", WeekSummary.text(41_200, 50_000, "Hy-Vee", 8_734))
        assertEquals("You spent $500 this week, $100 more than last week.", WeekSummary.text(50_000, 40_000, null, null))
        assertEquals("You spent $400 this week, about the same as last week.", WeekSummary.text(40_000, 41_000, null, null))
        assertEquals("You spent $40 this week.", WeekSummary.text(4_000, 0, null, null))
    }
}
