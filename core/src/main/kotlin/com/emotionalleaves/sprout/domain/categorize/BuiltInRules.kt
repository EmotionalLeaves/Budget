package com.emotionalleaves.sprout.domain.categorize

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as C

/**
 * A built-in rule matches when ALL its patterns appear as whole words in the description.
 * Specificity (total pattern length) breaks ties: the longest match wins, so
 * "TARGET"+"GROCERY" beats "TARGET", and "UBER EATS" beats "UBER".
 */
data class BuiltInRule(val patterns: List<String>, val categoryKey: String) {
    val specificity: Int = patterns.sumOf { it.length }
    private val regexes = patterns.map { p ->
        Regex("(?<![A-Z0-9])" + Regex.escape(p) + "(?![A-Z0-9])")
    }

    fun matches(haystack: String): Boolean = regexes.all { it.containsMatchIn(haystack) }
}

object BuiltInRules {

    private fun r(category: String, vararg single: String): List<BuiltInRule> =
        single.map { BuiltInRule(listOf(it), category) }

    private fun all(category: String, vararg patterns: String) = BuiltInRule(patterns.toList(), category)

    private val CARD_ISSUERS = listOf(
        "CAPITAL ONE", "CHASE", "DISCOVER", "AMEX", "AMERICAN EXPRESS", "CITI", "CITICARD", "SYNCHRONY",
        "BARCLAYCARD", "BARCLAYS", "WELLS FARGO CARD", "BANK OF AMERICA", "US BANK", "CREDIT ONE", "APPLECARD",
        "APPLE CARD", "GS BANK", "CARD SERVICES", "CREDIT CARD", "CRD",
    )

    val rules: List<BuiltInRule> = buildList {
        // Groceries
        addAll(r(C.GROCERIES, "HY VEE", "HYVEE", "WALMART GROCERY", "WM SUPERCENTER GROCERY", "KROGER", "ALDI",
            "COSTCO", "SAMS CLUB", "SAMSCLUB", "WHOLE FOODS", "WHOLEFDS", "TRADER JOES", "TRADER JOE", "SUPER SAVER",
            "FAREWAY", "SAFEWAY", "PUBLIX", "MEIJER", "HEB", "WINCO", "SPROUTS FARMERS", "FOOD LION", "GROCERY",
            "INSTACART", "RUSS MARKET", "BAKERS"))
        add(all(C.GROCERIES, "TARGET", "GROCERY"))
        // Shopping
        addAll(r(C.SHOPPING, "TARGET", "WALMART", "WAL MART", "WM SUPERCENTER", "AMAZON", "AMZN", "BEST BUY",
            "EBAY", "ETSY", "IKEA", "KOHLS", "TJ MAXX", "TJMAXX", "MARSHALLS", "HOME DEPOT", "LOWES", "MENARDS",
            "DOLLAR GENERAL", "DOLLAR TREE", "FAMILY DOLLAR", "OLD NAVY", "SHEIN", "TEMU", "GOODWILL"))
        // Gas & Fuel
        addAll(r(C.GAS, "SHELL", "EXXON", "EXXONMOBIL", "MOBIL", "BP", "CASEYS", "CASEY S", "QUIKTRIP", "QT",
            "KWIK SHOP", "KWIK STAR", "KWIK TRIP", "SINCLAIR", "PHILLIPS 66", "CONOCO", "CHEVRON", "SPEEDWAY",
            "CENEX", "MARATHON PETRO", "VALERO", "CITGO", "SUNOCO", "ARCO", "MURPHY USA", "PILOT", "FLYING J",
            "LOVES TRAVEL", "BUCKEES", "BUC EES"))
        // Eating Out
        addAll(r(C.EATING_OUT, "MCDONALDS", "MCDONALD S", "STARBUCKS", "CHIPOTLE", "TACO BELL", "WENDYS", "WENDY S",
            "BURGER KING", "SUBWAY", "DOMINOS", "DOMINO S", "PIZZA HUT", "RUNZA", "DOORDASH", "DD DOORDASH",
            "UBER EATS", "UBEREATS", "GRUBHUB", "RESTAURANT", "CAFE", "GRILL", "PIZZA", "COFFEE", "CHICK FIL A",
            "CHICKFILA", "PANERA", "DUNKIN", "SONIC DRIVE", "ARBYS", "KFC", "POPEYES", "JIMMY JOHNS", "PANDA EXPRESS",
            "QDOBA", "CULVERS", "RAISING CANES", "DAIRY QUEEN", "APPLEBEES", "IHOP", "DENNYS", "OLIVE GARDEN",
            "BAR & GRILL", "TAQUERIA", "SUSHI", "BAKERY", "DINER", "BISTRO", "KITCHEN", "BREWING", "TAVERN"))
        // Subscriptions
        addAll(r(C.SUBSCRIPTIONS, "NETFLIX", "SPOTIFY", "HULU", "DISNEY PLUS", "DISNEYPLUS", "DISNEY+", "HBO MAX",
            "MAX COM", "PARAMOUNT", "PEACOCK", "YOUTUBE PREMIUM", "YOUTUBEPREMIUM", "GOOGLE YOUTUBE", "AMAZON PRIME",
            "AMZN PRIME", "PRIME VIDEO", "APPLE", "ICLOUD", "PATREON", "ADOBE", "MICROSOFT", "MSFT", "XBOX",
            "PLAYSTATION", "SONY PLAYSTATION", "NINTENDO", "CRUNCHYROLL", "OPENAI", "CHATGPT", "ANTHROPIC",
            "CLAUDE AI", "AUDIBLE", "SIRIUSXM", "DUOLINGO", "GOOGLE STORAGE", "GOOGLE ONE", "DROPBOX", "CANVA",
            "PROCREATE", "CLIP STUDIO"))
        // Rent & Housing
        addAll(r(C.RENT, "RENT", "APARTMENT", "APARTMENTS", "APTS", "PROPERTY MGMT", "PROPERTY MANAGEMENT",
            "LEASING", "MORTGAGE", "HOA"))
        // Utilities
        addAll(r(C.UTILITIES, "ELECTRIC", "ENERGY", "WATER", "GAS CO", "BLACK HILLS", "LINCOLN ELECTRIC",
            "OPPD", "METROPOLITAN UTILITIES", "XCEL", "EVERGY", "UTILITIES", "UTILITY", "SEWER", "WASTE MANAGEMENT"))
        // Phone & Internet
        addAll(r(C.PHONE, "VERIZON", "VZWRLSS", "T MOBILE", "TMOBILE", "AT&T", "ATT", "SPECTRUM", "CHARTER",
            "XFINITY", "COMCAST", "ALLO", "MINT MOBILE", "CRICKET", "VISIBLE", "GOOGLE FI", "BOOST MOBILE",
            "METRO BY T MOBILE"))
        // Transportation
        addAll(r(C.TRANSPORT, "UBER", "LYFT", "PARKING", "TRANSIT", "STARTRAN", "AMTRAK", "GREYHOUND", "PARKMOBILE",
            "TOLL", "DMV"))
        // Hobbies & Art Supplies
        addAll(r(C.HOBBIES, "MICHAELS", "HOBBY LOBBY", "BLICK", "DICK BLICK", "JERRYS ARTARAMA", "JERRY S ARTARAMA",
            "JOANN", "JO ANN", "GAMES WORKSHOP", "ART SUPPLY", "ART SUPPLIES"))
        // Health
        addAll(r(C.HEALTH, "PHARMACY", "CVS", "WALGREENS", "HOSPITAL", "CLINIC", "DENTAL", "DENTIST", "MEDICAL",
            "OPTOMETRY", "URGENT CARE", "PLANET FITNESS", "GYM"))
        // Personal Care
        addAll(r(C.PERSONAL_CARE, "SALON", "BARBER", "SUPERCUTS", "GREAT CLIPS", "ULTA", "SEPHORA", "SPA"))
        // Entertainment
        addAll(r(C.ENTERTAINMENT, "AMC", "CINEMA", "THEATRE", "THEATER", "STEAM", "STEAMPOWERED", "TICKETMASTER",
            "EVENTBRITE", "BOWLING", "MARCUS THEATRES"))
        // Insurance
        addAll(r(C.INSURANCE, "INSURANCE", "GEICO", "PROGRESSIVE", "STATE FARM", "ALLSTATE", "LIBERTY MUTUAL",
            "FARMERS INS", "LEMONADE"))
        // Fees & Interest
        addAll(r(C.FEES, "INTEREST CHARGE", "LATE FEE", "OVERDRAFT", "SERVICE FEE", "MONTHLY FEE", "ATM FEE",
            "FOREIGN TRANSACTION FEE", "NSF FEE", "FINANCE CHARGE"))
        // Gifts & Donations
        addAll(r(C.GIFTS, "DONATION", "CHARITY", "GOFUNDME", "RED CROSS", "UNITED WAY"))
        // Income
        addAll(r(C.INCOME, "PAYROLL", "DIRECT DEP", "DIR DEP", "DIRECTDEP", "SALARY", "DIRECT DEPOSIT", "INTEREST PAID",
            "INTEREST PAYMENT", "IRS TREAS", "TAX REF"))
        // Transfers
        addAll(r(C.TRANSFERS, "TRANSFER", "XFER", "ONLINE PAYMENT", "PAYMENT THANK YOU", "PAYMENT THANKYOU",
            "MOBILE PAYMENT", "INTERNET PAYMENT", "ONLINE TRANSFER", "SAVINGS TRANSFER"))
        for (issuer in CARD_ISSUERS) add(all(C.TRANSFERS, issuer, "AUTOPAY"))
        for (issuer in CARD_ISSUERS) add(all(C.TRANSFERS, issuer, "AUTO PAY"))
        // Person-to-Person
        addAll(r(C.P2P, "VENMO", "ZELLE", "CASH APP", "CASHAPP", "SQUARE CASH", "SQC", "PAYPAL INST XFER"))
    }

    /**
     * The text rules are matched against: uppercase, apostrophes dropped, separators turned into spaces.
     * Keeps "&", "." and "/" so AT&T, AMAZON.COM and APPLE.COM/BILL still read naturally.
     */
    fun haystack(description: String): String =
        description.uppercase()
            .replace("'", "").replace("’", "")
            .replace(Regex("""[*#,_\-:;()\[\]"!?]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Longest matching rule wins; null if nothing matches. */
    fun match(description: String, merchantKey: String): BuiltInRule? {
        val hay = haystack(description) + " | " + haystack(merchantKey)
        var best: BuiltInRule? = null
        for (rule in rules) {
            if ((best == null || rule.specificity > best.specificity) && rule.matches(hay)) best = rule
        }
        // "TST*" (Toast) is a restaurant point-of-sale prefix.
        if (best == null && description.uppercase().trimStart().let { it.startsWith("TST*") || it.startsWith("TST *") }) {
            return BuiltInRule(listOf("TST*"), C.EATING_OUT)
        }
        return best
    }
}
