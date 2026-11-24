package com.emotionalleaves.sprout.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String) : Exception(message)

/**
 * The backup file format. Layout:
 * "SPROUTBK" (8 bytes) | format version (1) | iterations (4, big-endian) | salt (16) | IV (12) | AES-256-GCM ciphertext.
 * The key comes from the user's password with PBKDF2-HMAC-SHA256. The plaintext is gzip-compressed JSON.
 * The header is authenticated as GCM associated data, so any change to the file is detected.
 */
object BackupCrypto {
    private val MAGIC = "SPROUTBK".toByteArray(Charsets.US_ASCII)
    private const val FORMAT: Byte = 1
    /** OWASP 2023 guidance for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = 8 + 1 + 4 + SALT_LEN + IV_LEN

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        if (password.size < MIN_PASSWORD_LENGTH) throw BackupException("Use a password of at least $MIN_PASSWORD_LENGTH characters.")
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val header = ByteArrayOutputStream().apply {
            write(MAGIC); write(FORMAT.toInt())
            write(byteArrayOf((iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte()))
            write(salt); write(iv)
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(plain))
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER_LEN + 16 || !file.copyOfRange(0, 8).contentEquals(MAGIC)) {
            throw BackupException("This isn't a Sprout backup file.")
        }
        if (file[8] != FORMAT) throw BackupException("This backup was made by a newer version of Sprout. Update Sprout first.")
        val iterations = ((file[9].toInt() and 0xFF) shl 24) or ((file[10].toInt() and 0xFF) shl 16) or
            ((file[11].toInt() and 0xFF) shl 8) or (file[12].toInt() and 0xFF)
        if (iterations !in 1_000..10_000_000) throw BackupException("This backup file is damaged.")
        val salt = file.copyOfRange(13, 13 + SALT_LEN)
        val iv = file.copyOfRange(13 + SALT_LEN, HEADER_LEN)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(file.copyOfRange(0, HEADER_LEN))
            gunzip(cipher.doFinal(file, HEADER_LEN, file.size - HEADER_LEN))
        } catch (e: GeneralSecurityException) {
            throw BackupException("Wrong password, or the file was changed.")
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()

    /** Caps the decompressed size so a crafted file can't exhaust memory. */
    private fun gunzip(b: ByteArray, limit: Int = 200 * 1024 * 1024): ByteArray {
        GZIPInputStream(ByteArrayInputStream(b)).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw BackupException("This backup file is too large.")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
