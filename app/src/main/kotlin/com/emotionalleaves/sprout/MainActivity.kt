package com.emotionalleaves.sprout

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
import androidx.compose.runtime.getValue
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
import com.emotionalleaves.sprout.ui.settings.SettingsScreen
import com.emotionalleaves.sprout.ui.theme.SproutTheme
import com.emotionalleaves.sprout.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SproutTheme {
                Surface(Modifier.fillMaxSize()) { SproutRoot() }
            }
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
private fun SproutRoot() {
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
            MainScaffold()
        }
    }
}

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    fun goTab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
                )
            }
            composable(Tab.TRANSACTIONS.route) { TransactionsScreen(snackbar, onReview = { nav.navigate("review") }) }
            composable("review") { ReviewScreen(onBack = { nav.popBackStack() }) }
            composable("categories") { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
            composable(Tab.BUDGETS.route) {
                BudgetsScreen(onQuickSetup = { nav.navigate("quick-setup") }, onCategory = { nav.navigate("category/$it") })
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
            composable(Tab.BILLS.route) {
                EmptyState("Bills are coming soon", "Sprout will spot subscriptions and upcoming bills in version 0.7.")
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    snackbar,
                    onReplaceToken = { nav.navigate("connect") },
                    onCategories = { nav.navigate("categories") },
                    onRules = { nav.navigate("rules") },
                )
            }
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
