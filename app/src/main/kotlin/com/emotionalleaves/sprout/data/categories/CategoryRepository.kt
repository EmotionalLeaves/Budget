package com.emotionalleaves.sprout.data.categories

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.MerchantRuleEntity
import com.emotionalleaves.sprout.data.db.RuleOrigin
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TransactionSplitEntity
import com.emotionalleaves.sprout.domain.split.SplitPart
import com.emotionalleaves.sprout.domain.split.Splits
import com.emotionalleaves.sprout.domain.categorize.BuiltInRules
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.LearnedCategorizer
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.domain.categorize.UserRule
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Category corrections, rules and category management. Every change that affects which category a
 * transaction gets goes through here, so the "learn from corrections" rules stay consistent.
 */
class CategoryRepository(private val db: SproutDatabase, private val clock: Clock) {

    private fun now() = clock.instant().epochSecond

    /**
     * The user picked [categoryId] for one transaction. It becomes USER (never overwritten by sync).
     * With [alwaysForMerchant], an EXACT rule for its merchant is saved and every other transaction from that
     * merchant that the user hasn't set by hand moves to the same category.
     * Returns how many other transactions changed.
     */
    suspend fun recategorize(transactionId: String, categoryId: Long, alwaysForMerchant: Boolean): Int =
        recategorizeWithUndo(transactionId, categoryId, alwaysForMerchant).others

    /**
     * Result of a recategorization, with a way to put everything back. [learned] is how many other purchases
     * the on-device learner re-sorted because of this pick.
     */
    class Change(val others: Int, val undo: suspend () -> Unit, val learned: Int = 0)

    /** The on-device learner, trained on everything you've categorized yourself. */
    suspend fun learner(): LearnedCategorizer = withContext(Dispatchers.IO) {
        LearnedCategorizer.train(db.transactions().learningExamples().map {
            LearnedCategorizer.Example(it.description, it.merchantKey, it.categoryId)
        })
    }

    /**
     * With [learn] (the default) every purchase you haven't set by hand is re-sorted afterwards, so one pick
     * teaches the rest. Bulk edits pass false and call [reapplyRules] once at the end.
     */
    suspend fun recategorizeWithUndo(
        transactionId: String,
        categoryId: Long,
        alwaysForMerchant: Boolean,
        learn: Boolean = true,
    ): Change = withContext(Dispatchers.IO) {
        db.withTransaction {
            val tx = db.transactions().getById(transactionId) ?: return@withTransaction Change(0, {})
            // Snapshot what this change can touch: the merchant's transactions and its EXACT rule.
            val before = db.transactions().ruleInputsForMerchant(tx.merchantKey)
            val ruleBefore = db.merchantRules().find(RuleType.EXACT, tx.merchantKey)
            val undo: suspend () -> Unit = {
                withContext(Dispatchers.IO) {
                    db.withTransaction {
                        val now = now()
                        before.forEach { db.transactions().setCategory(it.id, it.categoryId, it.categorySource, now) }
                        val ruleNow = db.merchantRules().find(RuleType.EXACT, tx.merchantKey)
                        when {
                            ruleBefore == null && ruleNow != null -> db.merchantRules().delete(ruleNow.id)
                            ruleBefore != null -> db.merchantRules().update(ruleBefore)
                        }
                        if (learn) reapplyRulesLocked()
                    }
                }
            }
            val now = now()
            var others = 0
            if (alwaysForMerchant) {
                upsertRule(RuleType.EXACT, tx.merchantKey, categoryId, RuleOrigin.USER_CORRECTION, now)
                val updated = db.transactions().applyMerchantRule(tx.merchantKey, categoryId, now)
                // The tapped one was included above unless it was already USER; it becomes USER next.
                others = updated - if (tx.categorySource != CategorySource.USER) 1 else 0
            }
            db.transactions().setCategory(transactionId, categoryId, CategorySource.USER, now)
            val learned = if (learn) reapplyRulesLocked() else 0
            Change(others, undo, learned)
        }
    }

    /**
     * Saves a transaction's split: [parts] move money (positive amounts as typed) to other categories; the rest stays
     * in its own category. An empty list removes the split. Returns why it can't be saved, or null when saved.
     */
    suspend fun setSplits(transactionId: String, parts: List<SplitPart>): String? = withContext(Dispatchers.IO) {
        val tx = db.transactions().getById(transactionId) ?: return@withContext "This transaction no longer exists."
        val normalized = Splits.normalize(tx.amountMinor, parts)
        Splits.problem(tx.amountMinor, tx.pending, tx.categoryId, normalized)?.let { return@withContext it }
        val now = clock.instant().epochSecond
        db.splits().replace(transactionId, normalized.map { TransactionSplitEntity(transactionId = transactionId, categoryId = it.categoryId, amountMinor = it.amountMinor, createdAt = now) })
        null
    }

    suspend fun setNote(transactionId: String, note: String) = withContext(Dispatchers.IO) {
        val clean = Sanitize.text(note, 500).ifBlank { null }
        db.transactions().setNote(transactionId, clean, now())
    }

    suspend fun setExcluded(transactionId: String, excluded: Boolean) = withContext(Dispatchers.IO) {
        db.transactions().setExcluded(transactionId, excluded, now())
    }

    /** Adds a keyword rule ("contains"). Returns null if the keyword is unusable. */
    suspend fun addContainsRule(keyword: String, categoryId: Long, minAbsMinor: Long? = null, maxAbsMinor: Long? = null): Long? = withContext(Dispatchers.IO) {
        val pattern = BuiltInRules.haystack(Sanitize.text(keyword, 60))
        if (pattern.length < 2) return@withContext null
        val (lo, hi) = if (minAbsMinor != null && maxAbsMinor != null && minAbsMinor > maxAbsMinor) maxAbsMinor to minAbsMinor else minAbsMinor to maxAbsMinor
        db.withTransaction {
            val id = upsertRule(RuleType.CONTAINS, pattern, categoryId, RuleOrigin.USER_CREATED, now())
            db.merchantRules().getById(id)?.let { db.merchantRules().update(it.copy(minAbsMinor = lo?.takeIf { v -> v > 0 }, maxAbsMinor = hi?.takeIf { v -> v > 0 })) }
            reapplyRulesLocked()
            id
        }
    }

    /** Moves a category one place up (-1) or down (+1); everything picks up the new order (grids, budgets, lists). */
    suspend fun move(id: Long, delta: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val list = db.categories().getAll().filter { !it.archived }.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return@withTransaction
            java.util.Collections.swap(list, i, j)
            list.forEachIndexed { n, c -> if (c.sortOrder != n * 10) db.categories().update(c.copy(sortOrder = n * 10)) }
        }
    }

    /** How many transactions a keyword rule would match (for the live preview while typing). */
    suspend fun previewContains(keyword: String, minAbsMinor: Long?, maxAbsMinor: Long?): Int = withContext(Dispatchers.IO) {
        val pattern = BuiltInRules.haystack(Sanitize.text(keyword, 60))
        if (pattern.length < 2) return@withContext 0
        val rule = UserRule(0, RuleType.CONTAINS, pattern, 0, minAbsMinor, maxAbsMinor)
        val resolver = CategoryResolver(listOf(rule), emptyMap())
        db.transactions().ruleInputs().count { resolver.resolve(it.description, it.merchantKey, it.amountMinor).source == CategorySource.MERCHANT_RULE }
    }

    suspend fun changeRuleCategory(ruleId: Long, categoryId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val rule = db.merchantRules().getById(ruleId) ?: return@withTransaction
            db.merchantRules().update(rule.copy(categoryId = categoryId, updatedAt = now()))
            reapplyRulesLocked()
        }
    }

    suspend fun deleteRule(ruleId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.merchantRules().delete(ruleId)
            reapplyRulesLocked()
        }
    }

    /**
     * Runs once after an update that changed the built-in merchant list: adds categories that arrived later
     * (Travel, Pets) to existing installs, then re-sorts every purchase you haven't set by hand.
     * Never touches anything you categorized yourself. Returns how many purchases moved.
     */
    suspend fun upgradeCatalog(fromVersion: Int): Int = withContext(Dispatchers.IO) {
        if (fromVersion >= CATALOG_VERSION) return@withContext 0
        db.withTransaction {
            if (db.categories().count() == 0) return@withTransaction 0
            if (fromVersion < 1) {
                val have = db.categories().getAll().mapNotNull { it.key }.toSet()
                var order = db.categories().maxSortOrder()
                val missing = DefaultCategories.all.filter { it.key in DefaultCategories.addedLater && it.key !in have }
                db.categories().insertIgnore(missing.map { c ->
                    CategoryEntity(key = c.key, name = c.name, kind = c.kind, iconKey = c.key,
                        colorArgb = c.colorArgb.toInt(), sortOrder = ++order, archived = false)
                })
            }
            reapplyRulesLocked()
        }
    }

    /** Re-runs categorization for every transaction whose category wasn't set by the user or an import. */
    suspend fun reapplyRules(): Int = withContext(Dispatchers.IO) { db.withTransaction { reapplyRulesLocked() } }

    private suspend fun reapplyRulesLocked(): Int {
        val categories = db.categories().getAll()
        val idByKey = categories.mapNotNull { c -> c.key?.let { it to c.id } }.toMap()
        val rules = db.merchantRules().getAll().map { it.toUserRule() }
        val resolver = CategoryResolver(rules, idByKey, learner())
        val now = now()
        var changed = 0
        for (row in db.transactions().ruleInputs()) {
            val r = resolver.resolve(row.description, row.merchantKey, row.amountMinor)
            val newId = r.categoryId ?: continue
            if (newId != row.categoryId || r.source != row.categorySource) {
                db.transactions().setCategory(row.id, newId, r.source, now)
                changed++
            }
        }
        return changed
    }

    private suspend fun upsertRule(type: RuleType, pattern: String, categoryId: Long, origin: RuleOrigin, now: Long): Long {
        val existing = db.merchantRules().find(type, pattern)
        return if (existing != null) {
            db.merchantRules().update(existing.copy(categoryId = categoryId, hitCount = existing.hitCount + 1, updatedAt = now))
            existing.id
        } else {
            db.merchantRules().insert(
                MerchantRuleEntity(matchType = type, pattern = pattern, categoryId = categoryId, origin = origin,
                    hitCount = 1, createdAt = now, updatedAt = now),
            )
        }
    }

    // --- Categories ---

    suspend fun addCategory(name: String, kind: CategoryKind, colorArgb: Int, iconKey: String = "custom"): Long = withContext(Dispatchers.IO) {
        val clean = Sanitize.text(name, 40).ifBlank { "New category" }
        db.categories().insert(
            CategoryEntity(key = null, name = clean, kind = kind, iconKey = Sanitize.text(iconKey, 40).ifBlank { "custom" }, colorArgb = colorArgb,
                sortOrder = db.categories().maxSortOrder() + 1, archived = false),
        )
    }

    suspend fun updateCategory(id: Long, name: String, colorArgb: Int, iconKey: String? = null) = withContext(Dispatchers.IO) {
        val c = db.categories().getById(id) ?: return@withContext
        db.categories().update(c.copy(name = Sanitize.text(name, 40).ifBlank { c.name }, colorArgb = colorArgb,
            iconKey = iconKey?.let { Sanitize.text(it, 40) }?.ifBlank { null } ?: c.iconKey))
    }

    /** Archiving hides a category from pickers; it's never deleted, so its transactions keep it. */
    suspend fun setArchived(id: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        val c = db.categories().getById(id) ?: return@withContext
        db.categories().update(c.copy(archived = archived))
    }

    companion object {
        /** Bump when the built-in merchant list changes so existing history is re-sorted once on update. */
        const val CATALOG_VERSION = 2

        /** Colors offered for new categories (Material 600 shades). */
        val PALETTE: List<Int> = listOf(
            0xFF2E7D32, 0xFF00838F, 0xFF1565C0, 0xFF5E35B1, 0xFFAD1457, 0xFFC62828,
            0xFFEF6C00, 0xFFF9A825, 0xFF6D4C41, 0xFF546E7A, 0xFF00695C, 0xFF8E24AA,
        ).map { it.toInt() }
    }
}
