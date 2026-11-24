package com.emotionalleaves.sprout.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.secure.SecretCipher
import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.simplefin.SimpleFinClient
import com.emotionalleaves.sprout.data.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.io.File
import java.net.InetAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** XOR "cipher" for tests: Robolectric has no AndroidKeyStore. Can be told to fail like an invalidated key. */
class FakeCipher : SecretCipher {
    var failDecrypt = false
    var deleted = false
    override fun encrypt(plain: ByteArray) = byteArrayOf(12) + ByteArray(12) + plain.map { (it.toInt() xor 0x5A).toByte() }
    override fun decrypt(blob: ByteArray): ByteArray {
        if (failDecrypt) throw javax.crypto.AEADBadTagException("key invalidated")
        return blob.copyOfRange(13, blob.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
    override fun deleteKey() { deleted = true }
}

/** In-memory database, temp-file DataStores and a local TLS MockWebServer. */
class TestEnv(nowSeconds: Long = 1_763_900_000) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: SproutDatabase = Room.inMemoryDatabaseBuilder(context, SproutDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.cacheDir, "test-" + UUID.randomUUID()).apply { mkdirs() }
    val cipher = FakeCipher()
    val secure = SecureStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "secure.preferences_pb") }, cipher)
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
    var clock: Clock = Clock.fixed(Instant.ofEpochSecond(nowSeconds), ZoneId.of("America/Chicago"))

    val server = MockWebServer()
    val client: SimpleFinClient

    init {
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val cert = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start()
        client = SimpleFinClient(
            OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build(),
        )
    }

    /** Access URL pointing at the mock server, in the demo:demo form tests always use. */
    val accessUrl: String get() = "https://demo:demo@" + server.url("/simplefin").toString().removePrefix("https://")

    fun repo() = SyncRepository(db, secure, settings, client, clock)

    fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/simplefin/$name")) { "missing $name" }.readText()

    override fun close() {
        server.shutdown()
        db.close()
        dir.deleteRecursively()
    }
}
