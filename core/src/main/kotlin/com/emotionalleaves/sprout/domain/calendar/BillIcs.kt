package com.emotionalleaves.sprout.domain.calendar

import com.emotionalleaves.sprout.domain.recurring.Cadence
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bills as an iCalendar (.ics) file: one repeating all-day event per bill, so any calendar app can show them.
 * Built on the phone and handed to the share sheet; nothing is uploaded by Sprout.
 */
object BillIcs {
    data class Bill(val id: Long, val name: String, val amountText: String, val next: LocalDate, val cadence: Cadence)

    private val day = DateTimeFormatter.BASIC_ISO_DATE

    fun build(bills: List<Bill>, stamp: String): String = buildString {
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Sprout//Bills//EN")
        line("CALSCALE:GREGORIAN")
        for (b in bills) {
            line("BEGIN:VEVENT")
            line("UID:sprout-bill-${b.id}@sprout.local")
            line("DTSTAMP:$stamp")
            line("DTSTART;VALUE=DATE:${b.next.format(day)}")
            line("DTEND;VALUE=DATE:${b.next.plusDays(1).format(day)}")
            line("RRULE:" + rule(b.cadence, b.next))
            line("SUMMARY:" + escape("${b.name} · ${b.amountText}"))
            line("DESCRIPTION:" + escape("Expected bill, from Sprout."))
            line("TRANSP:TRANSPARENT")
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
    }

    fun rule(c: Cadence, start: LocalDate): String = when (c) {
        Cadence.WEEKLY -> "FREQ=WEEKLY"
        Cadence.BIWEEKLY -> "FREQ=WEEKLY;INTERVAL=2"
        // Day 29–31 doesn't exist every month: BYMONTHDAY=-1 means "last day" so the event never skips a month.
        Cadence.MONTHLY -> if (start.dayOfMonth >= 29) "FREQ=MONTHLY;BYMONTHDAY=-1" else "FREQ=MONTHLY"
        Cadence.QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3"
        Cadence.YEARLY -> "FREQ=YEARLY"
    }

    /** RFC 5545 text escaping; also strips control characters from bank-supplied names. */
    fun escape(s: String): String = s.filter { it >= ' ' }
        .replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").take(200)

    private fun StringBuilder.line(s: String) { append(s).append("\r\n") }
}
