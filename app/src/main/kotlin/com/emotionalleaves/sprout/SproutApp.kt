package com.emotionalleaves.sprout

import android.app.Application
import android.content.Context
import com.emotionalleaves.sprout.data.NetworkMonitor
import com.emotionalleaves.sprout.data.alerts.AlertRepository
import com.emotionalleaves.sprout.data.alerts.AndroidNotifier
import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.categories.CategoryRepository
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.simplefin.SimpleFinClient
import com.emotionalleaves.sprout.data.sync.ConnectRepository
import com.emotionalleaves.sprout.data.sync.SyncRepository
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

/** Simple manual dependency injection: one container per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    val db: SproutDatabase = SproutDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val secureStore = SecureStore(appContext)
    private val client = SimpleFinClient()
    val notifier = AndroidNotifier(appContext)
    val budgetRepository = BudgetRepository(db, clock)
    val alertRepository = AlertRepository(db, settings, budgetRepository, notifier, clock)
    val syncRepository = SyncRepository(db, secureStore, settings, client, clock, alertRepository)
    val connectRepository = ConnectRepository(client, secureStore, settings)
    val categoryRepository = CategoryRepository(db, clock)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val network = NetworkMonitor(appContext)
}

class SproutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.appScope.launch {
            container.syncRepository.ensureSeeded()
            val s = container.settings.current()
            val connected = container.secureStore.hasAccessUrlNow()
            if (connected && !s.accessRevoked) SyncScheduler.schedulePeriodic(this@SproutApp)
        }
    }
}
