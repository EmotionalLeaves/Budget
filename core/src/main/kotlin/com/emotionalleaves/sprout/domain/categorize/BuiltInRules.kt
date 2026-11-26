package com.emotionalleaves.sprout.domain.categorize

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as C

/**
 * A built-in rule matches when ALL its patterns appear as whole words in the description.
 * Ties are broken by [tier] first (a brand beats a generic word like "GRILL"; money-app rules beat both,
 * because "ZELLE TO ROSS" is a person, not a store), then by specificity (total pattern length):
 * "TARGET"+"GROCERY" beats "TARGET", and "UBER EATS" beats "UBER".
 */
data class BuiltInRule(
    val patterns: List<String>,
    val categoryKey: String,
    val tier: Int = TIER_BRAND,
    /** Position in the list: the earlier rule wins an exact tie, so results never depend on hash order. */
    val order: Int = 0,
) {
    val specificity: Int = patterns.sumOf { it.length }
    private val regexes = patterns.map { p ->
        Regex("(?<![A-Z0-9])" + Regex.escape(p) + "(?![A-Z0-9])")
    }

    fun matches(haystack: String): Boolean = regexes.all { it.containsMatchIn(haystack) }

    /** True when this rule should win over [other]. */
    fun beats(other: BuiltInRule?): Boolean =
        other == null || tier > other.tier || (tier == other.tier &&
            (specificity > other.specificity || (specificity == other.specificity && order < other.order)))

    companion object {
        /** A generic word ("PIZZA", "PHARMACY"); only used for money going out. */
        const val TIER_GENERIC = 0
        const val TIER_BRAND = 1
        /** Card payments and money apps: always win, because what follows them is usually a person's name. */
        const val TIER_PRIORITY = 2
    }
}

object BuiltInRules {

    private val CARD_ISSUERS = listOf(
        "CAPITAL ONE", "CHASE", "DISCOVER", "AMEX", "AMERICAN EXPRESS", "CITI", "CITICARD", "SYNCHRONY",
        "BARCLAYCARD", "BARCLAYS", "WELLS FARGO CARD", "BANK OF AMERICA", "US BANK", "CREDIT ONE", "APPLECARD",
        "APPLE CARD", "GS BANK", "CARD SERVICES", "CREDIT CARD", "CRD",
    )

    val rules: List<BuiltInRule> = buildList {
        val seen = HashSet<String>()
        fun add(category: String, pattern: String, tier: Int) {
            val p = haystack(pattern)
            if (p.isNotBlank() && seen.add(p)) add(BuiltInRule(listOf(p), category, tier))
        }
        for ((category, list) in MerchantCatalog.merchants) {
            val tier = if (category == C.P2P) BuiltInRule.TIER_PRIORITY else BuiltInRule.TIER_BRAND
            list.forEach { add(category, it, tier) }
        }
        for ((category, list) in MerchantCatalog.keywords) list.forEach { add(category, it, BuiltInRule.TIER_GENERIC) }
        add(BuiltInRule(listOf("TARGET", "GROCERY"), C.GROCERIES))
        add(BuiltInRule(listOf("WALMART", "GROCERY"), C.GROCERIES))
        for (issuer in CARD_ISSUERS) {
            add(BuiltInRule(listOf(issuer, "AUTOPAY"), C.TRANSFERS, BuiltInRule.TIER_PRIORITY))
            add(BuiltInRule(listOf(issuer, "AUTO PAY"), C.TRANSFERS, BuiltInRule.TIER_PRIORITY))
            add(BuiltInRule(listOf(issuer, "PAYMENT"), C.TRANSFERS, BuiltInRule.TIER_PRIORITY))
            add(BuiltInRule(listOf(issuer, "EPAY"), C.TRANSFERS, BuiltInRule.TIER_PRIORITY))
        }
    }.mapIndexed { i, r -> r.copy(order = i) }

    /** How many places and words Sprout recognizes out of the box (shown in Settings). */
    val size: Int get() = rules.size

    private val TOKEN_SPLIT = Regex("[^A-Z0-9&+]+")
    private val PLAIN_SPLIT = Regex("[^A-Z0-9]+")

    /**
     * Rules indexed by the first word of their first pattern, so matching a transaction only checks the handful
     * of rules that could possibly match instead of all ~2,000. Keeps re-categorizing years of history fast.
     */
    private val index: Map<String, List<BuiltInRule>> by lazy {
        rules.groupBy { r -> r.patterns.first().split(TOKEN_SPLIT).first { it.isNotEmpty() } }
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

    /**
     * Best matching rule, or null. [amountMinor] < 0 is money out; generic words are only trusted then
     * (null = unknown direction, treated as money out).
     */
    fun match(description: String, merchantKey: String, amountMinor: Long? = null): BuiltInRule? {
        val hay = haystack(description) + " | " + haystack(merchantKey)
        val allowGeneric = amountMinor == null || amountMinor <= 0
        val tokens = HashSet<String>()
        hay.split(TOKEN_SPLIT).filterTo(tokens) { it.isNotEmpty() }
        hay.split(PLAIN_SPLIT).filterTo(tokens) { it.isNotEmpty() }
        var best: BuiltInRule? = null
        for (t in tokens) {
            val candidates = index[t] ?: continue
            for (rule in candidates) {
                if (!allowGeneric && rule.tier == BuiltInRule.TIER_GENERIC) continue
                if (rule.beats(best) && rule.matches(hay)) best = rule
            }
        }
        // "TST*" (Toast) is a restaurant point-of-sale prefix: stronger than any generic word ("TST* MOD MARKET").
        if ((best == null || best.tier == BuiltInRule.TIER_GENERIC) && allowGeneric && description.uppercase().trimStart().let { it.startsWith("TST*") || it.startsWith("TST *") }) {
            return BuiltInRule(listOf("TST*"), C.EATING_OUT, BuiltInRule.TIER_GENERIC)
        }
        return best
    }
}
