package com.emotionalleaves.sprout.domain.categorize

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleUpgradesTest {
    private val keys = mapOf(DefaultCategories.P2P to 1L, DefaultCategories.RENT to 2L, DefaultCategories.UNCATEGORIZED to 3L, DefaultCategories.INCOME to 4L)

    @Test fun amountConditionsPickTheSpecificRule() {
        val rules = listOf(
            UserRule(1, RuleType.CONTAINS, "VENMO", 1),
            UserRule(2, RuleType.CONTAINS, "VENMO", 2, minAbsMinor = 50_000),
        )
        val r = CategoryResolver(rules, keys)
        assertEquals(2L, r.resolve("VENMO PAYMENT LANDLORD", "VENMO", -120_000).categoryId)
        assertEquals(1L, r.resolve("VENMO PAYMENT SAM", "VENMO", -2_000).categoryId)
    }

    @Test fun outOfRangeFallsThrough() {
        val r = CategoryResolver(listOf(UserRule(1, RuleType.EXACT, "ZELLE", 2, maxAbsMinor = 10_000)), keys)
        assertEquals(2L, r.resolve("ZELLE TO X", "ZELLE", -5_000).categoryId)
        org.junit.Assert.assertNotEquals(2L, r.resolve("ZELLE TO X", "ZELLE", -50_000).categoryId)
    }

    @Test fun tagRules() {
        val rules = listOf(TagRule(1, "delta", 7), TagRule(2, "hilton", 7), TagRule(3, "uber", 9))
        assertEquals(setOf(7L), TagRules.match(rules, "DELTA AIR 006123", "DELTA AIR"))
        assertEquals(setOf(9L), TagRules.match(rules, "UBER TRIP", "UBER"))
        assertEquals(emptySet<Long>(), TagRules.match(rules, "DELTAWAVE CAFE", "DELTAWAVE CAFE"))
    }
}
