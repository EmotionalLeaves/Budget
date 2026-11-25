package com.emotionalleaves.sprout.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {
    @Test fun text() = assertEquals(SearchQuery("starbucks"), SearchQuery.parse("  starbucks "))
    @Test fun exact() = assertEquals(SearchQuery("", 4_210, 4_210), SearchQuery.parse("$42.10"))
    @Test fun negativeExact() = assertEquals(SearchQuery("", 500, 500), SearchQuery.parse("-5"))
    @Test fun over() = assertEquals(SearchQuery("", minAbsMinor = 10_001), SearchQuery.parse(">100"))
    @Test fun atLeast() = assertEquals(SearchQuery("", minAbsMinor = 10_000), SearchQuery.parse(">= 100"))
    @Test fun under() = assertEquals(SearchQuery("", maxAbsMinor = 499), SearchQuery.parse("<5"))
    @Test fun range() = assertEquals(SearchQuery("", 2_000, 5_000), SearchQuery.parse("50-20"))
    @Test fun thousands() = assertEquals(SearchQuery("", 120_000, 120_000), SearchQuery.parse("1,200"))
    @Test fun storeNumbersStayText() = assertEquals(SearchQuery("7-eleven"), SearchQuery.parse("7-eleven"))
    @Test fun empty() = assertEquals(SearchQuery(""), SearchQuery.parse(""))
}
