package com.emotionalleaves.sprout.domain.categorize

/**
 * Turns a raw bank description into a stable merchant key, e.g.
 * "POS DEBIT CARD PURCHASE HY-VEE #1234 LINCOLN NE" -> "HY-VEE LINCOLN".
 *
 * Pure and deterministic: the same description always yields the same key, so learned rules keep working.
 */
object MerchantNormalizer {

    /** Max tokens kept. Three is enough to tell merchants apart without including store-specific noise. */
    private const val MAX_TOKENS = 3

    private val LEADING_PREFIXES = listOf(
        "PURCHASE AUTHORIZED ON",
        "DEBIT CARD PURCHASE",
        "DEBIT PURCHASE",
        "CARD PURCHASE",
        "CHECKCARD",
        "POS PURCHASE",
        "POS ",
        "ACH ",
        "RECURRING ",
        "PAYPAL *",
        "PAYPAL*",
        "PP*",
        "SQ *",
        "SQ*",
        "TST*",
        "TST *",
        "GOOGLE *",
        "GOOGLE*",
    )

    private val US_STATES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY",
        "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND",
        "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC",
    )

    private val PHONE = Regex("""\(?\b\d{3}\)?[\s.-]\d{3}[\s.-]\d{4}\b""")
    private val DATE = Regex("""\b\d{1,2}[/-]\d{1,2}([/-]\d{2,4})?\b""")
    private val CARD_FRAGMENT = Regex("""\b(CARD|CRD)\s*(ENDING\s*(IN)?\s*)?[X*#]*\d{2,}\b|\b[X*]{2,}\d{2,}\b""")
    private val REFERENCE = Regex("""\b(REF|REFERENCE|CONF|CONFIRMATION|TRACE|AUTH|ID|TRN|PPD|WEB|CCD)\s*[#:]?\s*[A-Z0-9]*\d[A-Z0-9]*\b""")
    private val STORE_NUMBER = Regex("""#\s*[A-Z0-9-]*\d[A-Z0-9-]*""")
    private val LONG_DIGITS = Regex("""\d{4,}""")
    private val DOMAIN = Regex("""\.(COM|NET|ORG|CO|IO|US|APP|TV)\b""")
    private val STAR_CODE = Regex("""\*\s*[A-Z0-9]*\d[A-Z0-9]*""")
    private val AMAZON = Regex("""^(AMAZON|AMZN)\b.*""")

    fun normalize(description: String): String {
        var s = description.uppercase().replace('’', '\'').trim()
        s = s.replace(Regex("""\s+"""), " ")

        // Strip processor and bank prefixes, repeatedly ("POS DEBIT CARD PURCHASE SQ *CAFE").
        var changed = true
        while (changed) {
            changed = false
            if (s.startsWith("APPLE.COM/BILL")) {
                s = "APPLE"
                break
            }
            for (p in LEADING_PREFIXES) {
                if (s.startsWith(p)) {
                    s = s.removePrefix(p).trimStart(' ', '-', ':')
                    // "PURCHASE AUTHORIZED ON 03/14" -> also drop the date that follows.
                    s = s.replace(Regex("""^\d{1,2}/\d{1,2}(/\d{2,4})?\s*"""), "")
                    changed = true
                    break
                }
            }
        }
        if (s.contains("APPLE.COM/BILL")) return "APPLE"

        // AMAZON.COM*AB12CD, AMZN MKTP US*AB12 -> AMAZON
        // Prime memberships stay distinct so they can be a subscription.
        if (AMAZON.matches(s) && !s.contains("PRIME")) return "AMAZON"

        s = PHONE.replace(s, " ")
        s = DATE.replace(s, " ")
        s = CARD_FRAGMENT.replace(s, " ")
        s = REFERENCE.replace(s, " ")
        s = STAR_CODE.replace(s, " ")
        s = STORE_NUMBER.replace(s, " ")
        s = s.split(' ').filter { it.isNotEmpty() }.mapIndexedNotNull { i, token ->
            when {
                // Web addresses: "NETFLIX.COM" first -> NETFLIX; later ones ("HELP.UBER.COM", "G.CO/HELPPAY") are noise.
                DOMAIN.containsMatchIn(token) ->
                    if (i == 0) token.removePrefix("WWW.").substringBefore('.') else null
                // Codes with 3+ digits (store ids, order ids like P1A2B3C4D5, S123456789) are noise.
                token.count(Char::isDigit) >= 3 -> null
                else -> token
            }
        }.joinToString(" ")
        s = LONG_DIGITS.replace(s, " ")
        // Keep letters, digits, &, ' and inner hyphens; everything else becomes a space.
        s = s.replace(Regex("""[^A-Z0-9&'\- ]"""), " ")
        s = s.replace("'", "")

        val tokens = s.split(' ')
            .map { it.trim('-') }
            .filter { it.isNotEmpty() && it.any(Char::isLetter) }
            .toMutableList()
        // Trailing US state code ("LINCOLN NE"), only when something else remains.
        while (tokens.size > 1 && tokens.last() in US_STATES) tokens.removeAt(tokens.lastIndex)
        // A trailing "US" country code is noise as well.
        while (tokens.size > 1 && tokens.last() == "US") tokens.removeAt(tokens.lastIndex)

        val key = tokens.take(MAX_TOKENS).joinToString(" ")
        return key.ifEmpty { description.uppercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().take(40).ifEmpty { "UNKNOWN" } }
    }

    /** A friendly name for UI text such as "Always use this for Hy-Vee Lincoln". */
    fun displayName(merchantKey: String): String =
        merchantKey.split(' ').joinToString(" ") { token ->
            if (token.length <= 3 && token.none { it.isDigit() } && token in SHORT_UPPER) token
            else token.lowercase().split('-').joinToString("-") { part -> part.replaceFirstChar { it.titlecase() } }
        }

    private val SHORT_UPPER = setOf("BP", "ATM", "ACH", "USA", "CVS", "KFC", "AT&T", "QT", "HBO", "IHOP", "ALDI")
}
