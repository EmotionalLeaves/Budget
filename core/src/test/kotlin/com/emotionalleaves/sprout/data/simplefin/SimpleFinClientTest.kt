package com.emotionalleaves.sprout.data.simplefin

import com.emotionalleaves.sprout.Fixtures
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.TimeUnit

/** All requests go to a local TLS MockWebServer. Tests never touch the real network. */
class SimpleFinClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SimpleFinClient
    private lateinit var access: AccessUrl
    private lateinit var clientCerts: HandshakeCertificates

    @Before fun setUp() {
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val cert = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory(), false)
        server.start()
        val ok = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .build()
        client = SimpleFinClient(ok)
        val base = server.url("/simplefin").toString().removePrefix("https://")
        access = AccessUrl.parse("https://demo:demo@$base")!!
    }

    @After fun tearDown() = server.shutdown()

    private fun claimUrl() = server.url("/simplefin/claim/abc123").toString()

    @Test fun claim200ReturnsAccessUrlAndPostsEmptyBodyOnce() {
        server.enqueue(MockResponse().setBody("  ${Fixtures.DEMO_ACCESS_URL}\n"))
        val result = client.claim(claimUrl())
        assertEquals(ClaimResult.Success(Fixtures.DEMO_ACCESS_URL), result)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals(0L, req.bodySize)
        assertEquals(1, server.requestCount)
    }

    @Test fun claim403IsAlreadyUsed() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(ClaimResult.AlreadyUsed, client.claim(claimUrl()))
    }

    @Test fun claim200WithHttpUrlIsRejected() {
        server.enqueue(MockResponse().setBody("http://demo:demo@bridge.example.test/simplefin"))
        assertEquals(ClaimResult.BadResponse, client.claim(claimUrl()))
    }

    @Test fun claim200WithoutCredentialsIsRejected() {
        server.enqueue(MockResponse().setBody("https://bridge.example.test/simplefin"))
        assertEquals(ClaimResult.BadResponse, client.claim(claimUrl()))
    }

    @Test fun claimNetworkFailureIsNotRetried() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val result = client.claim(claimUrl())
        assertTrue(result is ClaimResult.NetworkError)
        assertEquals(1, server.requestCount)
    }

    @Test fun claimRefusesHttpUrl() {
        assertEquals(ClaimResult.BadResponse, client.claim("http://bridge.example.test/simplefin/claim/x"))
        assertEquals(0, server.requestCount)
    }

    @Test fun accountsSendsBasicHeaderAndStripsCredentialsFromUrl() {
        server.enqueue(MockResponse().setBody(Fixtures.simplefin("v2_basic.json")))
        val result = client.fetchAccounts(access, AccountsQuery(1000, 2000))
        assertTrue(result is FetchResult.Success)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("demo:demo".toByteArray()), req.getHeader("Authorization"))
        val url = req.requestUrl!!
        assertEquals("", url.username)
        assertEquals("", url.password)
        assertEquals("/simplefin/accounts", url.encodedPath)
        assertEquals("1000", url.queryParameter("start-date"))
        assertEquals("2000", url.queryParameter("end-date"))
        assertEquals("1", url.queryParameter("pending"))
        assertEquals("2", url.queryParameter("version"))
        assertNull(url.queryParameter("balances-only"))
    }

    @Test fun v1FallbackAfter400RetriesOnceWithoutVersion() {
        server.enqueue(MockResponse().setResponseCode(400))
        server.enqueue(MockResponse().setBody(Fixtures.simplefin("v1_legacy_org_and_errors.json")))
        val result = client.fetchAccounts(access, AccountsQuery(1, 2)) as FetchResult.Success
        assertEquals(ProtocolMode.V1, result.accountSet.mode)
        assertEquals(2, result.requestCount)
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("version"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("version"))
    }

    @Test fun v1FallbackHappensOnlyOnce() {
        server.enqueue(MockResponse().setResponseCode(400))
        server.enqueue(MockResponse().setResponseCode(400))
        val result = client.fetchAccounts(access, AccountsQuery(1, 2))
        assertEquals(FetchResult.HttpError(400, 2), result)
        assertEquals(2, server.requestCount)
    }

    @Test fun accounts403IsAuthFailed() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.AuthFailed(1), client.fetchAccounts(access, AccountsQuery(1, 2)))
    }

    @Test fun accounts402IsPaymentRequired() {
        server.enqueue(MockResponse().setResponseCode(402))
        assertEquals(FetchResult.PaymentRequired(1), client.fetchAccounts(access, AccountsQuery(1, 2)))
    }

    @Test fun accounts503IsServerBusyWithRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "120"))
        assertEquals(FetchResult.ServerBusy(503, 120, 1), client.fetchAccounts(access, AccountsQuery(1, 2)))
        assertEquals(1, server.requestCount)
    }

    @Test fun accounts429IsServerBusy() {
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(FetchResult.ServerBusy(429, null, 1), client.fetchAccounts(access, AccountsQuery(1, 2)))
    }

    @Test fun malformedJsonIsParseError() {
        server.enqueue(MockResponse().setBody(Fixtures.simplefin("malformed.json")))
        val result = client.fetchAccounts(access, AccountsQuery(1, 2))
        assertTrue(result is FetchResult.ParseError)
        assertFalse((result as FetchResult.ParseError).message.contains("demo"))
    }

    @Test fun timeoutIsNetworkError() {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(5, TimeUnit.SECONDS))
        val shortTimeouts = SimpleFinClient(
            OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build(),
            readTimeoutSeconds = 1,
        )
        val result = shortTimeouts.fetchAccounts(access, AccountsQuery(1, 2))
        assertTrue(result is FetchResult.NetworkError)
        assertFalse((result as FetchResult.NetworkError).message.contains("demo"))
    }

    @Test fun accountFilterIsRepeatedForV2() {
        server.enqueue(MockResponse().setBody(Fixtures.simplefin("empty_accounts.json")))
        client.fetchAccounts(access, AccountsQuery(1, 2, accountIds = listOf("A1", "B 2")))
        val url = server.takeRequest().requestUrl!!
        assertEquals(listOf("A1", "B 2"), url.queryParameterValues("account"))
    }
}
