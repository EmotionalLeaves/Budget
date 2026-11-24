package com.emotionalleaves.sprout.domain.categorize

enum class RuleType { EXACT, CONTAINS }

/** A rule the user made. EXACT matches a merchant key; CONTAINS matches a keyword anywhere in the description. */
data class UserRule(val id: Long, val type: RuleType, val pattern: String, val categoryId: Long)

data class Resolution(val categoryId: Long?, val source: CategorySource)

/**
 * Picks a category for a transaction. First match wins:
 * 1. the user's EXACT merchant rule, 2. the user's CONTAINS rules (longest keyword first),
 * 3. built-in rules (longest pattern wins), 4. fallback: money in -> Income, money out -> Uncategorized.
 */
class CategoryResolver(
    userRules: List<UserRule>,
    private val categoryIdByKey: Map<String, Long>,
) {
    private val exact: Map<String, UserRule> =
        userRules.filter { it.type == RuleType.EXACT }.associateBy { it.pattern.uppercase() }

    private val contains: List<Pair<Regex, UserRule>> = userRules
        .filter { it.type == RuleType.CONTAINS && it.pattern.isNotBlank() }
        .sortedWith(compareByDescending<UserRule> { it.pattern.length }.thenByDescending { it.id })
        .map { rule ->
            val needle = BuiltInRules.haystack(rule.pattern)
            Regex("(?<![A-Z0-9])" + Regex.escape(needle) + "(?![A-Z0-9])") to rule
        }

    fun resolve(description: String, merchantKey: String, amountMinor: Long): Resolution {
        exact[merchantKey.uppercase()]?.let { return Resolution(it.categoryId, CategorySource.MERCHANT_RULE) }

        if (contains.isNotEmpty()) {
            val hay = BuiltInRules.haystack(description) + " | " + BuiltInRules.haystack(merchantKey)
            contains.firstOrNull { (regex, _) -> regex.containsMatchIn(hay) }
                ?.let { return Resolution(it.second.categoryId, CategorySource.MERCHANT_RULE) }
        }

        BuiltInRules.match(description, merchantKey)?.let { rule ->
            categoryIdByKey[rule.categoryKey]?.let { return Resolution(it, CategorySource.BUILT_IN) }
        }

        val fallbackKey = if (amountMinor > 0) DefaultCategories.INCOME else DefaultCategories.UNCATEGORIZED
        return Resolution(categoryIdByKey[fallbackKey], CategorySource.NONE)
    }

    /** Convenience for tests and previews: the built-in or fallback category key for a description. */
    companion object {
        fun builtInKey(description: String, amountMinor: Long): String {
            val key = MerchantNormalizer.normalize(description)
            return BuiltInRules.match(description, key)?.categoryKey
                ?: if (amountMinor > 0) DefaultCategories.INCOME else DefaultCategories.UNCATEGORIZED
        }
    }
}
