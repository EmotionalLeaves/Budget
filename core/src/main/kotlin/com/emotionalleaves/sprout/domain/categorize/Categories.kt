package com.emotionalleaves.sprout.domain.categorize

enum class CategoryKind { INCOME, EXPENSE, TRANSFER }

/**
 * Where a transaction's category came from. USER and MERCHANT_RULE are never overwritten by sync.
 * NONE = fallback (Income/Uncategorized), BUILT_IN = a built-in rule, IMPORT = set by a CSV import,
 * LEARNED = guessed on the phone from how you've categorized similar purchases (re-guessed as you teach it more).
 * Stored by name in a TEXT column, so adding a value needs no schema change.
 */
enum class CategorySource { NONE, BUILT_IN, MERCHANT_RULE, USER, IMPORT, LEARNED }

data class DefaultCategory(val key: String, val name: String, val kind: CategoryKind, val colorArgb: Long)

/** Seeded on first run. Keys are stable forever: rules and tests refer to them. */
object DefaultCategories {
    const val INCOME = "income"
    const val RENT = "rent_housing"
    const val UTILITIES = "utilities"
    const val PHONE = "phone_internet"
    const val GROCERIES = "groceries"
    const val EATING_OUT = "eating_out"
    const val GAS = "gas_fuel"
    const val TRANSPORT = "transportation"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SHOPPING = "shopping"
    const val HOBBIES = "hobbies_art"
    const val HEALTH = "health"
    const val PERSONAL_CARE = "personal_care"
    const val ENTERTAINMENT = "entertainment"
    const val INSURANCE = "insurance"
    const val FEES = "fees_interest"
    const val P2P = "person_to_person"
    const val GIFTS = "gifts_donations"
    const val OTHER = "other"
    const val UNCATEGORIZED = "uncategorized"
    const val TRANSFERS = "transfers"
    /** Added in 2.21.0; existing installs get them once on update (see CategoryRepository.upgradeCatalog). */
    const val TRAVEL = "travel"
    const val PETS = "pets"

    /** Keys added after the first release, in the order they arrived. */
    val addedLater: List<String> = listOf(TRAVEL, PETS)

    val all: List<DefaultCategory> = listOf(
        DefaultCategory(INCOME, "Income", CategoryKind.INCOME, 0xFF2E7D32),
        DefaultCategory(RENT, "Rent & Housing", CategoryKind.EXPENSE, 0xFF6D4C41),
        DefaultCategory(UTILITIES, "Utilities", CategoryKind.EXPENSE, 0xFFF9A825),
        DefaultCategory(PHONE, "Phone & Internet", CategoryKind.EXPENSE, 0xFF0277BD),
        DefaultCategory(GROCERIES, "Groceries", CategoryKind.EXPENSE, 0xFF558B2F),
        DefaultCategory(EATING_OUT, "Eating Out", CategoryKind.EXPENSE, 0xFFEF6C00),
        DefaultCategory(GAS, "Gas & Fuel", CategoryKind.EXPENSE, 0xFFC62828),
        DefaultCategory(TRANSPORT, "Transportation", CategoryKind.EXPENSE, 0xFF5E35B1),
        DefaultCategory(SUBSCRIPTIONS, "Subscriptions", CategoryKind.EXPENSE, 0xFFAD1457),
        DefaultCategory(SHOPPING, "Shopping", CategoryKind.EXPENSE, 0xFF00838F),
        DefaultCategory(HOBBIES, "Hobbies & Art Supplies", CategoryKind.EXPENSE, 0xFF8E24AA),
        DefaultCategory(HEALTH, "Health", CategoryKind.EXPENSE, 0xFFD81B60),
        DefaultCategory(PERSONAL_CARE, "Personal Care", CategoryKind.EXPENSE, 0xFFEC407A),
        DefaultCategory(ENTERTAINMENT, "Entertainment", CategoryKind.EXPENSE, 0xFF3949AB),
        DefaultCategory(INSURANCE, "Insurance", CategoryKind.EXPENSE, 0xFF546E7A),
        DefaultCategory(FEES, "Fees & Interest", CategoryKind.EXPENSE, 0xFFB71C1C),
        DefaultCategory(P2P, "Person-to-Person", CategoryKind.EXPENSE, 0xFF00695C),
        DefaultCategory(GIFTS, "Gifts & Donations", CategoryKind.EXPENSE, 0xFFF06292),
        DefaultCategory(TRAVEL, "Travel", CategoryKind.EXPENSE, 0xFF0097A7),
        DefaultCategory(PETS, "Pets", CategoryKind.EXPENSE, 0xFF8D6E63),
        DefaultCategory(OTHER, "Other", CategoryKind.EXPENSE, 0xFF757575),
        DefaultCategory(UNCATEGORIZED, "Uncategorized", CategoryKind.EXPENSE, 0xFF9E9E9E),
        DefaultCategory(TRANSFERS, "Transfers", CategoryKind.TRANSFER, 0xFF78909C),
    )
}
