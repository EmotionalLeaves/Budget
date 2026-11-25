package com.emotionalleaves.sprout.domain.split

import kotlin.math.abs
import kotlin.math.sign

/** One part of a transaction moved to another category. [amountMinor] has the transaction's sign. */
data class SplitPart(val categoryId: Long, val amountMinor: Long)

/**
 * Splitting a transaction. Parts move money out of the transaction's own category; whatever isn't moved stays
 * there. So the parts and the remainder always add up to the bank's amount, even if a later sync changes it.
 */
object Splits {
    /** Most parts one transaction can have. A shopping trip rarely needs more. */
    const val MAX_PARTS = 10

    /** Turns typed positive amounts into parts with the transaction's sign, merging parts in the same category. */
    fun normalize(txAmountMinor: Long, parts: List<SplitPart>): List<SplitPart> {
        val sign = txAmountMinor.sign.toLong().takeIf { it != 0L } ?: -1L
        return parts.groupBy { it.categoryId }
            .map { (id, l) -> SplitPart(id, sign * l.sumOf { abs(it.amountMinor) }) }
            .filter { it.amountMinor != 0L }
    }

    /** Why these parts can't be saved, or null if they can. Expects [normalize]d parts. */
    fun problem(txAmountMinor: Long, pending: Boolean, ownCategoryId: Long, parts: List<SplitPart>): String? = when {
        parts.isEmpty() -> null
        pending -> "You can split this once it posts."
        txAmountMinor == 0L -> "A $0 transaction can't be split."
        parts.size > MAX_PARTS -> "Up to $MAX_PARTS parts."
        parts.any { it.categoryId == ownCategoryId } -> "Pick a different category from the transaction's own."
        parts.any { it.amountMinor.sign != txAmountMinor.sign } -> "Amounts must be more than $0."
        parts.sumOf { abs(it.amountMinor) } > abs(txAmountMinor) -> "The parts add up to more than the transaction."
        else -> null
    }

    /** What stays in the transaction's own category. */
    fun remainder(txAmountMinor: Long, parts: List<SplitPart>): Long = txAmountMinor - parts.sumOf { it.amountMinor }
}
