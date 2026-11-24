package com.emotionalleaves.sprout.domain.text

import com.emotionalleaves.sprout.Fixtures
import com.emotionalleaves.sprout.data.simplefin.AccessUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test fun removesUserinfoFromUrls() {
        val out = Redactor.redact("failed to reach ${Fixtures.DEMO_ACCESS_URL}/accounts")
        assertFalse(out.contains("demo:demo"))
        assertEquals("failed to reach https://***@bridge.example.test/simplefin/accounts", out)
    }

    @Test fun removesBasicAuthHeaders() =
        assertEquals("header Basic ***", Redactor.redact("header Basic ZGVtbzpkZW1v"))

    @Test fun removesBareUserinfo() = assertFalse(Redactor.redact("x demo:demo@bridge.example.test y").contains("demo:demo"))

    @Test fun removesClaimToken() =
        assertEquals("https://bridge.example.test/claim/***", Redactor.redact("https://bridge.example.test/claim/AbC123"))

    @Test fun nullIsEmpty() = assertEquals("", Redactor.redact(null))

    @Test fun accessUrlToStringHidesEverything() =
        assertEquals("AccessUrl(***)", AccessUrl.parse(Fixtures.DEMO_ACCESS_URL).toString())

    @Test fun sanitizeStripsControlAndBidi() = assertEquals("ab c", Sanitize.text("a\u0000b‮ \n c", 50))
}
