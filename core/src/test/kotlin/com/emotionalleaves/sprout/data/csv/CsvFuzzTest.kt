package com.emotionalleaves.sprout.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Random input never crashes the importer, and anything written by [Csv.row] reads back the same. */
class CsvFuzzTest {
    private val alphabet = "abcXYZ019 ,;\t\"'\n\r()-+.$€=@ é﻿/"

    private fun randomText(r: Random, max: Int) = buildString { repeat(r.nextInt(max)) { append(alphabet[r.nextInt(alphabet.length)]) } }

    @Test fun parserNeverThrows() {
        val r = Random(42)
        repeat(3_000) {
            val text = randomText(r, 300)
            val rows = Csv.parse(text)
            val m = CsvImport.detect(rows)
            if (m != null) CsvImport.parse(rows, m)
            CsvImport.detect(listOf(listOf("Date", "Description", "Amount")) + rows)?.let { CsvImport.parse(rows, it) }
        }
    }

    @Test fun amountParserNeverThrows() {
        val r = Random(7)
        repeat(20_000) {
            val t = randomText(r, 25)
            CsvImport.parseAmount(t)
            CsvImport.parseAmount(t, decimalComma = true)
        }
        // Absurdly long numbers are refused, not overflowed.
        assertEquals(null, CsvImport.parseAmount("9".repeat(40)))
    }

    @Test fun writtenRowsReadBack() {
        val r = Random(9)
        repeat(1_000) {
            val fields = List(r.nextInt(1, 6)) { randomText(r, 20).replace("﻿", "").replace("\r", "") }
            // Blank lines are skipped on purpose (bank exports have them), so an all-blank row reads as nothing.
            if (fields.all { it.isBlank() }) return@repeat
            assertEquals(listOf(fields), Csv.parse(Csv.row(fields), ','))
        }
    }
}
