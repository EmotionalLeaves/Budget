package com.emotionalleaves.sprout.data.simplefin

import com.emotionalleaves.sprout.data.simplefin.SetupTokenResult.Reason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SetupTokenTest {
    private val demoUrl = "https://bridge.simplefin.org/simplefin/claim/demo"
    private fun std(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun urlSafeNoPad(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test fun decodesSpecExample() {
        val r = SetupToken.decode("aHR0cHM6Ly9icmlkZ2Uuc2ltcGxlZmluLm9yZy9zaW1wbGVmaW4vY2xhaW0vZGVtbw==")
        assertEquals(SetupTokenResult.Valid(demoUrl), r)
    }

    @Test fun trimsWhitespaceAndNewlinesInside() {
        val token = std(demoUrl)
        val wrapped = "  " + token.substring(0, 10) + "\n" + token.substring(10, 30) + " \r\n" + token.substring(30) + "\n"
        assertEquals(SetupTokenResult.Valid(demoUrl), SetupToken.decode(wrapped))
    }

    @Test fun acceptsUrlSafeWithoutPadding() {
        val url = "https://bridge.simplefin.org/simplefin/claim/zz?q=~~~?"
        // Encodes with both "-" and "_", which only the URL-safe alphabet has.
        check(urlSafeNoPad(url).contains("-") && urlSafeNoPad(url).contains("_"))
        assertEquals(SetupTokenResult.Valid(url), SetupToken.decode(urlSafeNoPad(url)))
    }

    @Test fun acceptsStandardWithoutPadding() {
        assertEquals(SetupTokenResult.Valid(demoUrl), SetupToken.decode(std(demoUrl).trimEnd('=')))
    }

    @Test fun rejectsEmpty() = assertEquals(SetupTokenResult.Invalid(Reason.EMPTY), SetupToken.decode("   \n"))

    @Test fun rejectsNonBase64() =
        assertEquals(SetupTokenResult.Invalid(Reason.NOT_BASE64), SetupToken.decode("this is not a token!"))

    @Test fun rejectsBinaryGarbage() =
        assertEquals(SetupTokenResult.Invalid(Reason.NOT_BASE64), SetupToken.decode("////////"))

    @Test fun rejectsHttp() = assertEquals(
        SetupTokenResult.Invalid(Reason.NOT_HTTPS),
        SetupToken.decode(std("http://bridge.simplefin.org/simplefin/claim/demo")),
    )

    @Test fun rejectsMissingClaimPath() = assertEquals(
        SetupTokenResult.Invalid(Reason.NO_CLAIM_PATH),
        SetupToken.decode(std("https://bridge.simplefin.org/simplefin/create")),
    )

    @Test fun rejectsClaimWithoutToken() = assertEquals(
        SetupTokenResult.Invalid(Reason.NO_CLAIM_PATH),
        SetupToken.decode(std("https://bridge.simplefin.org/simplefin/claim/")),
    )

    @Test fun rejectsNonUrlText() = assertEquals(
        SetupTokenResult.Invalid(Reason.NOT_URL),
        SetupToken.decode(std("hello world")),
    )

    @Test fun acceptsPastedClaimUrl() =
        assertEquals(SetupTokenResult.Valid(demoUrl), SetupToken.decode(demoUrl))
}
