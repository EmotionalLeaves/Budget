package com.emotionalleaves.sprout.data.tags

import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TagEntity
import com.emotionalleaves.sprout.data.db.TransactionTagEntity
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Deletes the tag; the transactions stay, they just lose the label. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { db.tags().delete(id) }

    companion object {
        const val MAX_NAME = 40
        private val PALETTE = listOf(
            0xFF17694A, 0xFF2B6773, 0xFFA35B12, 0xFF7A4FA0, 0xFFB3404A, 0xFF4F7A1F, 0xFF2F5BA8, 0xFF8A6D00,
        ).map { it.toInt() }
    }
}
