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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.SproutApp
import com.emotionalleaves.sprout.domain.money.Money
import kotlinx.coroutines.flow.first

/** Home-screen widget: your top budgets with progress bars. Follows "Hide amounts". */
class BudgetsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    data class Line(val name: String, val text: String, val fraction: Float, val over: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lines = runCatching {
            val c = (context.applicationContext as SproutApp).container
            val hide = c.settings.current().privacyMode
            c.budgetRepository.observeMonth(c.budgetRepository.currentMonth()).first().budgeted
                .sortedByDescending { l -> l.budgetMinor?.let { b -> if (b > 0) l.spentMinor.toFloat() / b else 0f } ?: 0f }
                .take(4).map { l ->
                    val b = l.budgetMinor ?: 0L
                    Line(l.category.name, if (hide) "•••" else "${Money.formatWhole(l.spentMinor)} / ${Money.formatWhole(b)}",
                        if (b <= 0) 1f else (l.spentMinor.toFloat() / b).coerceIn(0f, 1f), l.spentMinor > b)
                }
        }.getOrDefault(emptyList())
        provideContent { GlanceTheme { Content(lines) } }
    }

    @Composable
    private fun Content(lines: List<Line>) {
        val bg = ColorProvider(day = Color(0xFFFCFAF5), night = Color(0xFF141A17))
        val fg = ColorProvider(day = Color(0xFF1A1C19), night = Color(0xFFE2E3DD))
        val muted = ColorProvider(day = Color(0xFF55574F), night = Color(0xFFBEC3BC))
        val green = ColorProvider(day = Color(0xFF17694A), night = Color(0xFF7DD8AA))
        val red = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFFF8A80))
        val track = ColorProvider(day = Color(0xFFE6E4DB), night = Color(0xFF2A302C))
        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
            Text("Budgets", style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            if (lines.isEmpty()) Text("Set budgets in Sprout", style = TextStyle(color = muted, fontSize = 12.sp))
            lines.forEach { l ->
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(l.name, style = TextStyle(color = fg, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                    Text(l.text, style = TextStyle(color = if (l.over) red else muted, fontSize = 12.sp))
                }
                LinearProgressIndicator(progress = l.fraction, modifier = GlanceModifier.fillMaxWidth().height(6.dp), color = if (l.over) red else green, backgroundColor = track)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) { runCatching { BudgetsWidget().updateAll(context) } }
    }
}

class BudgetsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetsWidget()
}
