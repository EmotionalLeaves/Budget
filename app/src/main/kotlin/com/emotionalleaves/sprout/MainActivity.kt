package com.emotionalleaves.sprout

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
            SproutTheme {
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
            else -> null
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    BUDGETS("budgets", "Budgets", Icons.Filled.PieChart),
    BILLS("bills", "Bills", Icons.Filled.CalendarMonth),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
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
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { goTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
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
                )
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen(snackbar, onReview = { nav.navigate("review") }) }
            composable("review") { ReviewScreen(onBack = { nav.popBackStack() }) }
            composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
            composable(Tab.BUDGETS.route) {
                BudgetsScreen(onQuickSetup = { nav.navigate("quick-setup") }, onCategory = { nav.navigate("category/$it") })
            }
            composable("import") { ImportScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
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
