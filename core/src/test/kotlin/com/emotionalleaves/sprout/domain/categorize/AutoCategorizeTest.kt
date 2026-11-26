package com.emotionalleaves.sprout.domain.categorize

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCategorizeTest {

    private fun key(desc: String, amount: Long = -1234): String = CategoryResolver.builtInKey(desc, amount)

    /** Descriptions shaped like real card and bank feeds, Lincoln-heavy. */
    private val corpus = listOf(
        "SQ *SCOOTERS COFFEE LINCOLN NE" to C.EATING_OUT,
        "RUNZA #12 LINCOLN NE" to C.EATING_OUT,
        "TST* THE MILL - TELEGRAPH" to C.EATING_OUT,
        "VALENTINOS 27TH ST" to C.EATING_OUT,
        "LAZLOS BREWERY & GRILL" to C.EATING_OUT,
        "DD *DOORDASH CHIPOTLE" to C.EATING_OUT,
        "RAISING CANES 0612" to C.EATING_OUT,
        "CULVERS OF LINCOLN" to C.EATING_OUT,
        "CHICK-FIL-A #03341" to C.EATING_OUT,
        "MAIN STREET TAQUERIA" to C.EATING_OUT,
        "LITTLE SAIGON PHO" to C.EATING_OUT,
        "SUNRISE DONUTS & BAKERY" to C.EATING_OUT,
        "HY-VEE LINCOLN 1234" to C.GROCERIES,
        "SUPER SAVER #3" to C.GROCERIES,
        "ALDI 72045" to C.GROCERIES,
        "TRADER JOE'S #714" to C.GROCERIES,
        "WHOLEFDS LIN 10250" to C.GROCERIES,
        "TARGET 00012 GROCERY" to C.GROCERIES,
        "CASEYS #2913 LINCOLN" to C.GAS,
        "KWIK SHOP 0712" to C.GAS,
        "QT 512 OUTSIDE" to C.GAS,
        "SAPP BROS PETRO" to C.GAS,
        "TARGET.COM *" to C.SHOPPING,
        "AMZN Mktp US*2K3LM" to C.SHOPPING,
        "SCHEELS ALL SPORTS" to C.SHOPPING,
        "GOODWILL LINCOLN" to C.SHOPPING,
        "NETFLIX.COM" to C.SUBSCRIPTIONS,
        "SPOTIFY USA" to C.SUBSCRIPTIONS,
        "OPENAI *CHATGPT SUBSCR" to C.SUBSCRIPTIONS,
        "CRUNCHYROLL" to C.SUBSCRIPTIONS,
        "LINCOLN ELECTRIC SYSTEM" to C.UTILITIES,
        "BLACK HILLS ENERGY" to C.UTILITIES,
        "CITY OF LINCOLN WATER" to C.UTILITIES,
        "ALLO COMMUNICATIONS" to C.PHONE,
        "T-MOBILE AUTO PAY" to C.PHONE,
        "STARTRAN BUS PASS" to C.TRANSPORT,
        "PARKMOBILE" to C.TRANSPORT,
        "JIFFY LUBE #1932" to C.TRANSPORT,
        "UBER *TRIP HELP.UBER.COM" to C.TRANSPORT,
        "BLICK ART MATERIALS" to C.HOBBIES,
        "MICHAELS STORES 4821" to C.HOBBIES,
        "JERRY'S ARTARAMA" to C.HOBBIES,
        "WALGREENS #5678" to C.HEALTH,
        "BRYAN HEALTH PATIENT PAY" to C.HEALTH,
        "PLANET FITNESS" to C.HEALTH,
        "GREAT CLIPS #8890" to C.PERSONAL_CARE,
        "ULTA BEAUTY #712" to C.PERSONAL_CARE,
        "MARCUS THEATRES EAST PARK" to C.ENTERTAINMENT,
        "STEAMGAMES.COM 4259522985" to C.ENTERTAINMENT,
        "TICKETMASTER" to C.ENTERTAINMENT,
        "GEICO *AUTO" to C.INSURANCE,
        "STATE FARM INSURANCE" to C.INSURANCE,
        "OVERDRAFT FEE" to C.FEES,
        "INTEREST CHARGE ON PURCHASES" to C.FEES,
        "PETSMART #0412" to C.PETS,
        "CAPITAL HUMANE SOCIETY" to C.PETS,
        "HAMPTON INN OMAHA" to C.TRAVEL,
        "SOUTHWES 5262145890" to C.TRAVEL,
        "AIRBNB * HMXYZ" to C.TRAVEL,
        "VENMO PAYMENT 102938" to C.P2P,
        "ZELLE TO ROSS JORDAN" to C.P2P,
        "CAPITAL ONE AUTOPAY PYMT" to C.TRANSFERS,
        "DISCOVER E-PAYMENT 2345" to C.TRANSFERS,
        "1-800-FLOWERS.COM" to C.GIFTS,
        "SP * SOMEINDIEBRAND" to C.SHOPPING,
        "FS *SOFTWARETOOL" to C.SUBSCRIPTIONS,
    )

    @Test fun recognizesARealisticFeed() {
        val misses = corpus.filter { (d, want) -> key(d) != want }.map { (d, want) -> "$d -> ${key(d)} (want $want)" }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test fun genericWordsOnlyCountForMoneyGoingOut() {
        assertEquals(C.EATING_OUT, key("JOES GRILL", -2500))
        // A deposit mentioning a grill is far more likely a paycheck than a refund for lunch.
        assertEquals(C.INCOME, key("JOES GRILL DIRECT", 50000))
        // A brand still counts both ways: a refund lands back in the category it came from.
        assertEquals(C.SHOPPING, key("TARGET 00012 RETURN", 1999))
    }

    @Test fun brandsBeatGenericWords() {
        // "MARKET" says grocery, but Toast's prefix and the brand both say restaurant.
        assertEquals(C.EATING_OUT, key("TST* MOD MARKET 0098"))
        assertEquals(C.GAS, key("SHELL OIL 57444 FOOD MART"))
        assertEquals(C.SHOPPING, key("POTTERY BARN"))
    }

    @Test fun moneyAppsBeatTheNameAfterThem() {
        assertEquals(C.P2P, key("CASH APP*TARGET LADY"))
        assertEquals(C.P2P, key("ZELLE PAYMENT TO MARSHALLS JOHN"))
    }

    @Test fun unknownStaysUncategorized() {
        assertEquals(C.UNCATEGORIZED, key("XQZ 4471 HOLDINGS"))
    }

    @Test fun catalogIsBigAndIndexedSoItsFast() {
        assertTrue("catalog has ${BuiltInRules.size} entries", BuiltInRules.size > 1500)
        val start = System.nanoTime()
        repeat(20_000) { i -> BuiltInRules.match(corpus[i % corpus.size].first, "") }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("20k matches took ${ms}ms", ms < 4_000)
    }

    // --- learning ---------------------------------------------------------------------------------------

    private val examples = listOf(
        LearnedCategorizer.Example("SQ *BLUE MOON CAFE", "BLUE MOON CAFE", 6),
        LearnedCategorizer.Example("SQ *BLUE MOON CAFE", "BLUE MOON CAFE", 6),
        LearnedCategorizer.Example("PAYPAL *KIDS ART CLASS", "KIDS ART CLASS", 11),
        LearnedCategorizer.Example("WESTFIELD ART CLASS", "WESTFIELD ART CLASS", 11),
        LearnedCategorizer.Example("PRAIRIE ART CLASS", "PRAIRIE ART CLASS", 11),
        LearnedCategorizer.Example("ACME HOLDINGS LLC", "ACME HOLDINGS", 3),
        LearnedCategorizer.Example("ACME HOLDINGS LLC", "ACME HOLDINGS", 3),
        LearnedCategorizer.Example("ACME HOLDINGS LLC", "ACME HOLDINGS", 3),
    )

    @Test fun remembersWhatYouPickForAMerchant() {
        val l = LearnedCategorizer.train(examples)
        assertEquals(3L, l.merchantGuess("ACME HOLDINGS")?.categoryId)
        // One correction isn't a habit yet.
        assertNull(l.merchantGuess("KIDS ART CLASS"))
    }

    @Test fun splitMerchantIsNotForced() {
        val l = LearnedCategorizer.train(listOf(
            LearnedCategorizer.Example("COSTCO", "COSTCO", 5),
            LearnedCategorizer.Example("COSTCO", "COSTCO", 5),
            LearnedCategorizer.Example("COSTCO", "COSTCO", 10),
        ))
        assertNull(l.merchantGuess("COSTCO"))
    }

    @Test fun generalizesFromWords() {
        val l = LearnedCategorizer.train(examples)
        assertEquals(11L, l.wordGuess("DOWNTOWN ART CLASS", "DOWNTOWN ART CLASS")?.categoryId)
        // Nothing it has seen before: no guess, so it stays for you to decide.
        assertNull(l.wordGuess("ZZYZX 9000", "ZZYZX"))
    }

    @Test fun resolverUsesLearningAroundTheBuiltIns() {
        val ids = C.all.mapIndexed { i, c -> c.key to (i + 1L) }.toMap()
        val learned = LearnedCategorizer.train(examples + listOf(
            LearnedCategorizer.Example("WALMART 1234", "WALMART", ids.getValue(C.GROCERIES)),
            LearnedCategorizer.Example("WALMART 5678", "WALMART", ids.getValue(C.GROCERIES)),
        ))
        val r = CategoryResolver(emptyList(), ids, learned)
        // Your habit for a merchant beats the built-in guess (Walmart = Shopping)...
        assertEquals(Resolution(ids.getValue(C.GROCERIES), CategorySource.LEARNED), r.resolve("WALMART 9999", "WALMART", -5000))
        // ...an unknown place is learned from its words...
        assertEquals(Resolution(11L, CategorySource.LEARNED), r.resolve("NORTH ART CLASS", "NORTH ART CLASS", -3000))
        // ...and a known brand still uses the list.
        assertEquals(CategorySource.BUILT_IN, r.resolve("NETFLIX.COM", "NETFLIX", -1599).source)
        // A rule you made always wins.
        val withRule = CategoryResolver(listOf(UserRule(1, RuleType.EXACT, "WALMART", 99)), ids, learned)
        assertEquals(Resolution(99L, CategorySource.MERCHANT_RULE), withRule.resolve("WALMART 1", "WALMART", -100))
    }

    @Test fun bankNoiseIsIgnored() {
        val t = LearnedCategorizer.tokens("POS DEBIT PURCHASE 03/14 SQ *BLUE MOON CAFE LINCOLN NE 4521", "BLUE MOON CAFE")
        assertTrue(t.toString(), "BLUE" in t && "MOON" in t && "CAFE" in t && "BLUE_MOON" in t)
        assertTrue(t.toString(), "POS" !in t && "DEBIT" !in t && "4521" !in t && "LINCOLN" !in t)
    }
}
