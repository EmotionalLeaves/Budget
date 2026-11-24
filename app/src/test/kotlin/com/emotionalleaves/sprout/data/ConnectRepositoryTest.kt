package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.secure.StoredSecret
import com.emotionalleaves.sprout.data.simplefin.SetupTokenResult
import com.emotionalleaves.sprout.data.sync.ConnectRepository
import com.emotionalleaves.sprout.data.sync.ConnectResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class ConnectRepositoryTest {
    private lateinit var env: TestEnv
    private lateinit var repo: ConnectRepository

    @Before fun setUp() {
        env = TestEnv()
        repo = ConnectRepository(env.client, env.secure, env.settings)
    }

    @After fun tearDown() = env.close()

    private fun token(): String = Base64.getEncoder().encodeToString(env.server.url("/simplefin/claim/abc").toString().toByteArray())
    private fun stored() = runBlocking { (env.secure.readAccessUrl() as? StoredSecret.Present)?.accessUrl }

    @Test fun invalidTokenMakesNoRequest() = runBlocking {
        assertEquals(ConnectResult.InvalidToken(SetupTokenResult.Reason.NOT_BASE64), repo.connect("not a token!"))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun successStoresAccessUrlAndResetsFlags() = runBlocking {
        env.settings.setAccessRevoked(true)
        env.server.enqueue(MockResponse().setBody(env.accessUrl))
        assertEquals(ConnectResult.Connected, repo.connect(token()))
        assertEquals(env.accessUrl, stored())
        assertFalse(env.settings.current().accessRevoked)
    }

    @Test fun usedTokenKeepsExistingConnection() = runBlocking {
        env.secure.saveAccessUrl(env.accessUrl)
        env.server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(ConnectResult.AlreadyUsed, repo.connect(token()))
        assertEquals(env.accessUrl, stored())
    }

    @Test fun networkFailureIsReportedAndNothingStored() = runBlocking {
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(ConnectResult.NetworkProblem, repo.connect(token()))
        assertEquals(null, stored())
        assertEquals(1, env.server.requestCount)
    }

    @Test fun credentialLessAccessUrlIsRefused() = runBlocking {
        env.server.enqueue(MockResponse().setBody("https://bridge.example.test/simplefin"))
        assertEquals(ConnectResult.BadAccessUrl, repo.connect(token()))
        assertTrue(stored() == null)
    }
}
