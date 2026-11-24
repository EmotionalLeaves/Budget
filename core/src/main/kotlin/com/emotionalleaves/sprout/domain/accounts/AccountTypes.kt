package com.emotionalleaves.sprout.domain.accounts

enum class AccountType { CHECKING, SAVINGS, CREDIT, LOAN, INVESTMENT, CASH, OTHER }

object AccountTypes {
    /**
     * Guesses an account type from its name and balance sign. The user can always change it.
     * Credit card balances from SimpleFIN are usually negative, so a negative balance with no other clue is CREDIT.
     */
    fun infer(name: String, balanceMinor: Long): AccountType {
        val n = " " + name.uppercase().replace(Regex("[^A-Z0-9]+"), " ") + " "
        fun has(vararg words: String) = words.any { n.contains(" $it ") }
        return when {
            has("CREDIT CARD", "VISA", "MASTERCARD", "AMEX", "DISCOVER", "CARD", "REWARDS", "SIGNATURE", "PLATINUM") -> AccountType.CREDIT
            has("LOAN", "MORTGAGE", "AUTO LOAN", "HELOC", "LINE OF CREDIT", "STUDENT") -> AccountType.LOAN
            has("BROKERAGE", "INVESTMENT", "INVESTING", "IRA", "ROTH", "401K", "401 K", "403B", "HSA", "TRUST") -> AccountType.INVESTMENT
            has("SAVINGS", "SAVING", "SHARE SAVINGS", "MONEY MARKET", "CD", "CERTIFICATE") -> AccountType.SAVINGS
            has("CHECKING", "CHK", "SHARE DRAFT", "SPENDING") -> AccountType.CHECKING
            has("CASH") -> AccountType.CASH
            balanceMinor < 0 -> AccountType.CREDIT
            else -> AccountType.CHECKING
        }
    }

    /** "Cash" side of the dashboard total line. */
    fun isCashLike(type: AccountType) = type == AccountType.CHECKING || type == AccountType.SAVINGS || type == AccountType.CASH

    /** Custom currencies are URLs; they can't be added to USD totals. */
    fun isUsd(currency: String) = currency.equals("USD", ignoreCase = true)

    /** Display code for a currency; URL-based custom currencies show as "?". */
    fun currencyLabel(currency: String) = if (currency.contains("://")) "?" else currency.uppercase()
}
