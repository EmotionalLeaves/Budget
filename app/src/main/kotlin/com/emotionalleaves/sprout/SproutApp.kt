package com.emotionalleaves.sprout

import android.app.Application
import android.content.Context
import com.emotionalleaves.sprout.data.CrashLog
import com.emotionalleaves.sprout.data.NetworkMonitor
import com.emotionalleaves.sprout.data.alerts.AlertRepository
import com.emotionalleaves.sprout.data.alerts.AndroidNotifier
import com.emotionalleaves.sprout.data.backup.BackupRepository
import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.categories.CategoryRepository
import com.emotionalleaves.sprout.data.csv.ExportRepository
import com.emotionalleaves.sprout.data.csv.ImportRepository
import com.emotionalleaves.sprout.data.DataManager
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.simplefin.SimpleFinClient
import com.emotionalleaves.sprout.data.forecast.ForecastRepository
import com.emotionalleaves.sprout.data.goals.GoalRepository
import com.emotionalleaves.sprout.data.recurring.RecurringRepository
import com.emotionalleaves.sprout.data.sync.AlertSink
import com.emotionalleaves.sprout.data.sync.ConnectRepository
import com.emotionalleaves.sprout.data.sync.SyncProblem
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
    val crashLog = CrashLog(java.io.File(appContext.filesDir, "crash-report.txt"))
    val clock: Clock = Clock.systemDefaultZone()
    val db: SproutDatabase = SproutDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val secureStore = SecureStore(appContext)
    private val client = SimpleFinClient()
    val notifier = AndroidNotifier(appContext)
    val recurringRepository = RecurringRepository(db, clock)
    val goalRepository = GoalRepository(db, clock)
    val budgetRepository = BudgetRepository(db, clock, expectedCharges = recurringRepository::expectedCharges,
        plannedSetAside = { goalRepository.observePlannedSetAside() })
    val flexRepository = com.emotionalleaves.sprout.data.budget.FlexRepository(
        db, settings, budgetRepository, recurringRepository::expectedCharges, { goalRepository.observePlannedSetAside() },
    )
    val alertRepository = AlertRepository(db, settings, budgetRepository, notifier, clock)
    val forecastRepository = ForecastRepository(db, budgetRepository, recurringRepository, clock, paydaySource = { settings.current().paydaySource })

    /** After every sync: refresh recurring charges first (they feed safe to spend), then alerts. */
    private val postSync = object : AlertSink {
        override suspend fun afterSync(newOutflowIds: List<String>, problems: List<SyncProblem>) {
            runCatching { recurringRepository.detect() }
            runCatching { tagRepository.applyRulesToNew() }
            alertRepository.afterSync(newOutflowIds, problems)
            com.emotionalleaves.sprout.widget.SafeToSpendWidget.refresh(appContext)
            com.emotionalleaves.sprout.widget.BudgetsWidget.refresh(appContext)
        }
    }
    val syncRepository = SyncRepository(db, secureStore, settings, client, clock, postSync)
    val connectRepository = ConnectRepository(client, secureStore, settings)
    val categoryRepository = CategoryRepository(db, clock)
    val tagRepository = com.emotionalleaves.sprout.data.tags.TagRepository(db, clock)
    val manualRepository = com.emotionalleaves.sprout.data.manual.ManualRepository(db, clock)
    val challengeRepository = com.emotionalleaves.sprout.data.challenge.ChallengeRepository(appContext, db)
    val receiptRepository = com.emotionalleaves.sprout.data.receipts.ReceiptRepository(appContext, db, clock)
    val insightsRepository = com.emotionalleaves.sprout.data.insights.InsightsRepository(db, clock)
    val exportRepository = ExportRepository(db)
    val backupRepository = BackupRepository(db, clock)
    val snapshots = com.emotionalleaves.sprout.data.backup.SnapshotRepository(appContext, backupRepository)
    val dataManager = DataManager(appContext, db, settings, secureStore, syncRepository)
    val importRepository = ImportRepository(db, clock, afterImport = {
        recurringRepository.detect()
        runCatching { tagRepository.applyRulesToNew() }
        alertRepository.evaluateBudgets()
    })
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val network = NetworkMonitor(appContext)
    val updates = com.emotionalleaves.sprout.data.update.UpdateRepository(
        appContext, BuildConfig.VERSION_NAME, BuildConfig.UPDATE_REPO, BuildConfig.UPDATE_TOKEN,
        storedKey = { secureStore.readUpdateKey() },
        currentCode = BuildConfig.VERSION_CODE,
        beta = { settings.current().betaUpdates },
        beforeInstall = { snapshots.take(BuildConfig.VERSION_NAME) },
        saveStoredKey = { k -> if (k.isEmpty()) secureStore.clearUpdateKey() else secureStore.saveUpdateKey(k) },
    )
}

class SproutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashLog.install(BuildConfig.VERSION_NAME)
        container.notifier.createChannels()
        com.emotionalleaves.sprout.ui.common.AppShortcuts.install(this)
        container.appScope.launch {
            container.syncRepository.ensureSeeded()
            runCatching { container.settings.upgradeHistoryDefaultOnce() }
            runCatching { container.syncRepository.hideDuplicateAccounts() }
            runCatching {
                val v = container.settings.current().catalogVersion
                if (v < com.emotionalleaves.sprout.data.categories.CategoryRepository.CATALOG_VERSION) {
                    container.categoryRepository.upgradeCatalog(v)
                    container.settings.setCatalogVersion(com.emotionalleaves.sprout.data.categories.CategoryRepository.CATALOG_VERSION)
                }
            }
            val s = container.settings.current()
            val connected = container.secureStore.hasAccessUrlNow()
            if (connected && !s.accessRevoked) SyncScheduler.schedulePeriodic(this@SproutApp)
            runCatching { container.updates.checkIfDue(System.currentTimeMillis() / 1000) }
            runCatching { com.emotionalleaves.sprout.work.UpdateCheckWorker.schedule(this@SproutApp) }
            runCatching { com.emotionalleaves.sprout.work.BillReminderWorker.schedule(this@SproutApp) }
        }
    }
}
