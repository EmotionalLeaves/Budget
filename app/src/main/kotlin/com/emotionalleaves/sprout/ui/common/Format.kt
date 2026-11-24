package com.emotionalleaves.sprout.ui.common

import com.emotionalleaves.sprout.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    private val dayHeaderWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US)

    fun localDate(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayHeader) else date.format(dayHeaderWithYear)
    }

    fun dateTime(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochSecond(epochSeconds).atZone(zone).format(dateTime)

    /** "just now", "12 min ago", "3 h ago", "2 days ago". */
    fun ago(epochSeconds: Long, nowSeconds: Long): String {
        val s = (nowSeconds - epochSeconds).coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            s < 2 * 86_400 -> "yesterday"
            else -> "${s / 86_400} days ago"
        }
    }

    /** "-$12.34" out (default color), "+$12.34" in (the UI also colors it green). */
    fun amount(amountMinor: Long): String = Money.format(amountMinor, showPlus = true)

    /** What TalkBack reads: "spent 12 dollars and 34 cents" / "received ...". */
    fun amountSpoken(amountMinor: Long): String {
        val abs = kotlin.math.abs(amountMinor)
        val dollars = abs / 100
        val cents = abs % 100
        val words = if (cents == 0L) "$dollars dollars" else "$dollars dollars and $cents cents"
        return if (amountMinor > 0) "received $words" else "spent $words"
    }
}
