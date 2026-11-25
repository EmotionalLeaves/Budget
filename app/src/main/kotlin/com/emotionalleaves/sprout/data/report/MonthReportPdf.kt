package com.emotionalleaves.sprout.data.report

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.domain.insights.Recap
import com.emotionalleaves.sprout.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A one-page (or two) PDF of a month: totals, budgets vs spending, top categories and the biggest purchase.
 * Made on the phone with Android's PdfDocument; nothing is uploaded. You choose where to share it.
 */
object MonthReportPdf {
    private const val W = 612
    private const val H = 792
    private const val M = 48f

    suspend fun write(context: Context, recap: Recap, budget: BudgetMonth?): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var canvas = page.canvas
        var y = M
        val title = Paint().apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val h2 = Paint().apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val muted = Paint(body).apply { color = 0xFF666666.toInt() }
        val green = Paint().apply { color = 0xFF17694A.toInt(); isAntiAlias = true }
        val track = Paint().apply { color = 0xFFE6E4DB.toInt() }
        val red = Paint().apply { color = 0xFFB3261E.toInt() }

        fun ensure(space: Float) {
            if (y + space > H - M) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page.canvas
                y = M
            }
        }
        fun line(text: String, p: Paint = body, gap: Float = 16f) { ensure(gap); canvas.drawText(text, M, y, p); y += gap }
        fun row(left: String, right: String, p: Paint = body) {
            ensure(16f); canvas.drawText(left, M, y, p); canvas.drawText(right, W - M - p.measureText(right), y, p); y += 16f
        }
        fun bar(fraction: Float, over: Boolean) {
            ensure(12f)
            canvas.drawRoundRect(M, y - 8, W - M, y, 4f, 4f, track)
            canvas.drawRoundRect(M, y - 8, M + (W - 2 * M) * fraction.coerceIn(0f, 1f), y, 4f, 4f, if (over) red else green)
            y += 14f
        }

        val monthName = recap.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        line("Sprout — $monthName", title, 30f)
        line(recap.headline(), muted, 24f)
        line("Totals", h2, 20f)
        row("Spent", Money.format(recap.spentMinor))
        if (recap.incomeMinor > 0) { row("Came in", Money.format(recap.incomeMinor)); row("Kept", Money.format(recap.savedMinor)) }
        recap.changePercent?.let { row("vs month before", (if (it > 0) "+" else "") + "$it%") }
        row("No-spend days", recap.noSpendDays.toString())
        recap.biggest?.let { (n, v) -> row("Biggest purchase", "$n · ${Money.format(v)}") }
        y += 10f
        if (budget != null && budget.budgeted.isNotEmpty()) {
            line("Budgets", h2, 20f)
            budget.budgeted.forEach { l ->
                val b = l.budgetMinor ?: 0L
                row(l.category.name, "${Money.format(l.spentMinor)} of ${Money.format(b)}")
                bar(if (b <= 0) 1f else l.spentMinor.toFloat() / b, l.spentMinor > b)
            }
            y += 6f
        }
        if (recap.topCategories.isNotEmpty()) {
            line("Where it went", h2, 20f)
            val top = recap.topCategories.first().second.coerceAtLeast(1)
            recap.topCategories.forEach { (n, v) -> row(n, Money.format(v)); bar(v.toFloat() / top, false) }
        }
        y += 10f
        line("Made with Sprout on ${java.time.LocalDate.now()}. Read-only; numbers come from your bank and your entries.", muted)
        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "sprout-${recap.month}.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        out
    }
}
