package com.emotionalleaves.sprout.data.csv

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as K

/**
 * Maps a bank's own category text from a CSV (Chase "Food & Drink", Capital One "Gas/Automotive") to a Sprout
 * category. Your category names match first (so a category you made, like "Pets", picks up "Pets"), then common
 * bank names. Unknown text returns null and the usual rules decide.
 */
object CsvCategories {
    private val ALIASES: Map<String, String> = buildMap {
        fun alias(key: String, vararg names: String) { for (n in names) this[n] = key }
        alias(K.EATING_OUT, "food and drink", "restaurants", "restaurant", "dining", "food and dining", "fast food", "coffee shops")
        alias(K.GROCERIES, "groceries", "grocery", "supermarkets", "supermarket")
        alias(K.GAS, "gas", "gas automotive", "gasoline", "fuel", "gas stations", "gas and fuel")
        alias(K.SHOPPING, "shopping", "merchandise", "general merchandise", "online shopping")
        alias(K.ENTERTAINMENT, "entertainment")
        alias(K.TRANSPORT, "travel", "transportation", "auto", "automotive", "auto and transport", "parking", "rideshare")
        alias(K.UTILITIES, "bills and utilities", "utilities")
        alias(K.PHONE, "phone", "internet", "mobile phone", "phone and internet")
        alias(K.HEALTH, "health and wellness", "health", "medical", "healthcare", "pharmacy")
        alias(K.PERSONAL_CARE, "personal", "personal care")
        alias(K.GIFTS, "gifts and donations", "gifts", "charity", "donations")
        alias(K.FEES, "fees and adjustments", "fees", "interest", "bank fees", "fees and charges")
        alias(K.TRANSFERS, "payment", "payments", "payment credit", "payments and credits", "transfer", "transfers", "credit card payment")
        alias(K.INCOME, "income", "paycheck", "salary")
        alias(K.RENT, "home", "housing", "rent", "mortgage", "home improvement")
        alias(K.INSURANCE, "insurance")
        alias(K.SUBSCRIPTIONS, "subscriptions", "subscription")
        alias(K.HOBBIES, "hobbies")
        alias(K.P2P, "person to person")
    }

    /** "Food & Drink" → "food and drink"; "Gas/Automotive" → "gas automotive". */
    fun norm(text: String): String =
        text.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    /**
     * The category id for [bankCategory], or null. [byName] is every category's id by name; [idByKey] the
     * built-in categories' ids by key.
     */
    fun match(bankCategory: String?, byName: Map<String, Long>, idByKey: Map<String, Long>): Long? {
        if (bankCategory.isNullOrBlank()) return null
        val n = norm(bankCategory)
        if (n.isEmpty()) return null
        byName.entries.firstOrNull { norm(it.key) == n }?.let { return it.value }
        return ALIASES[n]?.let { idByKey[it] }
    }
}
