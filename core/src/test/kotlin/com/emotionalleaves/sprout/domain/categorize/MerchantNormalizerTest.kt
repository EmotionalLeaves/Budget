package com.emotionalleaves.sprout.domain.categorize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {
    private fun n(s: String) = MerchantNormalizer.normalize(s)

    @Test fun stripsPosDebitPrefixStoreNumberAndState() =
        assertEquals("HY-VEE LINCOLN", n("POS DEBIT CARD PURCHASE HY-VEE #1234 LINCOLN NE"))

    @Test fun stripsPurchaseAuthorizedOnDate() =
        assertEquals("KROGER", n("PURCHASE AUTHORIZED ON 03/14 KROGER #0412 S123456789012345 CARD 4421"))

    @Test fun stripsCheckcard() = assertEquals("SHELL OIL OMAHA", n("CHECKCARD 0314 SHELL OIL 57444123 OMAHA NE"))

    @Test fun stripsAchPrefix() = assertEquals("ACME CORP PAYROLL", n("ACH ACME CORP PAYROLL PPD ID: 9876543210"))

    @Test fun stripsRecurringPrefix() = assertEquals("SPOTIFY", n("RECURRING SPOTIFY P1A2B3C4D5"))

    @Test fun stripsSquarePrefix() = assertEquals("COFFEE HOUSE", n("SQ *COFFEE HOUSE"))

    @Test fun stripsToastPrefix() = assertEquals("RUNZA LINCOLN", n("TST* RUNZA LINCOLN NE"))

    @Test fun stripsPaypalPrefix() = assertEquals("NETFLIX", n("PAYPAL *NETFLIX 4029357733"))

    @Test fun stripsPpPrefix() = assertEquals("ETSY", n("PP*ETSY 888-123-4567"))

    @Test fun keepsPartAfterGoogle() = assertEquals("YOUTUBE PREMIUM", n("GOOGLE *YOUTUBE PREMIUM G.CO/HELPPAY#"))

    @Test fun appleComBillBecomesApple() = assertEquals("APPLE", n("APPLE.COM/BILL 866-712-7753 CA"))

    @Test fun amazonOrderCodeBecomesAmazon() = assertEquals("AMAZON", n("AMAZON.COM*AB12CD SEATTLE WA"))

    @Test fun amazonMarketplaceBecomesAmazon() = assertEquals("AMAZON", n("AMZN MKTP US*2K4LM1PQ0"))

    @Test fun amazonPrimeStaysDistinct() = assertEquals("AMAZON PRIME", n("AMAZON PRIME*1A2B3C4D5"))

    @Test fun removesPhoneNumber() = assertEquals("NETFLIX", n("NETFLIX.COM 866-579-7172 CA"))

    @Test fun removesDates() = assertEquals("VENMO PAYMENT", n("VENMO PAYMENT 11/02/2026"))

    @Test fun keepsAtMostThreeTokens() = assertEquals("THE GREAT CHINESE", n("THE GREAT CHINESE BUFFET AND GRILL"))

    @Test fun collapsesWhitespace() = assertEquals("TRADER JOES", n("  TRADER   JOE'S   #552  "))

    @Test fun removesCardFragments() = assertEquals("TARGET", n("TARGET 00012345 XXXX1234"))

    @Test fun removesStarCodes() = assertEquals("UBER TRIP", n("UBER *TRIP HELP.UBER.COM 8005928996"))

    @Test fun lowercaseInputIsUppercased() = assertEquals("STARBUCKS STORE", n("starbucks store 01234"))

    @Test fun repeatedPrefixesAreAllStripped() = assertEquals("CAFE BLOOM", n("POS DEBIT CARD PURCHASE SQ *CAFE BLOOM"))

    @Test fun stateCodeOnlyStrippedWhenTrailing() = assertEquals("NE FURNITURE MART", n("NE FURNITURE MART"))

    @Test fun sameMerchantDifferentStoresShareKey() =
        assertEquals(n("CASEYS #3021 LINCOLN NE"), n("CASEYS #2280 LINCOLN NE"))

    @Test fun emptyDescriptionHasFallbackKey() = assertEquals("UNKNOWN", n("   "))

    @Test fun deterministic() = assertEquals(n("WAL-MART #1234"), n("WAL-MART #1234"))

    @Test fun displayNameTitleCases() =
        assertEquals("Hy-Vee Lincoln", MerchantNormalizer.displayName("HY-VEE LINCOLN"))

    @Test fun bankWordingInFrontIsNotTheMerchant() {
        assertEquals("CARRIAGE PARK", MerchantNormalizer.normalize("18370 VISA DEBIT PURC Carriage Park 402-4418304 NE 10000971 018370"))
        assertEquals("HY-VEE LINCOLN", MerchantNormalizer.normalize("4821 VISA DEBIT PURC HY-VEE LINCOLN 1234 LINCOLN NE 10000971 018370"))
        assertEquals("RUNZA", MerchantNormalizer.normalize("MC DEBIT PURCHASE RUNZA #12"))
        assertEquals("SALVAGE WAREHOUS LINCOLN", MerchantNormalizer.normalize("45020 VISA DEBIT PURC SALVAGE WAREHOUS LINCOLN NE 00000000 045020"))
        assertEquals("APPLE STORE", MerchantNormalizer.normalize("APPLE STORE #R123"))
    }

    @Test fun genericKeys() {
        assertTrue(MerchantNormalizer.isGeneric("VISA DEBIT"))
        assertTrue(MerchantNormalizer.isGeneric("VISA DEBIT PURC"))
        assertTrue(MerchantNormalizer.isGeneric("CHECK"))
        assertTrue(MerchantNormalizer.isGeneric(""))
        assertFalse(MerchantNormalizer.isGeneric("CARRIAGE PARK"))
        assertFalse(MerchantNormalizer.isGeneric("HY-VEE LINCOLN"))
    }
}
