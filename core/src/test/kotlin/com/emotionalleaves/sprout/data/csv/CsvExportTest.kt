package com.emotionalleaves.sprout.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CsvExportTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(m: Int, d: Int, h: Int = 12) = LocalDateTime.of(2026, m, d, h, 0).atZone(zone).toEpochSecond()

    private val txs = listOf(
        ExportTx(at(11, 2), "STARBUCKS STORE 12345", -575, "Eating Out", "Checking", false, null, "STARBUCKS STORE"),
        ExportTx(at(11, 3), "AMAZON.COM*AB12CD, SEATTLE \"WA\"", -4210, "Shopping", "Card", false, "gift for Sam\nwrapped", "AMAZON"),
        ExportTx(at(11, 5, 23), "PAYROLL ACME", 185000, "Income", "Checking", false, null, "PAYROLL ACME"),
        ExportTx(at(11, 6), "=HYPERLINK(\"x\")", -100, "Other", "Checking", true, null, "HYPERLINK"),
        ExportTx(at(11, 6), "COFFEE", -500, "Eating Out", "Checking", false, null, "COFFEE"),
        ExportTx(at(11, 6), "COFFEE", -500, "Eating Out", "Checking", false, null, "COFFEE"),
    )

    @Test fun amountsArePlainDecimals() {
        assertEquals("-12.34", CsvExport.amount(-1234))
        assertEquals("0.05", CsvExport.amount(5))
        assertEquals("1850.00", CsvExport.amount(185000))
    }

    @Test fun datesAreIsoInLocalZone() = assertEquals("2026-11-05", CsvExport.isoDate(at(11, 5, 23), zone))

    @Test fun exportReimportsToIdenticalTransactions() {
        val text = CsvExport.transactions(txs, zone)
        val rows = Csv.parse(text)
        val m = CsvImport.detect(rows)!!
        assertEquals("yyyy-MM-dd", m.dateFormat)
        val back = CsvImport.parse(rows, m)
        assertEquals(emptyList<CsvRowError>(), back.errors)
        assertEquals(
            txs.map { Triple(CsvExport.isoDate(it.occurredAt, zone), it.amountMinor, it.description) },
            back.rows.map { Triple(it.date.toString(), it.amountMinor, it.description) },
        )
        // Identical rows (the two coffees) stay two transactions with distinct ids.
        assertEquals(back.rows.size, CsvImport.ids("manual:x", back.rows).toSet().size)
    }

    @Test fun formulaLikeTextIsNeutralizedButNumbersAreNot() {
        assertEquals("'=HYPERLINK(\"x\")", CsvExport.neutralize("=HYPERLINK(\"x\")"))
        assertEquals("-12.34", CsvExport.neutralize("-12.34"))
        assertEquals("'+1 call me", CsvExport.neutralize("+1 call me"))
    }

    @Test fun exportHasHeaderAndNoCredentials() {
        val text = CsvExport.transactions(txs, zone)
        assertEquals(CsvExport.TRANSACTION_HEADER, Csv.parse(text).first())
        assertFalse(text.contains("://"))
    }
}
