package com.emotionalleaves.sprout.data.backup

import android.content.Context
import com.emotionalleaves.sprout.data.db.SproutDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Automatic safety copies of your data, made right before an update installs (and on request). They stay in
 * Sprout's private storage on this phone, like the database itself, and are never uploaded. The last [KEEP] are kept.
 */
class SnapshotRepository(private val context: Context, private val backups: BackupRepository) {
    data class Snapshot(val file: File, val version: String, val createdAt: Long, val sizeBytes: Long)

    private val dir get() = File(context.filesDir, "snapshots").apply { mkdirs() }

    suspend fun take(version: String): Snapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis() / 1000
            val f = File(dir, "snapshot-$now-${version.replace(Regex("[^0-9A-Za-z.]"), "")}.json.gz")
            GZIPOutputStream(f.outputStream()).bufferedWriter().use { it.write(backups.toJson()) }
            list().drop(KEEP).forEach { it.file.delete() }
            list().firstOrNull()
        }.getOrNull()
    }

    fun list(): List<Snapshot> = (dir.listFiles() ?: emptyArray()).mapNotNull { f ->
        val m = Regex("""snapshot-(\d+)-(.+)\.json\.gz""").matchEntire(f.name) ?: return@mapNotNull null
        Snapshot(f, m.groupValues[2], m.groupValues[1].toLong(), f.length())
    }.sortedByDescending { it.createdAt }

    /** Puts everything back as it was in [s]. Same all-or-nothing restore as a backup file. */
    suspend fun restore(s: Snapshot): RestoreSummary = withContext(Dispatchers.IO) {
        val text = GZIPInputStream(s.file.inputStream()).bufferedReader().use { it.readText() }
        val obj = Json.parseToJsonElement(text).jsonObject
        val schema = (obj["schema"] as? JsonPrimitive)?.intOrNull ?: throw BackupException("This snapshot is damaged.")
        if (schema > SproutDatabase.VERSION) throw BackupException("This snapshot was made by a newer version of Sprout.")
        backups.restore(obj)
    }

    companion object { const val KEEP = 3 }
}
