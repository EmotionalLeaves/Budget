package com.emotionalleaves.sprout.ui

import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.sprout.ui.common.AppShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppShortcutsTest {
    @Test fun installsShortcutsThatOnlyCarryAnAllowedRoute() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppShortcuts.install(ctx)
        val list = ShortcutManagerCompat.getDynamicShortcuts(ctx)
        assertEquals(setOf("add", "review", "stats", "bills"), list.map { it.id }.toSet())
        list.forEach { s -> assertTrue(s.intent.getStringExtra(AppShortcuts.EXTRA_ROUTE) in AppShortcuts.ALLOWED) }
    }
}
