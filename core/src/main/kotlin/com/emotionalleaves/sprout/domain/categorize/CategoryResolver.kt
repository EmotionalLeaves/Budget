package com.emotionalleaves.sprout.domain.categorize

enum class RuleType { EXACT, CONTAINS }

/** A rule the user made. EXACT matches a merchant key; CONTAINS matches a keyword anywhere in the description. */
data class UserRule(
    val id: Long,
    val type: RuleType,
    val pattern: String,
    val categoryId: Long,
    /** Only for transactions of at least / at most this size (cents, either sign); null = any amount. */
    val minAbsMinor: Long? = null,
    val maxAbsMinor: Long? = null,
) {
    fun fits(amountMinor: Long): Boolean {
        val a = kotlin.math.abs(amountMinor)
        return (minAbsMinor == null || a >= minAbsMinor) && (maxAbsMinor == null || a <= maxAbsMinor)
    }
}

/** A tag rule: every transaction whose description or merchant contains [pattern] gets the tag. */
data class TagRule(val id: Long, val pattern: String, val tagId: Long)

object TagRules {
    /** The tags that apply to a transaction. */
    fun match(rules: List<TagRule>, description: String, merchantKey: String): Set<Long> {
        if (rules.isEmpty()) return emptySet()
        val hay = BuiltInRules.haystack(description) + " | " + BuiltInRules.haystack(merchantKey)
        return rules.filter { r ->
            val needle = BuiltInRules.haystack(r.pattern)
            needle.isNotBlank() && Regex("(?<![A-Z0-9])" + Regex.escape(needle) + "(?![A-Z0-9])").containsMatchIn(hay)
        }.map { it.tagId }.toSet()
    }
}

data class Resolution(val categoryId: Long?, val source: CategorySource)

/**
 * Picks a category for a transaction. First match wins:
 * 1. the user's EXACT merchant rule, 2. the user's CONTAINS rules (longest keyword first),
 * 3. what you've (nearly) always picked for this exact merchant ([LearnedCategorizer.merchantGuess]),
 * 4. built-in brands, 5. a place the list doesn't know that you've categorized even once,
 * 5b. a confident guess from words you've categorized before,
 * 6. built-in generic words ("PIZZA", "PHARMACY"; money out only),
 * 7. fallback: money in -> Income, money out -> Uncategorized.
 */
class CategoryResolver(
    userRules: List<UserRule>,
    private val categoryIdByKey: Map<String, Long>,
    private val learned: LearnedCategorizer = LearnedCategorizer.EMPTY,
) {
    private val exact: Map<String, UserRule> =
        userRules.filter { it.type == RuleType.EXACT }.associateBy { it.pattern.uppercase() }

    private val contains: List<Pair<Regex, UserRule>> = userRules
        .filter { it.type == RuleType.CONTAINS && it.pattern.isNotBlank() }
        // Rules with an amount condition are more specific, so they're tried first.
        .sortedWith(compareByDescending<UserRule> { it.minAbsMinor != null || it.maxAbsMinor != null }
            .thenByDescending { it.pattern.length }.thenByDescending { it.id })
        .map { rule ->
            val needle = BuiltInRules.haystack(rule.pattern)
            Regex("(?<![A-Z0-9])" + Regex.escape(needle) + "(?![A-Z0-9])") to rule
        }

    fun resolve(description: String, merchantKey: String, amountMinor: Long): Resolution {
        exact[merchantKey.uppercase()]?.takeIf { it.fits(amountMinor) }?.let { return Resolution(it.categoryId, CategorySource.MERCHANT_RULE) }

        if (contains.isNotEmpty()) {
            val hay = BuiltInRules.haystack(description) + " | " + BuiltInRules.haystack(merchantKey)
            contains.firstOrNull { (regex, rule) -> rule.fits(amountMinor) && regex.containsMatchIn(hay) }
                ?.let { return Resolution(it.second.categoryId, CategorySource.MERCHANT_RULE) }
        }

        learned.merchantGuess(merchantKey)?.let { return Resolution(it.categoryId, CategorySource.LEARNED) }

        val builtIn = BuiltInRules.match(description, merchantKey, amountMinor)
        val builtInId = builtIn?.let { categoryIdByKey[it.categoryKey] }
        if (builtInId != null && builtIn.tier != BuiltInRule.TIER_GENERIC) return Resolution(builtInId, CategorySource.BUILT_IN)

        learned.merchantGuess(merchantKey, minExamples = 1)?.let { return Resolution(it.categoryId, CategorySource.LEARNED) }
        learned.wordGuess(description, merchantKey)?.let { return Resolution(it.categoryId, CategorySource.LEARNED) }

        if (builtInId != null) return Resolution(builtInId, CategorySource.BUILT_IN)

        val fallbackKey = if (amountMinor > 0) DefaultCategories.INCOME else DefaultCategories.UNCATEGORIZED
        return Resolution(categoryIdByKey[fallbackKey], CategorySource.NONE)
    }

    /** Convenience for tests and previews: the built-in or fallback category key for a description. */
    companion object {
        fun builtInKey(description: String, amountMinor: Long): String {
            val key = MerchantNormalizer.normalize(description)
            return BuiltInRules.match(description, key, amountMinor)?.categoryKey
                ?: if (amountMinor > 0) DefaultCategories.INCOME else DefaultCategories.UNCATEGORIZED
        }
    }
}
