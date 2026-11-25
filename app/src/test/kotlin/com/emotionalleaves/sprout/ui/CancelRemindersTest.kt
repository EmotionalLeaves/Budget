package com.emotionalleaves.sprout.ui

import com.emotionalleaves.sprout.work.CancelReminders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CancelRemindersTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toEpochSecond()
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun remindsThreeDaysBeforeOnlyForMarkedOnes() {
        val bills = listOf(
            Triple(1L, "Netflix", at(today.plusDays(3))),
            Triple(2L, "Spotify", at(today.plusDays(3))),
            Triple(3L, "Gym", at(today.plusDays(9))),
        )
        val due = CancelReminders.due(bills, setOf(1L, 3L), today, zone, emptySet())
        assertEquals(listOf("cancel:1@2026-11-27"), due.map { it.first })
        assertEquals("Cancel Netflix before it renews on Nov 27", due.single().second)
        assertEquals(emptyList<Pair<String, String>>(), CancelReminders.due(bills, setOf(1L), today, zone, setOf("cancel:1@2026-11-27")))
    }
}
