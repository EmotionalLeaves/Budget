package com.emotionalleaves.sprout.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val json = """
        {"tag_name":"v1.16.0","html_url":"https://github.com/EmotionalLeaves/Budget/releases/tag/v1.16.0","draft":false,"prerelease":false,
         "body":"## [1.16.0]\n\n### Added\n- **Auto-update**: tap to install.\n- New look\n- Charts\n- Fourth",
         "assets":[{"name":"notes.txt","url":"https://api.github.com/x/1","browser_download_url":"https://github.com/x/notes.txt","size":3},
                   {"name":"sprout-v1.16.0-40.apk","url":"https://api.github.com/repos/EmotionalLeaves/Budget/releases/assets/2",
                    "browser_download_url":"https://github.com/EmotionalLeaves/Budget/releases/download/v1.16.0/sprout-v1.16.0-40.apk",
                    "size":2300000,"digest":"sha256:48D3EA936C8B5B68581FC8227E89F8F9BD5A79FC9D2ED26B58DAC505A957F42C"}]}
    """.trimIndent()

    @Test fun parsesTheApkAsset() {
        val r = UpdateCheck.parseRelease(json)!!
        assertEquals("1.16.0", r.version)
        assertEquals("sprout-v1.16.0-40.apk", r.apkName)
        assertEquals(2_300_000L, r.sizeBytes)
        assertEquals("48d3ea936c8b5b68581fc8227e89f8f9bd5a79fc9d2ed26b58dac505a957f42c", r.sha256)
        assertEquals("• Auto-update: tap to install.\n• New look\n• Charts", r.notes)
    }

    @Test fun prereleasesBrokenJsonAndNoApkAreIgnored() {
        assertNull(UpdateCheck.parseRelease(json.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(UpdateCheck.parseRelease("{not json"))
        assertNull(UpdateCheck.parseRelease("""{"tag_name":"v2","assets":[]}"""))
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateCheck.isNewer("v2.0", "1.99.99"))
        assertFalse(UpdateCheck.isNewer("1.9.0", "1.9.0"))
        assertFalse(UpdateCheck.isNewer("1.9.0", "1.10.0"))
        assertFalse(UpdateCheck.isNewer("1.9.0-build.5", "1.9.0"))
    }

    @Test fun keyOnlyGoesToTheApi() {
        val r = UpdateCheck.parseRelease(json)!!
        assertEquals(r.apiUrl, UpdateCheck.downloadUrl(r, hasKey = true))
        assertEquals(r.browserUrl, UpdateCheck.downloadUrl(r, hasKey = false))
        assertTrue(UpdateCheck.mayCarryKey(r.apiUrl))
        assertFalse(UpdateCheck.mayCarryKey(r.browserUrl))
        assertFalse(UpdateCheck.mayCarryKey("https://objects.githubusercontent.com/x"))
        assertFalse(UpdateCheck.mayCarryKey("http://api.github.com/x"))
        assertFalse(UpdateCheck.mayCarryKey("https://api.github.com.evil.test/x"))
    }
}
