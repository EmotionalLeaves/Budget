package com.emotionalleaves.sprout.data.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.emotionalleaves.sprout.data.db.ReceiptEntity
import com.emotionalleaves.sprout.data.db.SproutDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID

/**
 * Receipt photos, kept in Sprout's private storage (never in the gallery, never uploaded). Picked with Android's
 * photo picker, so Sprout needs no storage permission. Each photo is shrunk to at most [MAX_SIDE] px.
 */
class ReceiptRepository(private val context: Context, private val db: SproutDatabase, private val clock: Clock) {
    val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(r: ReceiptEntity) = File(dir, r.fileName)

    /** Copies and shrinks the picked image. Returns false if it couldn't be read. */
    suspend fun add(transactionId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val name = UUID.randomUUID().toString() + ".jpg"
            File(dir, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            db.receipts().insert(ReceiptEntity(transactionId = transactionId, fileName = name, createdAt = clock.instant().epochSecond))
            true
        }.getOrDefault(false)
    }

    suspend fun delete(r: ReceiptEntity) = withContext(Dispatchers.IO) {
        file(r).delete()
        db.receipts().delete(r.id)
    }

    /** Removes photo files no longer attached to any transaction (after a restore or a deleted transaction). */
    suspend fun cleanUp() = withContext(Dispatchers.IO) {
        val keep = db.receipts().getAll().map { it.fileName }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    companion object { const val MAX_SIDE = 1600 }
}
