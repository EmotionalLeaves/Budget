package com.emotionalleaves.sprout.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.R

/**
 * Long-press Sprout's icon for straight-to shortcuts. Each opens Sprout on a screen by its route; nothing
 * else is in the intent. Set from code (not XML) so the debug build's different package works too.
 */
object AppShortcuts {
    const val EXTRA_ROUTE = "com.emotionalleaves.sprout.ROUTE"

    /** Routes a shortcut may open; anything else in the intent is ignored. */
    val ALLOWED = setOf("add", "review", "stats", "bills")

    fun install(context: Context) {
        fun shortcut(id: String, label: String, long: String, icon: Int) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_ROUTE, id))
            .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(
                shortcut("add", "Add purchase", "Add a cash purchase", R.drawable.ic_shortcut_add),
                shortcut("review", "Sort purchases", "Sort uncategorized purchases", R.drawable.ic_shortcut_sort),
                shortcut("stats", "Stats", "Spending stats and money flow", R.drawable.ic_shortcut_stats),
                shortcut("bills", "Bills", "Bills coming up", R.drawable.ic_shortcut_bills),
            ))
        }
    }
}
