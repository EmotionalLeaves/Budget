package com.emotionalleaves.sprout.update

/** Reads Sprout's CHANGELOG.md (bundled in the app) for the "What's new" screen. */
object Changelog {
    data class Group(val heading: String, val items: List<String>)

    /** The section for [version]: its "### " groups and their bullet lines, markdown markers removed. */
    fun section(markdown: String, version: String): List<Group> {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.startsWith("## [${version.removePrefix("v").substringBefore('-')}]") }
        if (start < 0) return emptyList()
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## [") } ?: lines.size
        val groups = mutableListOf<Group>()
        var heading = ""
        var items = mutableListOf<String>()
        fun flush() { if (items.isNotEmpty()) groups += Group(heading, items.toList()); items = mutableListOf() }
        for (l in lines.subList(start + 1, end)) {
            val t = l.trim()
            when {
                t.startsWith("### ") -> { flush(); heading = t.removePrefix("### ").trim() }
                t.startsWith("- ") -> items += clean(t.removePrefix("- "))
                t.isNotEmpty() && items.isNotEmpty() && l.startsWith("  ") -> items[items.lastIndex] = items.last() + " " + clean(t)
            }
        }
        flush()
        return groups
    }

    private fun clean(s: String) = s.replace("**", "").replace("`", "").trim()
}
