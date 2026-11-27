package com.emotionalleaves.sprout.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewPrefTest {
    @Test fun viewChoicesAreRememberedSeparately() = runBlocking {
        TestEnv().use { env ->
            assertNull(env.settings.viewPref("stats-period").first())
            env.settings.setViewPref("stats-period", "YEAR")
            env.settings.setViewPref("bills-calendar", "true")
            assertEquals("YEAR", env.settings.viewPref("stats-period").first())
            assertEquals("true", env.settings.viewPref("bills-calendar").first())
            env.settings.setViewPref("stats-period", "THIS_MONTH")
            assertEquals("THIS_MONTH", env.settings.viewPref("stats-period").first())
            // Remembered views never touch the other settings.
            assertEquals(false, env.settings.settings.first().onboardingComplete)
        }
    }
}
