package com.emotionalleaves.sprout.data.tags

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TagEntity
import com.emotionalleaves.sprout.data.db.TransactionTagEntity
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.emotionalleaves.sprout.data.db.TagRuleEntity
import com.emotionalleaves.sprout.domain.categorize.TagRule
import com.emotionalleaves.sprout.domain.categorize.TagRules
import java.time.Clock

/** Tags: free labels across categories ("Chicago trip"). Names are unique, ignoring case. */
class TagRepository(private val db: SproutDatabase, private val clock: Clock) {

    /** Finds a tag by name or makes it. Returns null for an empty name. */
    suspend fun getOrCreate(rawName: String): Long? = withContext(Dispatchers.IO) {
        val name = Sanitize.text(rawName, MAX_NAME).trim().removePrefix("#").trim()
        if (name.isEmpty()) return@withContext null
        db.tags().getByName(name)?.let { return@withContext it.id }
        val count = db.backup().tags().size
        db.tags().insert(TagEntity(name = name, colorArgb = PALETTE[count % PALETTE.size], createdAt = clock.instant().epochSecond))
    }

    suspend fun attach(transactionId: String, tagId: Long) = withContext(Dispatchers.IO) {
        db.tags().attach(TransactionTagEntity(transactionId, tagId))
    }

    suspend fun detach(transactionId: String, tagId: Long) = withContext(Dispatchers.IO) { db.tags().detach(transactionId, tagId) }

    /** Renames a tag; refuses a name another tag already has. Returns false when refused. */
    suspend fun rename(id: Long, rawName: String): Boolean = withContext(Dispatchers.IO) {
        val name = Sanitize.text(rawName, MAX_NAME).trim().removePrefix("#").trim()
        if (name.isEmpty()) return@withContext false
        val other = db.tags().getByName(name)
        if (other != null && other.id != id) return@withContext false
        db.tags().rename(id, name)
        true
    }

    fun observeRules() = db.tags().observeRules()

    /** Transactions touched before this were already checked against the tag rules. */
    @Volatile private var watermark: Long = clock.instant().epochSecond

    /**
     * Adds "anything containing [rawPattern] gets [tagId]" and applies it to every existing transaction.
     * Returns how many transactions got the tag, or null for a too-short keyword.
     */
    suspend fun addRule(rawPattern: String, tagId: Long): Int? = withContext(Dispatchers.IO) {
        val pattern = Sanitize.text(rawPattern, 60).trim()
        if (pattern.length < 2) return@withContext null
        db.tags().insertRule(TagRuleEntity(pattern = pattern, tagId = tagId, createdAt = clock.instant().epochSecond))
        apply(listOf(TagRule(0, pattern, tagId)), 0)
    }

    /** How many transactions an auto-tag keyword would tag (live preview). */
    suspend fun previewRule(rawPattern: String): Int = withContext(Dispatchers.IO) {
        val pattern = Sanitize.text(rawPattern, 60).trim()
        if (pattern.length < 2) return@withContext 0
        val rules = listOf(TagRule(0, pattern, 0))
        db.tags().tagInputs(0).count { TagRules.match(rules, it.description, it.merchantKey).isNotEmpty() }
    }

    /** Removes an auto-tag word; the undo puts it back (tags it already added stay either way). */
    suspend fun deleteRule(id: Long): suspend () -> Unit = withContext(Dispatchers.IO) {
        val saved = db.tags().rules().firstOrNull { it.id == id }
        db.tags().deleteRule(id)
        val undo: suspend () -> Unit = { withContext(Dispatchers.IO) { saved?.let { db.tags().putRules(listOf(it)) } } }
        undo
    }

    /** After a sync or import: tag the transactions that changed since the last pass. Untagging by hand sticks. */
    suspend fun applyRulesToNew(): Int = withContext(Dispatchers.IO) {
        val since = watermark
        watermark = clock.instant().epochSecond
        val rules = db.tags().rules().map { TagRule(it.id, it.pattern, it.tagId) }
        if (rules.isEmpty()) 0 else apply(rules, since)
    }

    private suspend fun apply(rules: List<TagRule>, since: Long): Int {
        var n = 0
        for (t in db.tags().tagInputs(since)) {
            for (tag in TagRules.match(rules, t.description, t.merchantKey)) {
                db.tags().attach(TransactionTagEntity(t.id, tag)); n++
            }
        }
        return n
    }

    /** Deletes the tag; the transactions stay, they just lose the label. */
    /** Deletes a tag (and its links and auto-tag words); the undo restores all three. */
    suspend fun delete(id: Long): suspend () -> Unit = withContext(Dispatchers.IO) {
        val tag = db.tags().getById(id)
        val links = db.tags().transactionIdsFor(id)
        val rules = db.tags().rules().filter { it.tagId == id }
        db.tags().delete(id)
        val undo: suspend () -> Unit = {
            withContext(Dispatchers.IO) {
                if (tag != null && db.tags().getById(tag.id) == null && db.tags().getByName(tag.name) == null) {
                    db.withTransaction {
                        db.tags().insert(tag)
                        db.tags().putRules(rules)
                        // Purchases deleted since then are skipped.
                        val alive = links.chunked(500).flatMap { db.transactions().existingIds(it) }.toSet()
                        links.filter { it in alive }.forEach { db.tags().attach(com.emotionalleaves.sprout.data.db.TransactionTagEntity(it, tag.id)) }
                    }
                }
            }
        }
        undo
    }

    companion object {
        const val MAX_NAME = 40
        private val PALETTE = listOf(
            0xFF17694A, 0xFF2B6773, 0xFFA35B12, 0xFF7A4FA0, 0xFFB3404A, 0xFF4F7A1F, 0xFF2F5BA8, 0xFF8A6D00,
        ).map { it.toInt() }
    }
}
