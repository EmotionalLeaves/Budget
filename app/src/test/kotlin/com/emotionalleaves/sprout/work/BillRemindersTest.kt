package com.emotionalleaves.sprout.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillRemindersTest {
    private val z = ZoneId.of("America/Chicago")
    private fun at(d: Int) = LocalDate.of(2026, 11, d).atTime(10, 0).atZone(z).toEpochSecond()

    @Test fun onlyTomorrowAndOnlyOnce() {
        val bills = listOf(
            Triple(1L, "Rent", -120_000L to at(25)),
            Triple(2L, "Netflix", -1_549L to at(26)),
            Triple(3L, "Phone", -6_000L to at(25)),
        )
        val today = LocalDate.of(2026, 11, 24)
        val due = BillReminders.dueTomorrow(bills, today, z, already = setOf("3@2026-11-25"))
        assertEquals(listOf("Rent"), due.map { it.name })
        assertEquals("1@2026-11-25", due.single().key)
    }
}
