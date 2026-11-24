package com.emotionalleaves.sprout.data.simplefin

import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/**
 * A parsed SimpleFIN Access URL: an https URL whose userinfo part holds the Basic Auth user and password.
 *
 * The credentials are kept apart from the URL: requests go to [baseUrl] (no userinfo) with an explicit
 * `Authorization: Basic` header, because OkHttp does not turn URL userinfo into a header by itself.
 */
class AccessUrl private constructor(
    /** https://host[:port]/path with no userinfo and no trailing slash. */
    val baseUrl: String,
    private val username: String,
    private val password: String,
) {
    val host: String get() = URI(baseUrl).host

    fun authorizationHeader(): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

    /** The path "/accounts" appended to the access URL's own path. */
    fun accountsUrl(): String = "$baseUrl/accounts"

    override fun toString(): String = "AccessUrl(***)"

    companion object {
        /** Returns null unless [raw] is an https URL with a host and user:password credentials. */
        fun parse(raw: String): AccessUrl? {
            val text = raw.trim()
            val uri = try {
                URI(text)
            } catch (e: Exception) {
                return null
            }
            if (!"https".equals(uri.scheme, ignoreCase = true)) return null
            val host = uri.host ?: return null
            val userInfo = uri.rawUserInfo ?: return null
            val colon = userInfo.indexOf(':')
            if (colon <= 0) return null
            val user = percentDecode(userInfo.substring(0, colon)) ?: return null
            val pass = percentDecode(userInfo.substring(colon + 1)) ?: return null
            if (user.isEmpty() || pass.isEmpty()) return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val path = (uri.rawPath ?: "").trimEnd('/')
            return AccessUrl("https://$host$port$path", user, pass)
        }

        private fun percentDecode(s: String): String? = try {
            // URLDecoder turns '+' into a space, which is wrong for URL userinfo; protect it first.
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
