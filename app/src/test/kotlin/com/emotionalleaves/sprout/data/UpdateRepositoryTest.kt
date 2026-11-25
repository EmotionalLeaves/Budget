package com.emotionalleaves.sprout.data

import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.sprout.data.update.UpdateRepository
import com.emotionalleaves.sprout.data.update.UpdateState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class UpdateRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
    private val apk = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    @After fun tearDown() = server.shutdown()

    private fun repo(key: String = "k") = UpdateRepository(
        ApplicationProvider.getApplicationContext(), "1.15.0", "EmotionalLeaves/Budget", key, http, server.url("/").toString().trimEnd('/'),
    )

    private fun release(version: String, digest: String = sha) = """
        {"tag_name":"v$version","html_url":"https://github.com/x","body":"- New look","assets":[
          {"name":"sprout-v$version-50.apk","url":"${server.url("/asset")}","browser_download_url":"https://github.com/x.apk","size":${apk.size},"digest":"sha256:$digest"}]}
    """.trimIndent()

    @Test fun newerReleaseDownloadsThroughRedirectAndVerifies() = runBlocking {
        server.enqueue(MockResponse().setBody(release("1.16.0")))
        val r = repo()
        r.check()
        val available = r.state.value as UpdateState.Available
        assertEquals("1.16.0", available.release.version)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/cdn/file.apk")))
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        r.download(available.release)
        val ready = r.state.value as UpdateState.ReadyToInstall
        assertTrue(ready.file.readBytes().contentEquals(apk))
        server.takeRequest() // check
        val first = server.takeRequest()
        val cdn = server.takeRequest()
        assertEquals("/cdn/file.apk", cdn.path)
        // The key is never sent anywhere but api.github.com (the mock server isn't it).
        assertNull(cdn.getHeader("Authorization"))
        assertNull(first.getHeader("Authorization"))
    }

    @Test fun badChecksumIsRejected() = runBlocking {
        server.enqueue(MockResponse().setBody(release("1.16.0", digest = "0".repeat(64))))
        val r = repo()
        r.check()
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        r.download((r.state.value as UpdateState.Available).release)
        assertTrue(r.state.value is UpdateState.Failed)
    }

    @Test fun sameVersionIsUpToDate() = runBlocking {
        server.enqueue(MockResponse().setBody(release("1.15.0")))
        val r = repo()
        r.check()
        assertEquals(UpdateState.UpToDate, r.state.value)
    }

    @Test fun noAccessFallsBackToReleasesPage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val r = repo()
        r.check()
        assertTrue(r.state.value is UpdateState.OpenReleasesPage)
        // Without a key, a found release opens the page instead of downloading.
        server.enqueue(MockResponse().setBody(release("1.16.0")))
        val keyless = repo(key = "")
        keyless.check()
        keyless.download((keyless.state.value as UpdateState.Available).release)
        assertTrue(keyless.state.value is UpdateState.OpenReleasesPage)
    }

    @Test fun quietCheckStaysQuietOffline() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = repo()
        r.check(quiet = true)
        assertEquals(UpdateState.Idle, r.state.value)
    }

    @Test fun notifiesOncePerVersionAndDetectsJustUpdated() = runBlocking {
        val r = repo()
        server.enqueue(MockResponse().setBody(release("1.16.0")))
        assertEquals("1.16.0", r.checkForNotification())
        server.enqueue(MockResponse().setBody(release("1.16.0")))
        assertNull(r.checkForNotification())
        // First launch records the version; a later version is "just updated from" the earlier one, once.
        assertNull(r.consumeJustUpdatedFrom())
        val newer = UpdateRepository(ApplicationProvider.getApplicationContext(), "1.16.0", "EmotionalLeaves/Budget", "k", http, server.url("/").toString().trimEnd('/'))
        assertEquals("1.15.0", newer.consumeJustUpdatedFrom())
        assertNull(newer.consumeJustUpdatedFrom())
    }
}
