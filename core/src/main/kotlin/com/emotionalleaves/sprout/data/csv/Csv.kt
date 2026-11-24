package com.emotionalleaves.sprout.data.csv

/**
 * RFC 4180 CSV: quoted fields, doubled quotes inside quotes, embedded delimiters and newlines, CRLF or LF,
 * an optional UTF-8 BOM, and comma, semicolon or tab delimiters (auto-detected).
 */
object Csv {

    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val s = text.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var fieldStarted = false
        fun endField() { row.add(field.toString()); field.setLength(0); fieldStarted = false }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }
        while (i < s.length) {
            val ch = s[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else {
                    field.append(ch)
                }
            } else {
                when {
                    ch == '"' && !fieldStarted && field.isBlank() -> { field.setLength(0); inQuotes = true; fieldStarted = true }
                    ch == delimiter -> endField()
                    ch == '\r' -> { if (i + 1 < s.length && s[i + 1] == '\n') i++; endRow() }
                    ch == '\n' -> endRow()
                    else -> { field.append(ch); if (!ch.isWhitespace()) fieldStarted = true }
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) endRow()
        return rows
    }

    /** Picks the delimiter that splits the first lines most consistently (ignoring quoted text). */
    fun detectDelimiter(text: String): Char {
        val lines = text.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.take(10).toList()
        if (lines.isEmpty()) return ','
        return listOf(',', ';', '\t').maxByOrNull { d ->
            val counts = lines.map { countOutsideQuotes(it, d) }
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
            if (mode == 0) 0 else counts.count { it == mode } * 100 + mode
        } ?: ','
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var q = false
        var n = 0
        for (c in line) {
            if (c == '"') q = !q else if (c == d && !q) n++
        }
        return n
    }

    /** Writes one RFC 4180 row: fields with delimiters, quotes or newlines are quoted, quotes doubled. */
    fun row(fields: List<String>): String = fields.joinToString(",") { f ->
        if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || f.startsWith(" ") || f.endsWith(" ")) {
            "\"" + f.replace("\"", "\"\"") + "\""
        } else {
            f
        }
    } + "\r\n"
}
