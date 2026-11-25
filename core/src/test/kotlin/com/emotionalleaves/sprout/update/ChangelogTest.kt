package com.emotionalleaves.sprout.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val md = """
        # Changelog

        ## [2.1.0] - 2026-11-25

        Intro line.

        ### Added
        - **Widget** on your home screen.
        - Merchant pages
          with a chart.

        ### Fixed
        - A `crash`.

        ## [2.0.0] - 2026-11-24

        ### Added
        - Old stuff
    """.trimIndent()

    @Test fun readsTheVersionsSection() {
        val s = Changelog.section(md, "2.1.0")
        assertEquals(listOf("Added", "Fixed"), s.map { it.heading })
        assertEquals(listOf("Widget on your home screen.", "Merchant pages with a chart."), s[0].items)
        assertEquals(listOf("A crash."), s[1].items)
    }

    @Test fun debugSuffixAndMissingVersion() {
        assertEquals(1, Changelog.section(md, "2.0.0-debug").size)
        assertTrue(Changelog.section(md, "9.9.9").isEmpty())
    }
}
