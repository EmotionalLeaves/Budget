package com.emotionalleaves.sprout.domain.categorize

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as C
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryResolverTest {

    private val ids: Map<String, Long> = C.all.mapIndexed { i, c -> c.key to (i + 1).toLong() }.toMap()
    private fun idOf(key: String) = ids.getValue(key)

    private fun cat(description: String, amountMinor: Long = -1000, rules: List<UserRule> = emptyList()): Resolution {
        val resolver = CategoryResolver(rules, ids)
        return resolver.resolve(description, MerchantNormalizer.normalize(description), amountMinor)
    }

    private fun assertCat(key: String, description: String, amountMinor: Long = -1000) =
        assertEquals(description, idOf(key), cat(description, amountMinor).categoryId)

    // Groceries
    @Test fun hyVee() = assertCat(C.GROCERIES, "POS DEBIT HY-VEE #1234 LINCOLN NE")
    @Test fun kroger() = assertCat(C.GROCERIES, "KROGER #0412 OMAHA NE")
    @Test fun aldi() = assertCat(C.GROCERIES, "ALDI 72031 LINCOLN NE")
    @Test fun costco() = assertCat(C.GROCERIES, "COSTCO WHSE #1105")
    @Test fun samsClub() = assertCat(C.GROCERIES, "SAM'S CLUB #8213")
    @Test fun traderJoes() = assertCat(C.GROCERIES, "TRADER JOE'S #552 QPS")
    @Test fun superSaver() = assertCat(C.GROCERIES, "SUPER SAVER #12 LINCOLN NE")
    @Test fun walmartGrocery() = assertCat(C.GROCERIES, "WALMART GROCERY 800-966-6546 AR")
    @Test fun targetGroceryGoesToGroceries() = assertCat(C.GROCERIES, "TARGET GROCERY 00012345")
    @Test fun targetAloneIsShopping() = assertCat(C.SHOPPING, "TARGET 00012345 LINCOLN NE")

    // Gas
    @Test fun shell() = assertCat(C.GAS, "SHELL OIL 57444123 LINCOLN NE")
    @Test fun caseys() = assertCat(C.GAS, "CASEYS #3021 LINCOLN NE")
    @Test fun quikTrip() = assertCat(C.GAS, "QUIKTRIP 0123 OMAHA NE")
    @Test fun kwikShop() = assertCat(C.GAS, "KWIK SHOP #741")
    @Test fun phillips66() = assertCat(C.GAS, "PHILLIPS 66 - 1234 OMAHA")
    @Test fun bp() = assertCat(C.GAS, "BP#9087653 SPEEDY MART")

    // Eating out
    @Test fun mcdonalds() = assertCat(C.EATING_OUT, "MCDONALD'S F12345 LINCOLN NE")
    @Test fun starbucks() = assertCat(C.EATING_OUT, "STARBUCKS STORE 12345")
    @Test fun runza() = assertCat(C.EATING_OUT, "RUNZA #44 LINCOLN NE")
    @Test fun doordash() = assertCat(C.EATING_OUT, "DD *DOORDASH CHIPOTLE")
    @Test fun uberEatsIsEatingOutNotTransport() = assertCat(C.EATING_OUT, "UBER *EATS PENDING")
    @Test fun toastPrefixIsEatingOut() = assertCat(C.EATING_OUT, "TST* MOD MARKET 0098")
    @Test fun cafeKeyword() = assertCat(C.EATING_OUT, "SQ *BLUE MOON CAFE")
    @Test fun pizzaKeyword() = assertCat(C.EATING_OUT, "LITTLE ITALY PIZZA")

    // Subscriptions
    @Test fun netflix() = assertCat(C.SUBSCRIPTIONS, "NETFLIX.COM 866-579-7172 CA")
    @Test fun spotify() = assertCat(C.SUBSCRIPTIONS, "SPOTIFY USA P1234ABCD")
    @Test fun appleBill() = assertCat(C.SUBSCRIPTIONS, "APPLE.COM/BILL 866-712-7753 CA")
    @Test fun amazonPrimeIsSubscription() = assertCat(C.SUBSCRIPTIONS, "AMAZON PRIME*1A2B3C4D5")
    @Test fun anthropic() = assertCat(C.SUBSCRIPTIONS, "ANTHROPIC SAN FRANCISCO CA")
    @Test fun youtubePremium() = assertCat(C.SUBSCRIPTIONS, "GOOGLE *YOUTUBE PREMIUM")
    @Test fun amazonOrderIsShopping() = assertCat(C.SHOPPING, "AMAZON.COM*AB12CD SEATTLE WA")

    // Housing, utilities, phone
    @Test fun propertyMgmt() = assertCat(C.RENT, "ACH SUNRISE PROPERTY MGMT WEB PMT")
    @Test fun blackHills() = assertCat(C.UTILITIES, "BLACK HILLS ENERGY 800-123-4567")
    @Test fun tMobile() = assertCat(C.PHONE, "T-MOBILE*AUTO PAY 800-937-8997")
    @Test fun allo() = assertCat(C.PHONE, "ALLO COMMUNICATIONS LINCOLN NE")
    @Test fun atAndT() = assertCat(C.PHONE, "AT&T*BILL PAYMENT 800-331-0500 TX")

    // Transport and hobbies
    @Test fun uberRideIsTransport() = assertCat(C.TRANSPORT, "UBER *TRIP HELP.UBER.COM")
    @Test fun lyft() = assertCat(C.TRANSPORT, "LYFT *RIDE WED 7PM")
    @Test fun parking() = assertCat(C.TRANSPORT, "CITY OF LINCOLN PARKING")
    @Test fun michaels() = assertCat(C.HOBBIES, "MICHAELS STORES 9876")
    @Test fun hobbyLobby() = assertCat(C.HOBBIES, "HOBBY LOBBY #123")
    @Test fun blick() = assertCat(C.HOBBIES, "BLICK ART MATERIALS 00123")
    @Test fun jerrysArtarama() = assertCat(C.HOBBIES, "JERRY'S ARTARAMA ONLINE")

    // Income, transfers, P2P
    @Test fun payroll() = assertCat(C.INCOME, "ACME CORP PAYROLL PPD ID: 9876543210", 185000)
    @Test fun directDeposit() = assertCat(C.INCOME, "DIRECT DEP UNIV OF NEBRASKA", 120000)
    @Test fun onlineTransfer() = assertCat(C.TRANSFERS, "ONLINE TRANSFER TO SAVINGS XXXXXX1234")
    @Test fun paymentThankYou() = assertCat(C.TRANSFERS, "PAYMENT THANK YOU", 30000)
    @Test fun cardAutopayIsTransfer() = assertCat(C.TRANSFERS, "CAPITAL ONE AUTOPAY PYMT")
    @Test fun utilityAutopayIsNotTransfer() = assertCat(C.UTILITIES, "BLACK HILLS ENERGY AUTOPAY")
    @Test fun venmo() = assertCat(C.P2P, "VENMO PAYMENT 1023456789")
    @Test fun zelle() = assertCat(C.P2P, "ZELLE TO SAM R ON 11/02")
    @Test fun cashApp() = assertCat(C.P2P, "CASH APP*JANE DOE")

    // Fallbacks and sources
    @Test fun unknownOutflowIsUncategorized() {
        val r = cat("ZQXV HOLDINGS 12345", -2500)
        assertEquals(idOf(C.UNCATEGORIZED), r.categoryId)
        assertEquals(CategorySource.NONE, r.source)
    }

    @Test fun unknownInflowIsIncome() {
        val r = cat("ZQXV HOLDINGS 12345", 2500)
        assertEquals(idOf(C.INCOME), r.categoryId)
        assertEquals(CategorySource.NONE, r.source)
    }

    @Test fun builtInSourceIsBuiltIn() = assertEquals(CategorySource.BUILT_IN, cat("NETFLIX.COM").source)

    @Test fun refundFromShopKeepsShopCategory() = assertCat(C.SHOPPING, "AMAZON.COM*REFUND", 2599)

    // Precedence
    @Test fun userExactRuleBeatsBuiltIn() {
        val rule = UserRule(1, RuleType.EXACT, "STARBUCKS STORE", idOf(C.GIFTS))
        val r = cat("STARBUCKS STORE 12345", rules = listOf(rule))
        assertEquals(idOf(C.GIFTS), r.categoryId)
        assertEquals(CategorySource.MERCHANT_RULE, r.source)
    }

    @Test fun userContainsRuleBeatsBuiltIn() {
        val rule = UserRule(1, RuleType.CONTAINS, "shell", idOf(C.TRANSPORT))
        assertEquals(idOf(C.TRANSPORT), cat("SHELL OIL 57444123", rules = listOf(rule)).categoryId)
    }

    @Test fun exactRuleBeatsContainsRule() {
        val rules = listOf(
            UserRule(1, RuleType.CONTAINS, "SHELL", idOf(C.TRANSPORT)),
            UserRule(2, RuleType.EXACT, "SHELL OIL", idOf(C.OTHER)),
        )
        assertEquals(idOf(C.OTHER), cat("SHELL OIL 57444123", rules = rules).categoryId)
    }

    @Test fun longerContainsKeywordWins() {
        val rules = listOf(
            UserRule(1, RuleType.CONTAINS, "MARKET", idOf(C.GROCERIES)),
            UserRule(2, RuleType.CONTAINS, "FARMERS MARKET", idOf(C.HOBBIES)),
        )
        assertEquals(idOf(C.HOBBIES), cat("HAYMARKET FARMERS MARKET", rules = rules).categoryId)
    }

    @Test fun containsRuleMatchesWholeWordsOnly() {
        val rule = UserRule(1, RuleType.CONTAINS, "ART", idOf(C.HOBBIES))
        assertEquals(idOf(C.UNCATEGORIZED), cat("QXWALMARTZ PARTY", rules = listOf(rule)).categoryId)
    }

    @Test fun exactRuleDoesNotMatchOtherMerchants() {
        val rule = UserRule(1, RuleType.EXACT, "STARBUCKS STORE", idOf(C.GIFTS))
        assertEquals(idOf(C.EATING_OUT), cat("CHIPOTLE 1234", rules = listOf(rule)).categoryId)
    }

    @Test fun learnedRuleAppliesToNextTransactionFromMerchant() {
        // A correction creates an EXACT rule for the normalized merchant key; a later store number still matches.
        val key = MerchantNormalizer.normalize("CASEYS #3021 LINCOLN NE")
        val rule = UserRule(7, RuleType.EXACT, key, idOf(C.EATING_OUT))
        assertEquals(idOf(C.EATING_OUT), cat("CASEYS #2280 LINCOLN NE", rules = listOf(rule)).categoryId)
    }
}
