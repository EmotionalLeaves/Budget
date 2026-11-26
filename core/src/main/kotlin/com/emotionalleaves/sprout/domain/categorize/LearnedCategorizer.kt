package com.emotionalleaves.sprout.domain.categorize

import kotlin.math.exp
import kotlin.math.ln

/**
 * Learns from the categories you've picked yourself and guesses for purchases nothing else recognizes.
 * Runs entirely on the phone: a small naive-Bayes model over the words in each description, rebuilt from
 * your own history whenever it's needed (it's a few thousand numbers, built in milliseconds).
 *
 * Two kinds of guesses:
 * - [merchantGuess]: you've put this exact merchant in one category at least twice (and almost always
 *   the same one). That's strong enough to beat the built-in list, because it's what you actually do.
 * - [wordGuess]: the words in the description ("SQ *THE MILL TELEGRAPH" shares "MILL" with things you filed
 *   under Eating Out). Only used when it's confident, and only after the built-in list had nothing.
 */
class LearnedCategorizer private constructor(
    private val byMerchant: Map<String, Map<Long, Int>>,
    private val tokenCounts: Map<Long, Map<String, Int>>,
    private val tokenTotals: Map<Long, Int>,
    private val docCounts: Map<Long, Int>,
    private val vocabulary: Set<String>,
    private val tokenDocFreq: Map<String, Int>,
) {
    data class Example(val description: String, val merchantKey: String, val categoryId: Long)
    data class Guess(val categoryId: Long, val confidence: Double)

    val exampleCount: Int = docCounts.values.sum()

    /** The category you've (nearly) always used for this merchant, if you've done it at least twice. */
    fun merchantGuess(merchantKey: String): Guess? {
        val counts = byMerchant[merchantKey.uppercase()] ?: return null
        val total = counts.values.sum()
        if (total < MERCHANT_MIN_EXAMPLES) return null
        val (best, n) = counts.maxByOrNull { it.value } ?: return null
        val share = n.toDouble() / total
        return if (share >= MERCHANT_MIN_SHARE) Guess(best, share) else null
    }

    /** A guess from the description's words, or null when it isn't confident enough to act on. */
    fun wordGuess(description: String, merchantKey: String): Guess? =
        score(description, merchantKey)?.takeIf { it.confidence >= WORD_MIN_CONFIDENCE }

    /**
     * A softer guess to *suggest* (never applied on its own): "Looks like Eating Out?" in Review.
     * Merchant habit first, then words at a lower bar.
     */
    fun suggestion(description: String, merchantKey: String): Guess? {
        byMerchant[merchantKey.uppercase()]?.maxByOrNull { it.value }?.let { (id, n) ->
            val total = byMerchant.getValue(merchantKey.uppercase()).values.sum()
            return Guess(id, n.toDouble() / total)
        }
        return score(description, merchantKey)?.takeIf { it.confidence >= SUGGEST_MIN_CONFIDENCE }
    }

    private fun score(description: String, merchantKey: String): Guess? {
        if (docCounts.size < 2 || exampleCount < MIN_TOTAL_EXAMPLES) return null
        val tokens = tokens(description, merchantKey).filter { it in vocabulary }
        // A word that only ever appeared once tells us little; ask for real evidence.
        if (tokens.none { (tokenDocFreq[it] ?: 0) >= 2 }) return null
        val v = vocabulary.size.toDouble()
        val scores = docCounts.keys.associateWith { c ->
            val total = (tokenTotals[c] ?: 0).toDouble()
            val counts = tokenCounts[c].orEmpty()
            var s = ln(docCounts.getValue(c).toDouble() / exampleCount)
            for (t in tokens) s += ln(((counts[t] ?: 0) + ALPHA) / (total + ALPHA * v))
            s
        }
        val max = scores.values.max()
        val norm = scores.values.sumOf { exp(it - max) }
        val (best, score) = scores.maxBy { it.value }
        // The winner must also have actually seen at least one of these words.
        if (tokens.none { (tokenCounts[best]?.get(it) ?: 0) > 0 }) return null
        return Guess(best, exp(score - max) / norm)
    }

    companion object {
        /** Merchant memory needs two examples: one could be a one-off correction. */
        const val MERCHANT_MIN_EXAMPLES = 2
        /** ...and 80% agreement, so a merchant you split between two categories isn't forced into one. */
        const val MERCHANT_MIN_SHARE = 0.8
        /** Below this many hand-categorized purchases the word model is mostly noise. */
        const val MIN_TOTAL_EXAMPLES = 3
        /** Act on a word guess only when it's this sure; otherwise it stays Uncategorized for you to decide. */
        const val WORD_MIN_CONFIDENCE = 0.75
        /** Bar for a one-tap suggestion; you still confirm it. */
        const val SUGGEST_MIN_CONFIDENCE = 0.45
        /** Laplace smoothing. */
        private const val ALPHA = 0.5

        val EMPTY = train(emptyList())

        /** Bank noise that says nothing about what you bought. */
        private val STOP = setOf(
            "POS", "DEBIT", "CREDIT", "CARD", "PURCHASE", "PURCHASES", "PAYMENT", "RECURRING", "AUTHORIZED", "ON",
            "VISA", "MASTERCARD", "MC", "CHECKCARD", "CHECK", "ACH", "WEB", "PPD", "CCD", "ID", "INC", "LLC", "CO",
            "CORP", "THE", "AND", "OF", "AT", "IN", "TO", "FROM", "FOR", "US", "USA", "NE", "LINCOLN", "OMAHA",
            "SQ", "TST", "SP", "PP", "PAYPAL", "WWW", "COM", "HTTPS", "STORE", "PENDING", "TRANSACTION", "TRANS",
            "REF", "NO", "NUM", "DES", "INDN", "CO", "XX", "XXXX",
        )

        fun tokens(description: String, merchantKey: String): Set<String> {
            val hay = BuiltInRules.haystack("$description $merchantKey")
            val words = hay.split(Regex("[^A-Z0-9&]+"))
                .filter { it.length >= 2 && it !in STOP && it.any(Char::isLetter) && it.count(Char::isDigit) <= 1 }
            val out = LinkedHashSet<String>(words)
            // Neighbouring pairs ("THE MILL" vs "MILL CREEK") carry more meaning than either word.
            for (i in 0 until words.size - 1) out += words[i] + "_" + words[i + 1]
            return out
        }

        fun train(examples: List<Example>): LearnedCategorizer {
            val byMerchant = HashMap<String, HashMap<Long, Int>>()
            val tokenCounts = HashMap<Long, HashMap<String, Int>>()
            val tokenTotals = HashMap<Long, Int>()
            val docCounts = HashMap<Long, Int>()
            val docFreq = HashMap<String, Int>()
            for (e in examples) {
                if (e.merchantKey.isNotBlank()) {
                    val m = byMerchant.getOrPut(e.merchantKey.uppercase()) { HashMap() }
                    m[e.categoryId] = (m[e.categoryId] ?: 0) + 1
                }
                docCounts[e.categoryId] = (docCounts[e.categoryId] ?: 0) + 1
                val counts = tokenCounts.getOrPut(e.categoryId) { HashMap() }
                for (t in tokens(e.description, e.merchantKey)) {
                    counts[t] = (counts[t] ?: 0) + 1
                    tokenTotals[e.categoryId] = (tokenTotals[e.categoryId] ?: 0) + 1
                    docFreq[t] = (docFreq[t] ?: 0) + 1
                }
            }
            return LearnedCategorizer(byMerchant, tokenCounts, tokenTotals, docCounts, docFreq.keys, docFreq)
        }
    }
}
