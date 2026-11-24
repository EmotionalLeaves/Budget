package com.emotionalleaves.sprout.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/csv/$name")).readText()

    private fun load(name: String): Pair<CsvMapping, CsvParseResult> {
        val rows = Csv.parse(fixture(name))
        val m = requireNotNull(CsvImport.detect(rows)) { "no mapping for $name" }
        return m to CsvImport.parse(rows, m)
    }

    // --- RFC 4180 parser ---

    @Test fun quotedFieldsWithCommasQuotesAndNewlines() {
        val rows = Csv.parse("a,\"b, c\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\nx,y,z,w\r\n")
        assertEquals(listOf("a", "b, c", "say \"hi\"", "line1\nline2"), rows[0])
        assertEquals(listOf("x", "y", "z", "w"), rows[1])
    }

    @Test fun bomIsStripped() = assertEquals("Date", Csv.parse("﻿Date,Amount\n")[0][0])

    @Test fun detectsDelimiters() {
        assertEquals(';', Csv.detectDelimiter("a;b;c\n1;\"2;x\";3\n"))
        assertEquals('\t', Csv.detectDelimiter("a\tb\tc\n1\t2\t3\n"))
        assertEquals(',', Csv.detectDelimiter("a,b,c\n1,2,3\n"))
    }

    @Test fun emptyTrailingFieldsKept() = assertEquals(listOf("a", "", ""), Csv.parse("a,,\n")[0])

    @Test fun writerQuotesWhenNeeded() =
        assertEquals("plain,\"a,b\",\"q\"\"q\",\"l1\nl2\"\r\n", Csv.row(listOf("plain", "a,b", "q\"q", "l1\nl2")))

    @Test fun writerRoundTrips() {
        val fields = listOf("Trader Joe's \"QPS\"", "1,234.56", "multi\nline", "")
        assertEquals(fields, Csv.parse(Csv.row(fields)).single())
    }

    // --- Amounts and dates ---

    @Test fun amounts() {
        assertEquals(123456L, CsvImport.parseAmount("$1,234.56"))
        assertEquals(-1200L, CsvImport.parseAmount("(12.00)"))
        assertEquals(-120000L, CsvImport.parseAmount("($1,200.00)"))
        assertEquals(-1200L, CsvImport.parseAmount("-12.00"))
        assertEquals(-6000L, CsvImport.parseAmount("60.00-"))
        assertEquals(500L, CsvImport.parseAmount("+5"))
        assertEquals(-500L, CsvImport.parseAmount("-$5.00"))
        assertNull(CsvImport.parseAmount(""))
        assertNull(CsvImport.parseAmount("abc"))
    }

    @Test fun dateFormats() {
        assertEquals("M/d/yy", CsvImport.detectDateFormat(listOf("9/5/26", "10/12/26")))
        assertEquals("yyyy-MM-dd", CsvImport.detectDateFormat(listOf("2026-10-28")))
        assertEquals("MM/dd/yyyy", CsvImport.detectDateFormat(listOf("11/02/2026", "12/31/2026")))
        assertEquals(LocalDate.of(1999, 12, 31), CsvImport.parseDate("12/31/99", "MM/dd/yy"))
        assertNull(CsvImport.parseDate("02/30/2026", "MM/dd/yyyy"))
    }

    // --- Five bank-like formats ---

    @Test fun chaseStyleCard() {
        val (m, r) = load("card_chase_style.csv")
        assertEquals(5, m.amount)
        assertEquals(4, r.rows.size)
        assertTrue(r.errors.isEmpty())
        assertEquals("AMAZON.COM*AB12CD, SEATTLE", r.rows[1].description)
        assertEquals("TRADER JOE'S #552 \"QPS\"", r.rows[3].description)
        assertEquals(30000L, r.rows[2].amountMinor)
        assertEquals(LocalDate.of(2026, 11, 2), r.rows[0].date)
    }

    @Test fun bofaStyleWithSummaryPreamble() {
        val (m, r) = load("checking_bofa_style.csv")
        assertEquals(3, m.headerRow) // blank lines are skipped by the parser
        assertEquals(3, r.rows.size)
        assertEquals(185000L, r.rows[0].amountMinor)
        assertEquals(-8745L, r.rows[1].amountMinor)
        assertEquals("No amount", r.errors.single().reason) // the "Beginning balance" line
    }

    @Test fun capitalOneStyleDebitCredit() {
        val (m, r) = load("card_capone_style.csv")
        assertNull(m.amount)
        assertEquals("yyyy-MM-dd", m.dateFormat)
        assertEquals(listOf(-3850L, -1549L, 25000L, 1999L), r.rows.map { it.amountMinor })
    }

    @Test fun creditUnionSemicolonWithBom() {
        val (m, r) = load("creditunion_semicolon.csv")
        assertEquals("M/d/yy", m.dateFormat)
        assertEquals(listOf("SUPER SAVER; LINCOLN", "PAYROLL ACME", "VENMO \"Sam\""), r.rows.map { it.description })
        assertEquals(LocalDate.of(2026, 9, 5), r.rows[0].date)
        assertEquals(-4510L, r.rows[0].amountMinor)
    }

    @Test fun tabSeparatedWithParentheses() {
        val (_, r) = load("checking_tab_parentheses.tsv")
        assertEquals(listOf(-120000L, -1199L, 42L, -6000L), r.rows.map { it.amountMinor })
    }

    @Test fun malformedRowsAreReportedNotFatal() {
        val (_, r) = load("malformed_rows.csv")
        assertEquals(listOf("GOOD ROW", "EXTRA COLUMNS"), r.rows.map { it.description })
        assertEquals(4, r.errors.size)
        assertTrue(r.errors.any { it.reason.startsWith("Unreadable date") })
        assertTrue(r.errors.any { it.reason == "No date" })
    }

    @Test fun invertFlipsSigns() {
        val rows = Csv.parse(fixture("card_capone_style.csv"))
        val m = CsvImport.detect(rows)!!.copy(invert = true)
        assertEquals(3850L, CsvImport.parse(rows, m).rows.first().amountMinor)
    }

    @Test fun noRecognizableHeaderReturnsNull() = assertNull(CsvImport.detect(Csv.parse("foo,bar\n1,2\n")))

    // --- Dedup ids ---

    @Test fun idsAreDeterministicAndOverlapSafe() {
        val (_, r) = load("card_chase_style.csv")
        val a = CsvImport.ids("manual:x", r.rows)
        val b = CsvImport.ids("manual:x", r.rows)
        assertEquals(a, b)
        assertEquals(a.drop(1), CsvImport.ids("manual:x", r.rows.drop(1)))
        assertTrue(a.all { it.startsWith("manual:x:csv:") })
    }

    @Test fun identicalRowsInOneFileGetDifferentIds() {
        val row = CsvRow(2, LocalDate.of(2026, 11, 1), -500, "COFFEE")
        val ids = CsvImport.ids("manual:x", listOf(row, row.copy(line = 3)))
        assertNotEquals(ids[0], ids[1])
    }

    @Test fun whitespaceAndCaseDontChangeIds() {
        val a = CsvImport.ids("k", listOf(CsvRow(2, LocalDate.of(2026, 1, 1), -100, "Coffee  Shop")))
        val b = CsvImport.ids("k", listOf(CsvRow(9, LocalDate.of(2026, 1, 1), -100, "COFFEE SHOP")))
        assertEquals(a, b)
    }
}
