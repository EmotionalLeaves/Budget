package com.emotionalleaves.sprout.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesNegativeDecimal() = assertEquals(-1234L, Money.parseMinor("-12.34"))
    @Test fun parsesWholeNumber() = assertEquals(100000L, Money.parseMinor("1000"))
    @Test fun parsesPlusSign() = assertEquals(550L, Money.parseMinor("+5.5"))
    @Test fun parsesLeadingDot() = assertEquals(50L, Money.parseMinor(".50"))
    @Test fun roundsHalfUpBeyondCents() = assertEquals(1235L, Money.parseMinor("12.345"))
    @Test fun parsesWithWhitespaceAndCommas() = assertEquals(123456L, Money.parseMinor(" 1,234.56 "))
    @Test fun rejectsGarbage() = assertNull(Money.parseMinor("12abc"))
    @Test fun rejectsExponent() = assertNull(Money.parseMinor("1e5"))
    @Test fun rejectsNull() = assertNull(Money.parseMinor(null))
    @Test fun rejectsEmpty() = assertNull(Money.parseMinor(""))
    @Test fun noFloatingPointDrift() = assertEquals(30L, Money.parseMinor("0.30"))
    @Test fun formatsNegative() = assertEquals("-$12.34", Money.format(-1234))
    @Test fun formatsThousands() = assertEquals("$1,234.56", Money.format(123456))
    @Test fun formatsPlus() = assertEquals("+$5.00", Money.format(500, showPlus = true))
    @Test fun formatsWhole() = assertEquals("$1,235", Money.formatWhole(123456))
    @Test fun parsesUserInputWithDollar() = assertEquals(120000L, Money.parseUserInput("$1,200.00"))
}
