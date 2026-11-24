package com.emotionalleaves.sprout.data.simplefin

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Result of checking a pasted SimpleFIN setup token, before any network request. */
sealed interface SetupTokenResult {
    data class Valid(val claimUrl: String) : SetupTokenResult
    data class Invalid(val reason: Reason) : SetupTokenResult

    enum class Reason { EMPTY, NOT_BASE64, NOT_URL, NOT_HTTPS, NO_CLAIM_PATH }
}

object SetupToken {

    /**
     * Decodes a setup token. Whitespace and newlines anywhere are removed (tokens often get wrapped
     * when copied on a phone). Both the standard and URL-safe Base64 alphabets are accepted, with or
     * without padding. The decoded text must be an https URL whose path contains "/claim/".
     */
    fun decode(input: String): SetupTokenResult {
        val compact = input.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return SetupTokenResult.Invalid(SetupTokenResult.Reason.EMPTY)

        // People sometimes paste the already-decoded claim URL. Accept it if it is otherwise valid.
        val decoded = if (compact.startsWith("https://", ignoreCase = true)) compact else decodeBase64(compact)
            ?: return SetupTokenResult.Invalid(SetupTokenResult.Reason.NOT_BASE64)

        val text = decoded.trim()
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return SetupTokenResult.Invalid(SetupTokenResult.Reason.NOT_URL)
        }
        if (uri.scheme == null || uri.host.isNullOrEmpty()) {
            return SetupTokenResult.Invalid(SetupTokenResult.Reason.NOT_URL)
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return SetupTokenResult.Invalid(SetupTokenResult.Reason.NOT_HTTPS)
        }
        val path = uri.rawPath ?: ""
        val idx = path.indexOf("/claim/")
        if (idx < 0 || idx + "/claim/".length >= path.length) {
            return SetupTokenResult.Invalid(SetupTokenResult.Reason.NO_CLAIM_PATH)
        }
        return SetupTokenResult.Valid(text)
    }

    private fun decodeBase64(s: String): String? {
        if (!BASE64_CHARS.matches(s)) return null
        val normalized = s.trimEnd('=').replace('-', '+').replace('_', '/')
        if (normalized.length % 4 == 1) return null
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = try {
            Base64.getDecoder().decode(padded)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .takeIf { str -> str.none { it.isISOControl() && it != '\n' && it != '\r' } }
        } catch (e: CharacterCodingException) {
            null
        }
    }

    private val BASE64_CHARS = Regex("[A-Za-z0-9+/_-]+={0,2}")
}
