package com.emotionalleaves.sprout.data.simplefin

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ClaimResult {
    /** The raw access URL text. Store it immediately; it is never shown again by the server. */
    data class Success(val accessUrl: String) : ClaimResult
    /** 403: the token was already claimed or doesn't exist. */
    data object AlreadyUsed : ClaimResult
    /** The request may or may not have reached the server, so the token may have been used. */
    data class NetworkError(val message: String) : ClaimResult
    data class ServerError(val httpCode: Int) : ClaimResult
    /** The server answered 200 but the body was not a usable https access URL. */
    data object BadResponse : ClaimResult
}

sealed interface FetchResult {
    data class Success(val accountSet: AccountSet, val requestCount: Int) : FetchResult
    /** 403: access revoked or credentials wrong. The user needs a new setup token. */
    data class AuthFailed(val requestCount: Int) : FetchResult
    /** 402: the SimpleFIN Bridge subscription needs attention. */
    data class PaymentRequired(val requestCount: Int) : FetchResult
    /** 5xx or 429: try again later with backoff. [retryAfterSeconds] comes from the Retry-After header. */
    data class ServerBusy(val httpCode: Int, val retryAfterSeconds: Long?, val requestCount: Int) : FetchResult
    data class HttpError(val httpCode: Int, val requestCount: Int) : FetchResult
    data class NetworkError(val message: String, val requestCount: Int) : FetchResult
    data class ParseError(val message: String, val requestCount: Int) : FetchResult
}

/** Query for GET /accounts. Dates are Unix epoch seconds; end is exclusive. */
data class AccountsQuery(
    val startDate: Long,
    val endDate: Long,
    val pending: Boolean = true,
    val balancesOnly: Boolean = false,
)

/**
 * The only network code in Sprout. It talks to exactly two kinds of URL: the claim URL inside the
 * setup token, and the user's own Access URL. There is no other network destination.
 */
class SimpleFinClient(
    baseClient: OkHttpClient = OkHttpClient(),
    readTimeoutSeconds: Long = READ_TIMEOUT_S,
) {

    companion object {
        const val CONNECT_TIMEOUT_S = 15L
        const val READ_TIMEOUT_S = 60L
    }

    // The claim must be POSTed exactly once: no automatic retries, no redirects.
    private val claimClient = baseClient.newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    // Never follow redirects: the Authorization header must only ever go to the access URL's own host.
    private val dataClient = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    fun claim(claimUrl: String): ClaimResult {
        val url = claimUrl.toHttpUrlOrNull() ?: return ClaimResult.BadResponse
        if (!url.isHttps) return ClaimResult.BadResponse
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        return try {
            claimClient.newCall(request).execute().use { resp ->
                when (resp.code) {
                    200 -> {
                        val body = resp.body?.string()?.trim().orEmpty()
                        if (AccessUrl.parse(body) != null) ClaimResult.Success(body) else ClaimResult.BadResponse
                    }
                    403 -> ClaimResult.AlreadyUsed
                    else -> ClaimResult.ServerError(resp.code)
                }
            }
        } catch (e: IOException) {
            ClaimResult.NetworkError(e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            ClaimResult.NetworkError(e.javaClass.simpleName)
        }
    }

    /**
     * GET {access}/accounts with version=2. If the server rejects version=2 with HTTP 400, retries once
     * without the version parameter and parses in v1 mode. Never loops.
     */
    fun fetchAccounts(access: AccessUrl, query: AccountsQuery): FetchResult {
        val first = get(access, query, version = 2)
        if (first is Raw.Http && first.code == 400) {
            return interpret(get(access, query, version = null), ProtocolMode.V1, requests = 2)
        }
        return interpret(first, ProtocolMode.V2, requests = 1)
    }

    private sealed interface Raw {
        data class Http(val code: Int, val body: String?, val retryAfter: Long? = null) : Raw
        data class Failed(val message: String) : Raw
    }

    private fun get(access: AccessUrl, q: AccountsQuery, version: Int?): Raw {
        val base = access.accountsUrl().toHttpUrlOrNull() ?: return Raw.Failed("Bad access URL")
        val url = base.newBuilder().apply {
            addQueryParameter("start-date", q.startDate.toString())
            addQueryParameter("end-date", q.endDate.toString())
            if (q.pending) addQueryParameter("pending", "1")
            if (q.balancesOnly) addQueryParameter("balances-only", "1")
            if (version != null) addQueryParameter("version", version.toString())
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", access.authorizationHeader())
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            dataClient.newCall(request).execute().use { resp ->
                Raw.Http(
                    code = resp.code,
                    body = if (resp.code == 200) resp.body?.string() else null,
                    retryAfter = resp.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(0, 86_400),
                )
            }
        } catch (e: IOException) {
            Raw.Failed(e.javaClass.simpleName)
        } catch (e: IllegalStateException) {
            Raw.Failed(e.javaClass.simpleName)
        }
    }

    private fun interpret(raw: Raw, mode: ProtocolMode, requests: Int): FetchResult = when (raw) {
        is Raw.Failed -> FetchResult.NetworkError(raw.message, requests)
        is Raw.Http -> when (raw.code) {
            200 -> try {
                FetchResult.Success(AccountSetParser.parse(raw.body.orEmpty(), mode), requests)
            } catch (e: AccountSetParseException) {
                FetchResult.ParseError(e.message ?: "Unreadable response", requests)
            }
            402 -> FetchResult.PaymentRequired(requests)
            403 -> FetchResult.AuthFailed(requests)
            429, in 500..599 -> FetchResult.ServerBusy(raw.code, raw.retryAfter, requests)
            else -> FetchResult.HttpError(raw.code, requests)
        }
    }
}
