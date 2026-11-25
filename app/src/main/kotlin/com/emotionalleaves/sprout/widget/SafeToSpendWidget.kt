package com.emotionalleaves.sprout.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.SproutApp
import com.emotionalleaves.sprout.domain.money.Money
import kotlinx.coroutines.flow.first

/** What the widget shows; computed when it updates. */
data class WidgetData(val title: String, val amount: String, val detail: String, val negative: Boolean)

/** Home-screen widget: today's safe to spend. Respects "Hide amounts". Tapping opens Sprout. */
class SafeToSpendWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): WidgetData = runCatching {
        val c = (context.applicationContext as SproutApp).container
        val settings = c.settings.current()
        if (!settings.onboardingComplete) return WidgetData("Sprout", "Set up", "Open Sprout to connect", false)
        val m = c.budgetRepository.observeMonth(c.budgetRepository.currentMonth()).first()
        val safe = m.safeToSpend ?: return WidgetData("Sprout", "No budgets", "Tap to set budgets", false)
        if (settings.privacyMode) return WidgetData("Safe to spend", "$ •••", "Tap to view", false)
        WidgetData(
            "Safe to spend",
            Money.formatWhole(safe.amountMinor),
            listOfNotNull(safe.perDayMinor?.let { "≈ ${Money.formatWhole(it)}/day" }, safe.daysRemaining?.let { "$it days left" }).joinToString(" · "),
            safe.amountMinor < 0,
        )
    }.getOrElse { WidgetData("Sprout", "—", "Tap to open", false) }

    @Composable
    private fun Content(d: WidgetData) {
        val green = ColorProvider(day = Color(0xFF17694A), night = Color(0xFF0F5137))
        val white = ColorProvider(day = Color.White, night = Color(0xFFEFFFF6))
        val soft = ColorProvider(day = Color(0xFFDDF3E6), night = Color(0xFFC7EBD6))
        Column(
            GlanceModifier.fillMaxSize().background(green).cornerRadius(24.dp).padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(d.title, style = TextStyle(color = soft, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Text(d.amount, style = TextStyle(color = if (d.negative) ColorProvider(Color(0xFFFFB4A9), Color(0xFFFFB4A9)) else white, fontSize = 30.sp, fontWeight = FontWeight.Bold))
            if (d.detail.isNotEmpty()) Text(d.detail, style = TextStyle(color = soft, fontSize = 12.sp))
        }
    }

    companion object {
        /** Refresh every placed widget; quiet if there are none or anything fails. */
        suspend fun refresh(context: Context) { runCatching { SafeToSpendWidget().updateAll(context) } }
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()
}
