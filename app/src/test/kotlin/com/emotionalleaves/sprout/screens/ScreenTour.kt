package com.emotionalleaves.sprout.screens

import androidx.compose.ui.test.performTextInput
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.sprout.MainActivity
import com.emotionalleaves.sprout.SproutApp
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.CategoryBudgetEntity
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Not a test of behaviour: renders the main screens with realistic data into PNGs, for visual review.
 * Runs only with -Dsprout.screens=<dir>, so normal CI is unaffected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1900dp-xhdpi")
class ScreenTour {
    @get:Rule val rule = createEmptyComposeRule()
    private val out = System.getProperty("sprout.screens")
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shot(name: String) {
        settle()
        var bmp: Bitmap? = null
        // The first draw records Compose's latest frame; the second one paints it.
        repeat(2) {
            scenario!!.onActivity { act -> act.window.decorView.let { v -> v.invalidate(); v.draw(android.graphics.Canvas(Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888))) } }
            settle()
        }
        scenario!!.onActivity { act ->
            val v = act.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(b)
            v.draw(canvas)
            // Dialogs and sheets live in their own windows: paint them on top, with the dim behind them.
            for (root in allRootViews().filter { it !== v && it.isShown }) {
                root.invalidate(); root.draw(android.graphics.Canvas(Bitmap.createBitmap(maxOf(1, root.width), maxOf(1, root.height), Bitmap.Config.ARGB_8888)))
                canvas.drawColor(0x66000000)
                val loc = IntArray(2); root.getLocationOnScreen(loc)
                val lp = root.layoutParams as? android.view.WindowManager.LayoutParams
                val x = if (loc[0] == 0 && root.width < v.width) (v.width - root.width) / 2 else loc[0]
                val y = if (loc[1] == 0 && root.height < v.height) {
                    if (lp != null && (lp.gravity and android.view.Gravity.BOTTOM) == android.view.Gravity.BOTTOM) v.height - root.height else (v.height - root.height) / 2
                } else loc[1]
                canvas.save(); canvas.translate(x.toFloat(), y.toFloat()); root.draw(canvas); canvas.restore()
            }
            bmp = b
        }
        File(out).mkdirs()
        File(out, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun allRootViews(): List<android.view.View> = runCatching {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val g = cls.getMethod("getInstance").invoke(null)
        val f = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (f.get(g) as List<android.view.View>).toList()
    }.getOrDefault(emptyList())

    private fun settle() {
        repeat(16) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            runCatching { rule.mainClock.advanceTimeBy(150) }
            Thread.sleep(90)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
        repeat(5) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }
    }

    private fun tap(text: String, last: Boolean = text in setOf("Home", "Activity", "Budgets", "Bills", "More")) {
        settle()
        val clickable = rule.onAllNodes(androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.hasClickAction())
        val n = clickable.fetchSemanticsNodes().size
        if (n > 0) {
            clickable[if (last) n - 1 else 0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        } else {
            rule.onAllNodesWithText(text)[0].performClick()
        }
        settle()
    }

    private fun back() {
        val n = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"))
        if (n.fetchSemanticsNodes().isNotEmpty()) n[0].performClick() else scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun seed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SproutApp>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val c = app.container
        c.syncRepository.ensureSeeded()
        c.settings.setOnboardingComplete(true)
        if (System.getProperty("sprout.dark") != null) c.settings.setThemeMode(2)
        val z = ZoneId.systemDefault()
        val now = System.currentTimeMillis() / 1000
        c.db.accounts().upsert(listOf(
            acct("chk", "Everyday Checking", AccountType.CHECKING, 184_233),
            acct("sav", "High-Yield Savings", AccountType.SAVINGS, 642_000),
            acct("cc", "Rewards Card", AccountType.CREDIT, -48_712),
        ))
        val cats = c.db.categories().getAll()
        val idByKey = cats.mapNotNull { k -> k.key?.let { it to k.id } }.toMap()
        val resolver = CategoryResolver(emptyList(), idByKey)
        val today = LocalDate.now(z)
        val txs = mutableListOf<TransactionEntity>()
        var n = 0
        fun add(daysAgo: Long, desc: String, amount: Long, account: String = "chk") {
            val at = today.minusDays(daysAgo).atTime(12, 0).atZone(z).toEpochSecond()
            val key = MerchantNormalizer.normalize(desc)
            val r = resolver.resolve(desc, key, amount)
            txs += TransactionEntity(
                id = "t${n++}", accountId = account, remoteId = "r$n", postedAt = at, transactedAt = null, occurredAt = at,
                amountMinor = amount, currency = "USD", description = desc, merchantKey = key, pending = daysAgo == 0L,
                categoryId = r.categoryId ?: idByKey.values.first(), categorySource = r.source, note = null, excludedFromBudget = false,
                recurringChargeId = null, alertedAt = now, importedAt = 0, updatedAt = now, reviewedAt = if (daysAgo < 3) null else now,
            )
        }
        for (m in 0..4) {
            val base = m * 30L
            add(base + 2, "ACME CORP PAYROLL", 212_500)
            add(base + 16, "ACME CORP PAYROLL", 212_500)
            add(base + 5, "OAKWOOD APARTMENTS RENT", -95_000)
            add(base + 8, "NETFLIX.COM", -1_549, "cc")
            add(base + 11, "SPOTIFY USA", -1_199, "cc")
            add(base + 12, "LINCOLN ELECTRIC SYSTEM", -8_412L - m * 300L)
            add(base + 20, "VERIZON WIRELESS", -6_500)
            add(base + 3, "HY-VEE #1234", -8_734L - m * 410L, "cc")
            add(base + 10, "HY-VEE #1234", -6_122, "cc")
            add(base + 17, "TRADER JOE'S #551", -4_390, "cc")
            add(base + 4, "STARBUCKS STORE 0921", -675, "cc")
            add(base + 9, "STARBUCKS STORE 0921", -712, "cc")
            add(base + 6, "CHIPOTLE 2231", -1_438, "cc")
            add(base + 14, "SHELL OIL 5741", -4_210, "chk")
            add(base + 19, "AMAZON MKTPLACE PMTS", -3_299, "cc")
            add(base + 23, "TARGET 00012", -5_612, "cc")
        }
        add(0, "DOORDASH*THAI BASIL", -2_845, "cc")
        add(1, "MICHAELS STORES 9921", -3_418, "cc")
        add(1, "VENMO PAYMENT SAM", -2_000)
        c.db.transactions().upsert(txs)
        fun b(key: String, amt: Long) = idByKey[key]?.let { CategoryBudgetEntity(it, amt, now) }
        listOfNotNull(b("groceries", 40_000), b("eating_out", 12_000), b("shopping", 15_000), b("gas", 8_000), b("entertainment", 5_000))
            .let { c.db.budgets().upsertDefaults(it) }
        c.recurringRepository.detect()
        // Some extras so goal, challenge and tag screens have something to show.
        c.goalRepository.save(null, "Trip to Denver", 120_000, today.plusMonths(5), "sav", 45_000)
        c.goalRepository.save(null, "New laptop", 90_000, null, null, 12_000)
        idByKey["eating_out"]?.let { c.challengeRepository.add(com.emotionalleaves.sprout.domain.challenge.ChallengeType.NO_SPEND_CATEGORY, "No eating out", 7, it) }
        c.tagRepository.getOrCreate("Gifts")?.let { tag -> txs.filter { it.description.startsWith("TARGET") }.take(3).forEach { t -> c.tagRepository.attach(t.id, tag) } }
    }

    private fun acct(id: String, name: String, type: AccountType, bal: Long) = AccountEntity(
        id = id, source = AccountSource.MANUAL, remoteId = id, connId = null, name = name, displayName = null, type = type,
        currency = "USD", balanceMinor = bal, availableBalanceMinor = null, balanceDate = System.currentTimeMillis() / 1000,
        includeInTotals = true, hidden = false, firstSyncCompleted = true, lastSyncedAt = System.currentTimeMillis() / 1000,
        createdAt = System.currentTimeMillis() / 1000 - 200 * 86_400,
    )

    /**
     * Runs in normal CI (no screenshots): opens every main screen and every Explore tool with realistic data and
     * scrolls Home to the bottom, so a screen that crashes while drawing (like 2.29's goal card) fails the build.
     */
    @Test fun smokeEveryScreen() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            settle()
            rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasText("Customize Home"))
            settle()
            for (tab in listOf("Activity", "Budgets", "Bills", "More")) { tap(tab); settle() }
            for (tool in listOf("Stats", "Reports", "Goals", "Challenges", "Year in review", "Net worth", "Year view", "Tags", "Debt plan", "Sort purchases")) {
                tap("More")
                rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasText(tool))
                tap(tool); settle()
                back(); settle()
            }
        }
    }

    /** The main screens at 200% text size (an accessibility setting people really use). Run with -Psprout.only=large. */
    @Test @Config(fontScale = 2.0f) fun tourLargeText() {
        assumeTrue(out != null && System.getProperty("sprout.only") == "large")
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            shot("x-01-home")
            tap("Activity"); shot("x-02-activity")
            tap("Budgets"); shot("x-03-budgets")
            tap("Bills"); shot("x-04-bills")
            tap("More"); shot("x-05-more")
        }
    }

    @Test fun tour() {
        assumeTrue(out != null && System.getProperty("sprout.only") != "large")
        if (System.getProperty("sprout.only") == "onboard") {
            androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<SproutApp>())
            ActivityScenario.launch(MainActivity::class.java).use {
                scenario = it
                shot("o-01-welcome")
                runCatching { tap("I have a setup token"); shot("o-01b-token"); back() }
                runCatching { tap("Skip — I'll import a CSV"); shot("o-02-after-skip") }
                runCatching { tap("Home"); shot("o-03-empty-home") }
                runCatching { tap("Activity"); shot("o-04-empty-activity") }
                runCatching { tap("Budgets"); shot("o-05-empty-budgets") }
                runCatching { tap("Bills"); shot("o-06-empty-bills") }
            }
            return
        }
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            Thread.sleep(1500)
            val p = if (System.getProperty("sprout.dark") != null) "d" else "l"
            fun s(n: String) = shot("$p-$n")
            val only = System.getProperty("sprout.only")
            if (only == null) {
            s("01-home")
            runCatching { tap("Review"); s("01b-inbox"); back() }
            tap("Activity"); s("02-activity")
            tap("Budgets"); s("03-budgets")
            runCatching { tap("Flex"); s("03b-flex"); tap("Per category") }
            tap("Bills"); s("04-bills")
            runCatching { tap("Calendar"); s("04b-bill-calendar"); tap("List") }
            tap("More"); s("05-more")
            for ((label, name) in listOf("Reports" to "06-reports", "Year in review" to "07-year", "Net worth" to "08-networth",
                "Year view" to "09-yearview", "Tags" to "10-tags", "Goals" to "11-goals", "Challenges" to "12-challenges",
                "Debt plan" to "13-debt")) {
                runCatching { tap(label); s(name); if (label == "Reports") { tap("Compare"); s("06b-compare"); back() }; back() }
            }
            }
            if (only == "detail2") {
                fun t(n: String, block: () -> Unit) = runCatching(block).onFailure { System.err.println("TOUR $n: " + it.toString().take(200)) }
                t("merchant") {
                    tap("More"); tap("Stats"); tap("Places")
                    rule.onAllNodes(androidx.compose.ui.test.hasText("Hy-Vee") and androidx.compose.ui.test.hasClickAction())[0]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
                    s("50-merchant"); back(); back()
                }
                t("select") {
                    tap("Activity")
                    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Hy-Vee", substring = true))[0]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick); settle()
                    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Netflix", substring = true))[0]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
                    s("56-select")
                    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Stop selecting"))[0].performClick(); settle()
                }
                t("account") {
                    // Show only the Accounts card on Home so it's on screen, open Checking, then put Home back.
                    val app = ApplicationProvider.getApplicationContext<SproutApp>()
                    runBlocking { com.emotionalleaves.sprout.ui.home.HomeCards.forEach { (k, _) -> if (k != "accounts") app.container.settings.setHomeCard(k, false) } }
                    tap("Home"); settle()
                    tap("Everyday Checking"); s("57-account"); back()
                    runBlocking { com.emotionalleaves.sprout.ui.home.HomeCards.forEach { (k, _) -> app.container.settings.setHomeCard(k, true) } }
                }
                t("cats") { tap("More"); tap("Categories"); s("51-categories"); back() }
                t("rules") { tap("More"); tap("Category rules"); s("52-rules"); back() }
                t("review") {
                    tap("Activity")
                    val n = rule.onAllNodes(androidx.compose.ui.test.hasText("Check") and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().size
                    rule.onAllNodes(androidx.compose.ui.test.hasText("Check") and androidx.compose.ui.test.hasClickAction())[n - 1]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
                    s("58-review"); back()
                }
                t("health") {
                    tap("Home")
                    rule.onAllNodes(androidx.compose.ui.test.hasText("Money health", substring = true) and androidx.compose.ui.test.hasClickAction())[0]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
                    s("53-health"); back()
                }
                t("networth") { tap("More"); tap("Net worth"); s("54-networth"); back() }
                t("challenge") { tap("More"); tap("Challenges"); tap("No eating out"); s("55-challenge"); back() }
                return@use
            }
            if (only == "extras") {
                fun t(n: String, block: () -> Unit) = runCatching(block).onFailure { System.err.println("TOUR $n: " + it.toString().take(200)) }
                t("search") {
                    tap("Activity")
                    rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput(">5 this month"); settle()
                    Thread.sleep(400); settle(); s("59-search-dates")
                    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Clear search"))[0].performClick(); settle()
                }
                t("goals") { tap("More"); tap("Goals"); s("60-goals"); back() }
                t("stats") {
                    tap("More"); tap("Stats"); s("64-stats"); tap("Flow"); s("64b-stats-flow")
                    tap("Patterns"); s("65-stats-patterns"); tap("Places"); s("66-stats-places"); tap("Trends"); s("67-stats-trends")
                    tap("12 months"); tap("Overview"); s("68-stats-year"); back()
                }
                t("challenges") { tap("More"); tap("Challenges"); s("61-challenges"); back() }
                t("tags") { tap("More"); tap("Tags"); s("62-tags"); tap("#Gifts"); s("63-tag"); back(); back() }
                t("debt") { tap("More"); tap("Debt plan"); s("64-debt"); back() }
                return@use
            }
            if (only == "settings") {
                tap("More")
                val list = rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
                list.performScrollToNode(androidx.compose.ui.test.hasText("Notifications", substring = true)); settle(); s("40-settings-a")
                list.performScrollToNode(androidx.compose.ui.test.hasText("Open-source licenses")); settle(); s("41-settings-b")
                return@use
            }
            if (only == "detail") {
                fun t(n: String, block: () -> Unit) = runCatching(block).onFailure { System.err.println("TOUR $n: " + it.toString().take(200) + " @ " + it.stackTrace.firstOrNull { e -> e.className.contains("ScreenTour") }) }
                t("sheet") { tap("Activity")
                    rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Hy-Vee", substring = true))[0]
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle(); Thread.sleep(800); settle(); s("30-tx-sheet"); back() }
                t("add") { tap("Activity"); rule.onAllNodes(androidx.compose.ui.test.hasText("Add"), useUnmergedTree = true)[0].performClick(); settle(); s("31-add-sheet"); back() }
                t("cat") { tap("Budgets"); tap("Groceries"); s("32-category"); back() }
                t("backup") { tap("More"); tap("Backup and restore"); s("33-backup"); back() }
                t("import") { tap("More"); tap("Import CSV"); s("34-import"); back() }
                t("more-bottom") { tap("More"); s("35-more-full") }
                return@use
            }
            // Dialogs and sheets.
            tap("More")
            runCatching { tap("Tags"); tap("Auto-tag"); s("20-autotag-dialog"); tap("Cancel"); back() }.onFailure { System.err.println("TOUR: " + it.toString().take(300)) }
            runCatching { tap("More"); tap("Categories"); tap("Groceries"); s("21-category-dialog"); tap("Cancel"); back() }.onFailure { System.err.println("TOUR: " + it.toString().take(300)) }
            runCatching { tap("More"); tap("Category rules"); rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add keyword rule"))[0].performClick(); settle(); s("22-rule-dialog"); tap("Cancel"); back() }.onFailure { System.err.println("TOUR: " + it.toString().take(300)) }
            runCatching { tap("Bills"); tap("Netflix"); s("23-bill-dialog"); tap("Cancel") }.onFailure { System.err.println("TOUR: " + it.toString().take(300)) }
            runCatching { tap("Activity"); tap("Doordash Thai Basil"); s("24-transaction-sheet") }.onFailure { System.err.println("TOUR: " + it.toString().take(300)) }
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.printToStringCompat(): String {
    fun walk(n: androidx.compose.ui.semantics.SemanticsNode, d: Int, sb: StringBuilder) {
        val texts = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }
        val cd = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.joinToString()
        sb.append("  ".repeat(d)).append(n.id).append(" t=").append(texts).append(" cd=").append(cd).append('\n')
        if (d < 12) n.children.forEach { walk(it, d + 1, sb) }
    }
    return StringBuilder().also { walk(fetchSemanticsNode(), 0, it) }.toString()
}
