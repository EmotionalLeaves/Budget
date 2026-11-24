package com.emotionalleaves.sprout.domain.text

/** The SimpleFIN spec says every string from the server must be sanitized before display. */
object Sanitize {
    /** Removes control and bidi-override characters, collapses whitespace and caps the length. */
    fun text(input: String, maxLength: Int): String {
        val sb = StringBuilder(minOf(input.length, maxLength))
        var lastSpace = false
        for (ch in input) {
            if (sb.length >= maxLength) break
            val isBidi = ch in '‪'..'‮' || ch in '⁦'..'⁩' || ch == '‎' || ch == '‏'
            if (isBidi || (Character.isISOControl(ch) && !ch.isWhitespace())) continue
            if (ch.isWhitespace()) {
                if (!lastSpace && sb.isNotEmpty()) sb.append(' ')
                lastSpace = true
            } else {
                sb.append(ch)
                lastSpace = false
            }
        }
        return sb.toString().trimEnd()
    }

    /** Server messages ("msg", legacy "errors" strings) are shown as plain text, at most this long. */
    const val MAX_MESSAGE = 300
}

/**
 * Removes anything that looks like a credential before text is logged, stored in SyncLog or shown.
 * Every exception message from the network layer passes through here.
 */
object Redactor {
    private val USERINFO_URL = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://)[^\s/@]+@""")
    private val BARE_USERINFO = Regex("""\b[^\s/:@]+:[^\s/@]+@[a-zA-Z0-9.-]+""")
    private val BASIC_AUTH = Regex("""(?i)\bbasic\s+[A-Za-z0-9+/=_-]{6,}""")
    private val CLAIM_PATH = Regex("""(?i)/claim/[^\s?#]+""")

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var s = USERINFO_URL.replace(text) { it.groupValues[1] + "***@" }
        s = BARE_USERINFO.replace(s, "***@host")
        s = BASIC_AUTH.replace(s, "Basic ***")
        s = CLAIM_PATH.replace(s, "/claim/***")
        return s
    }
}
