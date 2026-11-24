package com.emotionalleaves.sprout.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Money helpers. All amounts in Sprout are Long minor units (cents), never Float or Double.
 */
object Money {

    /**
     * Parses a SimpleFIN numeric string such as "-12.34", "1000", "+5.5" into cents.
     * Values with more than two decimals are rounded half-up (SimpleFIN amounts for USD never have more).
     * Returns null for anything that is not a plain decimal number, so one bad row never breaks a sync.
     */
    fun parseMinor(text: String?): Long? {
        if (text == null) return null
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty() || cleaned.length > 40) return null
        if (!DECIMAL.matches(cleaned)) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Formats cents as US dollars, e.g. -1234 -> "-$12.34". */
    fun format(amountMinor: Long, showPlus: Boolean = false): String {
        val abs = BigDecimal.valueOf(amountMinor).abs().movePointLeft(2)
        val body = usd().format(abs)
        return when {
            amountMinor < 0 -> "-$body"
            showPlus && amountMinor > 0 -> "+$body"
            else -> body
        }
    }

    /** Formats whole dollars without cents, e.g. 123456 -> "$1,235". Rounds half-up. */
    fun formatWhole(amountMinor: Long): String {
        val dollars = BigDecimal.valueOf(amountMinor).movePointLeft(2).setScale(0, RoundingMode.HALF_UP)
        val f = usd().apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
        val body = f.format(dollars.abs())
        return if (dollars.signum() < 0) "-$body" else body
    }

    /** Parses what a person types ("12", "12.5", "$1,200.00") into cents. */
    fun parseUserInput(text: String): Long? = parseMinor(text.trim().removePrefix("$").trim())

    private fun usd(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    private val DECIMAL = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)""")
}
