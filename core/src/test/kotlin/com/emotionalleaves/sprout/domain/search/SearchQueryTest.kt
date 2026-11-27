package com.emotionalleaves.sprout.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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

    private val today = LocalDate.of(2026, 11, 26) // a Thursday
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test fun lastMonthWithText() {
        val q = SearchQuery.parse("hy-vee last month", today)
        assertEquals("hy-vee", q.text)
        assertEquals(d(10, 1), q.fromDate); assertEquals(d(10, 31), q.toDate); assertEquals("Last month", q.dateLabel)
    }
    @Test fun monthNameMeansItsLatestStartedMonth() {
        assertEquals(d(11, 1), SearchQuery.parse("nov", today).fromDate)
        val dec = SearchQuery.parse("December", today)
        assertEquals(d(12, 1, 2025), dec.fromDate); assertEquals(d(12, 31, 2025), dec.toDate); assertEquals("December 2025", dec.dateLabel)
        assertEquals(d(2, 28, 2025), SearchQuery.parse("feb 2025", today).toDate)
        assertEquals(d(9, 1), SearchQuery.parse("sept", today).fromDate)
    }
    @Test fun amountAndDateTogether() {
        val q = SearchQuery.parse(">50 october", today)
        assertEquals(5_001L, q.minAbsMinor); assertEquals("", q.text); assertEquals(d(10, 1), q.fromDate)
    }
    @Test fun weeksAndDays() {
        assertEquals(d(11, 23) to d(11, 26), SearchQuery.parse("this week", today).let { it.fromDate to it.toDate })
        assertEquals(d(11, 16) to d(11, 22), SearchQuery.parse("last week", today).let { it.fromDate to it.toDate })
        assertEquals(d(11, 25), SearchQuery.parse("yesterday", today).fromDate)
        assertEquals(d(1, 1, 2025) to d(12, 31, 2025), SearchQuery.parse("last year", today).let { it.fromDate to it.toDate })
    }
    @Test fun wordsThatOnlyLookLikeMonths() {
        assertEquals(SearchQuery("marathon"), SearchQuery.parse("marathon", today))
        assertEquals(SearchQuery("novel books"), SearchQuery.parse("novel books", today))
        assertEquals(SearchQuery("this place"), SearchQuery.parse("this place", today))
    }

    @Test fun singleDays() {
        val q = SearchQuery.parse("nov 24", today)
        assertEquals(d(11, 24) to d(11, 24), q.fromDate to q.toDate); assertEquals("Tue, Nov 24", q.dateLabel)
        assertEquals(d(12, 25, 2025), SearchQuery.parse("dec 25", today).fromDate) // hasn't happened this year yet
        assertEquals(d(3, 1, 2025), SearchQuery.parse("hy-vee march 1 2025", today).let { assertEquals("hy-vee", it.text); it.fromDate })
        // Not a day: amounts after a month still read as a month plus an amount.
        assertEquals(d(10, 1), SearchQuery.parse("october", today).fromDate)
    }
}
