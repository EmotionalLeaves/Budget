package com.emotionalleaves.sprout

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import com.emotionalleaves.sprout.ui.common.showUndoable
import android.content.Intent
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.emotionalleaves.sprout.ui.lock.AppLockPolicy
import com.emotionalleaves.sprout.ui.lock.DeviceLock
import com.emotionalleaves.sprout.ui.lock.LockScreen
import kotlinx.coroutines.runBlocking
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.emotionalleaves.sprout.data.alerts.AndroidNotifier
import com.emotionalleaves.sprout.ui.transactions.TransactionDetailSheet
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.emotionalleaves.sprout.data.settings.Settings
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.emotionalleaves.sprout.ui.bills.BillsScreen
import com.emotionalleaves.sprout.ui.importcsv.ImportScreen
import com.emotionalleaves.sprout.ui.goals.GoalsScreen
import com.emotionalleaves.sprout.ui.budgets.BudgetsScreen
import com.emotionalleaves.sprout.ui.budgets.CategoryDetailScreen
import com.emotionalleaves.sprout.ui.budgets.QuickSetupScreen
import com.emotionalleaves.sprout.ui.categories.CategoriesScreen
import com.emotionalleaves.sprout.ui.categories.ReviewScreen
import com.emotionalleaves.sprout.ui.categories.RulesScreen
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.appContainer
import com.emotionalleaves.sprout.ui.home.HomeScreen
import com.emotionalleaves.sprout.ui.onboarding.ConnectScreen
import com.emotionalleaves.sprout.ui.settings.BackupScreen
import com.emotionalleaves.sprout.ui.settings.ExportScreen
import com.emotionalleaves.sprout.ui.settings.SettingsScreen
import com.emotionalleaves.sprout.ui.theme.SproutTheme
import com.emotionalleaves.sprout.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch

/** Where a notification tap should take the user. */
sealed interface DeepLink {
    data class Category(val id: Long) : DeepLink
    data class Transaction(val id: String) : DeepLink
    data object Settings : DeepLink
    /** From a home-screen shortcut: one of AppShortcuts.ALLOWED. */
    data class Route(val route: String) : DeepLink
}

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<DeepLink?>(null)

    // App lock state. "unlocked" survives configuration changes within this process.
    private val locked = mutableStateOf(false)
    private var lockEnabled = false
    private var confirming = false
    private var backgroundedAt: Long? = null

    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        confirming = false
        if (result.resultCode == RESULT_OK) {
            unlockedThisProcess = true
            locked.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLink.value = parse(intent)
        val settings = (application as SproutApp).container.settings
        // One small read so the lock and FLAG_SECURE apply before anything is drawn.
        val initial = runBlocking { settings.current() }
        lockEnabled = initial.appLock
        applySecureFlag(initial.hideInRecents)
        lifecycleScope.launch {
            settings.settings.collect { s ->
                lockEnabled = s.appLock
                if (!s.appLock) locked.value = false
                applySecureFlag(s.hideInRecents)
            }
        }
        setContent {
            val look by settings.settings.collectAsState(initial = initial)
            SproutTheme(themeMode = look.themeMode, dynamicColor = look.dynamicColor) {
                com.emotionalleaves.sprout.ui.common.UniformScale {
                    Surface(Modifier.fillMaxSize()) {
                        if (locked.value) {
                            LockScreen(onUnlock = ::requestUnlock)
                        } else {
                            SproutRoot(deepLink.value) { deepLink.value = null }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (confirming) return
        val now = System.currentTimeMillis() / 1000
        if (AppLockPolicy.shouldLock(lockEnabled, DeviceLock.isSecure(this), unlockedThisProcess, backgroundedAt, now)) {
            locked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !confirming) backgroundedAt = System.currentTimeMillis() / 1000
    }

    private fun requestUnlock() {
        if (confirming) return
        val intent = DeviceLock.confirmIntent(this)
        if (intent == null) {
            // The screen lock was removed: nothing to confirm against.
            locked.value = false
            return
        }
        confirming = true
        confirmCredential.launch(intent)
    }

    private fun applySecureFlag(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        /** Process-wide: a new process (after Android kills Sprout) always asks again. */
        private var unlockedThisProcess = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { deepLink.value = it }
    }

    private fun parse(intent: Intent?): DeepLink? {
        intent ?: return null
        return when {
            intent.hasExtra(AndroidNotifier.EXTRA_CATEGORY) -> DeepLink.Category(intent.getLongExtra(AndroidNotifier.EXTRA_CATEGORY, 0))
            intent.hasExtra(AndroidNotifier.EXTRA_TRANSACTION) -> intent.getStringExtra(AndroidNotifier.EXTRA_TRANSACTION)?.let { DeepLink.Transaction(it) }
            intent.hasExtra(AndroidNotifier.EXTRA_SETTINGS) -> DeepLink.Settings
            intent.hasExtra(com.emotionalleaves.sprout.ui.common.AppShortcuts.EXTRA_ROUTE) ->
                intent.getStringExtra(com.emotionalleaves.sprout.ui.common.AppShortcuts.EXTRA_ROUTE)
                    ?.takeIf { it in com.emotionalleaves.sprout.ui.common.AppShortcuts.ALLOWED }?.let { DeepLink.Route(it) }
            else -> null
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Rounded.Home),
    TRANSACTIONS("transactions", "Activity", Icons.AutoMirrored.Rounded.ReceiptLong),
    BUDGETS("budgets", "Budgets", Icons.Rounded.DonutLarge),
    BILLS("bills", "Bills", Icons.Rounded.CalendarMonth),
    SETTINGS("settings", "More", Icons.Rounded.Tune),
}

@Composable
private fun SproutRoot(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val container = appContainer()
    // null until DataStore answers, so the onboarding screen never flashes for returning users.
    val settings: Settings? by container.settings.settings.collectAsState(initial = null)
    val scope = container.appScope
    when (val s = settings) {
        null -> Box(Modifier.fillMaxSize())
        else -> if (!s.onboardingComplete) {
            ConnectScreen(
                startAtToken = false,
                onDone = {},
                onSkip = { scope.launch { container.settings.setOnboardingComplete(true) } },
            )
        } else {
            MainScaffold(deepLink, onDeepLinkHandled)
        }
    }
}

@Composable
private fun MainScaffold(deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var openTx by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val container = appContainer()
    val inboxCount by remember { container.db.transactions().observeInboxCount() }.collectAsState(initial = 0)

    fun goTab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Wait until the NavHost has a graph (backStack != null) before navigating.
    LaunchedEffect(deepLink, backStack != null) {
        if (backStack == null) return@LaunchedEffect
        when (deepLink) {
            is DeepLink.Category -> nav.navigate("category/${deepLink.id}")
            is DeepLink.Transaction -> openTx = deepLink.id
            DeepLink.Settings -> goTab(Tab.SETTINGS)
            is DeepLink.Route -> if (deepLink.route == "bills") goTab(Tab.BILLS) else nav.navigate(deepLink.route)
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }
    openTx?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openTx = null }, onMessage = { msg, undo -> scope.showUndoable(snackbar, msg, undo) })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { goTab(tab) },
                            icon = {
                                if (tab == Tab.TRANSACTIONS && inboxCount > 0) {
                                    androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge { Text(if (inboxCount > 99) "99+" else "$inboxCount") } }) {
                                        Icon(tab.icon, contentDescription = "${tab.label}, $inboxCount to review")
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val names by androidx.compose.runtime.remember { container.db.merchantNames().observeAll() }.collectAsState(initial = emptyList())
        val nameMap = androidx.compose.runtime.remember(names) { names.associate { it.merchantKey to it.displayName } }
        androidx.compose.runtime.CompositionLocalProvider(
            com.emotionalleaves.sprout.ui.common.LocalNavigate provides { r: String -> nav.navigate(r) },
            com.emotionalleaves.sprout.ui.common.LocalMerchantNames provides nameMap,
        ) {
        NavHost(
            nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { it / 14 } },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) +
                androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { it / 14 } },
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    snackbar,
                    onConnect = { nav.navigate("connect") },
                    onSeeAllTransactions = { goTab(Tab.TRANSACTIONS) },
                    onSetBudgets = { nav.navigate("quick-setup") },
                    onCategory = { nav.navigate("category/$it") },
                    onSeeBudgets = { goTab(Tab.BUDGETS) },
                    onSeeBills = { goTab(Tab.BILLS) },
                    onImport = { nav.navigate("import") },
                    onGoals = { nav.navigate("goals") },
                    onNetWorth = { nav.navigate("networth") },
                )
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen(snackbar, onReview = { nav.navigate("review") }) }
            composable("review") { ReviewScreen(onBack = { nav.popBackStack() }) }
            composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
            composable(Tab.BUDGETS.route) {
                BudgetsScreen(
                    onQuickSetup = { nav.navigate("quick-setup") },
                    onCategory = { nav.navigate("category/$it") },
                    onGoals = { nav.navigate("goals") },
                )
            }
            composable("import") { ImportScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
            composable("goals") { GoalsScreen(onBack = { nav.popBackStack() }) }
            composable("merchant/{key}") { entry ->
                val key = entry.arguments?.getString("key")?.let { android.net.Uri.decode(it) }.orEmpty()
                com.emotionalleaves.sprout.ui.insights.MerchantScreen(key, snackbar, onBack = { nav.popBackStack() })
            }
            composable("stats") {
                com.emotionalleaves.sprout.ui.stats.StatsScreen(onBack = { nav.popBackStack() }, onMerchant = { nav.navigate("merchant/" + android.net.Uri.encode(it)) })
            }
            composable("debt") { com.emotionalleaves.sprout.ui.debt.DebtScreen(onBack = { nav.popBackStack() }) }
            composable("inbox") { com.emotionalleaves.sprout.ui.transactions.InboxScreen(snackbar, onBack = { nav.popBackStack() }) }
            composable("reports") { com.emotionalleaves.sprout.ui.insights.ReportsScreen(onBack = { nav.popBackStack() }) }
            composable("year-review") { com.emotionalleaves.sprout.ui.insights.YearInReviewScreen(onBack = { nav.popBackStack() }) }
            composable("challenges") { com.emotionalleaves.sprout.ui.challenge.ChallengesScreen(onBack = { nav.popBackStack() }) }
            composable("networth") { com.emotionalleaves.sprout.ui.insights.NetWorthScreen(onBack = { nav.popBackStack() }) }
            composable("year") { com.emotionalleaves.sprout.ui.insights.YearScreen(onBack = { nav.popBackStack() }, onCategory = { nav.navigate("category/$it") }) }
            composable("tags") { com.emotionalleaves.sprout.ui.tags.TagsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("tag/$it") }) }
            composable("account/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    com.emotionalleaves.sprout.ui.home.AccountHeader(id, onBack = { nav.popBackStack() })
                    TransactionsScreen(snackbar, onReview = { nav.navigate("review") }, initialAccountId = id)
                }
            }
            composable("tag/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    com.emotionalleaves.sprout.ui.tags.TagHeader(id, onBack = { nav.popBackStack() })
                    TransactionsScreen(snackbar, onReview = { nav.navigate("review") }, initialTagId = id)
                }
            }
            // A ready-made search ("nov 24" from a calendar day), with a way back.
            composable("search/{q}") { entry ->
                val q = entry.arguments?.getString("q").orEmpty()
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                        androidx.compose.material3.Text("Purchases", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    }
                    TransactionsScreen(snackbar, onReview = { nav.navigate("review") }, initialQuery = q)
                }
            }
            composable("quick-setup") { QuickSetupScreen(onBack = { nav.popBackStack() }) }
            composable(
                "category/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                CategoryDetailScreen(
                    categoryId = entry.arguments?.getLong("id") ?: 0L,
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                    onEditCategories = { nav.navigate("categories") },
                )
            }
            composable(Tab.BILLS.route) { BillsScreen() }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    snackbar,
                    onReplaceToken = { nav.navigate("connect") },
                    onCategories = { nav.navigate("categories") },
                    onRules = { nav.navigate("rules") },
                    onImport = { nav.navigate("import") },
                    onExport = { nav.navigate("export") },
                    onBackup = { nav.navigate("backup") },
                    onRoute = { nav.navigate(it) },
                )
            }
            composable("export") { ExportScreen(snackbar, onBack = { nav.popBackStack() }) }
            composable("backup") { BackupScreen(snackbar, onBack = { nav.popBackStack() }) }
            composable("connect") {
                ConnectScreen(
                    startAtToken = true,
                    onDone = { nav.popBackStack() },
                    onSkip = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        }
    }
}
