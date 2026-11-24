package com.emotionalleaves.sprout.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts small secrets. Separated from storage so tests can swap in a fake. */
interface SecretCipher {
    /** Returns IV + ciphertext. */
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
    fun deleteKey()
}

/**
 * AES-256-GCM key in the AndroidKeyStore: non-exportable, random IV per encryption.
 */
class KeystoreCipher : SecretCipher {

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        require(ivLen in 12..16 && blob.size > 1 + ivLen) { "bad blob" }
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val existing = keyStore().getKey(ALIAS, null) as? SecretKey ?: error("no key")
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    override fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    companion object {
        const val ALIAS = "sprout_simplefin_key"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_store")

sealed interface StoredSecret {
    data object None : StoredSecret
    /** Plain access URL text; only ever held in memory for the duration of a request. */
    class Present(val accessUrl: String) : StoredSecret {
        override fun toString(): String = "Present(***)"
    }
    /** Ciphertext exists but can't be decrypted (key invalidated). The user must reconnect. */
    data object Unreadable : StoredSecret
}

/**
 * Holds the SimpleFIN access URL, encrypted. Never stored in Room, never in plaintext, never logged.
 */
class SecureStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    constructor(context: Context) : this(context.applicationContext.secureDataStore, KeystoreCipher())

    val hasAccessUrl = dataStore.data.map { it[KEY_ACCESS] != null }

    suspend fun hasAccessUrlNow(): Boolean = dataStore.data.first()[KEY_ACCESS] != null

    suspend fun saveAccessUrl(accessUrl: String) {
        val blob = cipher.encrypt(accessUrl.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        dataStore.edit { it[KEY_ACCESS] = encoded }
    }

    suspend fun readAccessUrl(): StoredSecret {
        val encoded = dataStore.data.first()[KEY_ACCESS] ?: return StoredSecret.None
        return try {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            StoredSecret.Present(String(cipher.decrypt(blob), Charsets.UTF_8))
        } catch (e: Exception) {
            // Never fall back to plaintext and never crash: the user reconnects with a new token.
            StoredSecret.Unreadable
        }
    }

    /** Deletes the stored access URL and its Keystore key. */
    suspend fun clear() {
        dataStore.edit { it.remove(KEY_ACCESS) }
        cipher.deleteKey()
    }

    private companion object {
        val KEY_ACCESS = stringPreferencesKey("access_url_ct")
    }
}
