package com.emotionalleaves.sprout.data.simplefin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class AccessUrlTest {
    private fun basic(s: String) = "Basic " + Base64.getEncoder().encodeToString(s.toByteArray())

    @Test fun splitsCredentialsFromUrl() {
        val a = AccessUrl.parse("https://user123:secretPass@bridge.simplefin.org/simplefin")!!
        assertEquals("https://bridge.simplefin.org/simplefin", a.baseUrl)
        assertEquals("https://bridge.simplefin.org/simplefin/accounts", a.accountsUrl())
        assertEquals(basic("user123:secretPass"), a.authorizationHeader())
    }

    @Test fun percentDecodesCredentials() {
        val a = AccessUrl.parse("https://us%40er:p%3Ass+w@host.example/sf/")!!
        assertEquals(basic("us@er:p:ss+w"), a.authorizationHeader())
        assertEquals("https://host.example/sf", a.baseUrl)
    }

    @Test fun keepsPort() =
        assertEquals("https://h.example:8443/x", AccessUrl.parse("https://u:p@h.example:8443/x")!!.baseUrl)

    @Test fun trimsWhitespace() =
        assertEquals("https://h.example/x", AccessUrl.parse("  https://u:p@h.example/x\n")!!.baseUrl)

    @Test fun rejectsHttp() = assertNull(AccessUrl.parse("http://u:p@h.example/x"))
    @Test fun rejectsMissingCredentials() = assertNull(AccessUrl.parse("https://h.example/x"))
    @Test fun rejectsMissingPassword() = assertNull(AccessUrl.parse("https://u@h.example/x"))
    @Test fun rejectsGarbage() = assertNull(AccessUrl.parse("not a url"))

    @Test fun toStringHidesSecrets() =
        assertFalse(AccessUrl.parse("https://u:topsecret@h.example/x").toString().contains("topsecret"))
}
