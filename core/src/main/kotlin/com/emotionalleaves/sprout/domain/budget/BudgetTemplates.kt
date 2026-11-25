package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.categorize.DefaultCategories as K

/**
 * 50/30/20: half of income for needs, 30% for wants, 20% left unbudgeted for savings and debt. Each group's money
 * is shared across its categories by how much you spent there recently (equally if there's no history), then
 * rounded down to $10 so the total never goes over.
 */
object BudgetTemplates {
    val NEEDS = setOf(K.RENT, K.UTILITIES, K.PHONE, K.GROCERIES, K.GAS, K.TRANSPORT, K.HEALTH, K.INSURANCE)
    val WANTS = setOf(K.EATING_OUT, K.SUBSCRIPTIONS, K.SHOPPING, K.HOBBIES, K.PERSONAL_CARE, K.ENTERTAINMENT, K.GIFTS)

    data class Input(val categoryId: Long, val key: String?, val recentMonthlySpendMinor: Long)

    fun fiftyThirtyTwenty(monthlyIncomeMinor: Long, categories: List<Input>): Map<Long, Long> {
        if (monthlyIncomeMinor <= 0) return emptyMap()
        fun share(group: Set<String>, pot: Long): Map<Long, Long> {
            val members = categories.filter { it.key in group }
            if (members.isEmpty()) return emptyMap()
            val total = members.sumOf { maxOf(0L, it.recentMonthlySpendMinor) }
            return members.associate { m ->
                val raw = if (total > 0) pot * maxOf(0L, m.recentMonthlySpendMinor) / total else pot / members.size
                m.categoryId to (raw / 1_000) * 1_000
            }
        }
        return share(NEEDS, monthlyIncomeMinor * 50 / 100) + share(WANTS, monthlyIncomeMinor * 30 / 100)
    }
}
