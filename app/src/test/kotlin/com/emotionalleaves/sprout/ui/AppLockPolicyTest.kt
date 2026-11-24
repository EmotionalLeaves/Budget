package com.emotionalleaves.sprout.ui

import com.emotionalleaves.sprout.ui.lock.AppLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private fun lock(enabled: Boolean = true, secure: Boolean = true, unlocked: Boolean = true, bg: Long? = null, now: Long = 1_000) =
        AppLockPolicy.shouldLock(enabled, secure, unlocked, bg, now)

    @Test fun offNeverLocks() = assertFalse(lock(enabled = false, unlocked = false))
    @Test fun noScreenLockNeverLocks() = assertFalse(lock(secure = false, unlocked = false))
    @Test fun freshStartLocks() = assertTrue(lock(unlocked = false))
    @Test fun shortTripAwayDoesNotLock() = assertFalse(lock(bg = 950, now = 1_000))
    @Test fun aMinuteAwayLocks() = assertTrue(lock(bg = 940, now = 1_000))
    @Test fun stayingInAppDoesNotLock() = assertFalse(lock(bg = null))
}
