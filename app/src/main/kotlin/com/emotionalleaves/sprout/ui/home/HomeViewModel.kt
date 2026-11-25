package com.emotionalleaves.sprout.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.ConnectionEntity
import com.emotionalleaves.sprout.data.db.ConnectionStatus
import com.emotionalleaves.sprout.data.db.SyncLogEntity
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.data.settings.Settings
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import com.emotionalleaves.sprout.domain.budget.BudgetMonth
import com.emotionalleaves.sprout.data.recurring.UpcomingBill
import com.emotionalleaves.sprout.ui.common.SyncActions
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

data class Totals(val totalMinor: Long, val cashMinor: Long, val creditMinor: Long, val excludedNonUsd: Int)

data class HomeState(
    val accounts: List<AccountEntity> = emptyList(),
    val connections: List<ConnectionEntity> = emptyList(),
    val recent: List<TransactionRow> = emptyList(),
    val lastLog: SyncLogEntity? = null,
    val settings: Settings = Settings(),
    val connected: Boolean = false,
    val online: Boolean = true,
    val loaded: Boolean = false,
) {
    val totals: Totals get() = computeTotals(accounts)
    val attention: List<ConnectionEntity> get() = connections.filter { it.status == ConnectionStatus.NEEDS_ATTENTION }
    val backupDue: Boolean get() = backupDue(System.currentTimeMillis() / 1000)

    /** A week after the first account (then monthly), unless snoozed. Quiet until there's something worth keeping. */
    fun backupDue(now: Long): Boolean {
        val first = accounts.minOfOrNull { it.createdAt } ?: return false
        if (now < settings.backupNudgeUntil) return false
        val last = settings.lastBackupAt
        return if (last == null) now - first > 7 * 86_400 else now - last > 30 * 86_400
    }

    companion object {
        fun computeTotals(accounts: List<AccountEntity>): Totals {
            val counted = accounts.filter { it.includeInTotals && !it.hidden && AccountTypes.isUsd(it.currency) }
            val cash = counted.filter { AccountTypes.isCashLike(it.type) }.sumOf { it.balanceMinor }
            val credit = counted.filterNot { AccountTypes.isCashLike(it.type) }.sumOf { it.balanceMinor }
            return Totals(cash + credit, cash, credit, accounts.count { !AccountTypes.isUsd(it.currency) })
        }
    }
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val updates get() = c.updates

    private val base = combine(
        c.db.accounts().observeVisible(),
        c.db.connections().observeAll(),
        c.db.transactions().observeRows("", 10),
        c.db.syncLog().observeRecent(1).map { it.firstOrNull() },
        c.settings.settings,
    ) { accounts, connections, recent, log, settings ->
        HomeState(accounts, connections, recent, log, settings, loaded = true)
    }

    val state: StateFlow<HomeState> = combine(base, c.secureStore.hasAccessUrl, c.network.online) { s, connected, online ->
        s.copy(connected = connected, online = online)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** This month's budgets; re-subscribed when the screen comes back so a new month is picked up. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val budget: StateFlow<BudgetMonth?> = c.budgetRepository.todayFlow()
        .map { java.time.YearMonth.from(it) }
        .distinctUntilChanged()
        .flatMapLatest { c.budgetRepository.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bills: StateFlow<List<UpcomingBill>> = c.budgetRepository.todayFlow()
        .flatMapLatest { c.recurringRepository.observeUpcoming(30) }.map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val outlook: StateFlow<com.emotionalleaves.sprout.data.forecast.HomeOutlook?> = combine(
        c.budgetRepository.todayFlow(),
        c.db.transactions().observeCount(),
        c.db.accounts().observeAll(),
        c.settings.settings.map { it.paydaySource }.distinctUntilChanged(),
    ) { _, _, _, _ -> Unit }
        .mapLatest { runCatching { c.forecastRepository.outlook() }.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Spending for the last 6 months, for the bar chart. Refreshes when transactions change. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val months: StateFlow<List<Pair<java.time.YearMonth, Long>>> = combine(
        c.budgetRepository.todayFlow(), c.db.transactions().observeCount(),
    ) { _, _ -> Unit }
        .mapLatest { runCatching { c.budgetRepository.spendingByMonth(6) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val insights: StateFlow<List<com.emotionalleaves.sprout.domain.insights.Insight>> = combine(
        c.budgetRepository.todayFlow(), c.db.transactions().observeCount(),
    ) { _, _ -> Unit }
        .mapLatest { runCatching { c.insightsRepository.insights() }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val calendar: StateFlow<com.emotionalleaves.sprout.domain.calendar.SpendCalendar?> = combine(
        c.budgetRepository.todayFlow(), c.db.transactions().observeCount(),
    ) { _, _ -> Unit }
        .mapLatest { runCatching { c.insightsRepository.calendar() }.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Last month's recap during the first 7 days of a month, unless dismissed for that month. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recap: StateFlow<com.emotionalleaves.sprout.domain.insights.Recap?> = combine(
        c.budgetRepository.todayFlow(), c.settings.settings.map { it.recapDismissed },
    ) { today, dismissed -> today to dismissed }
        .mapLatest { (today, dismissed) ->
            val month = java.time.YearMonth.from(today).minusMonths(1)
            if (today.dayOfMonth > 7 || dismissed == month.toString()) null else runCatching { c.insightsRepository.recap(month) }.getOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setPaydaySource(name: String?) = viewModelScope.launch { c.settings.setPaydaySource(name) }
    fun snoozeBackup() = viewModelScope.launch { c.settings.snoozeBackupNudge(System.currentTimeMillis() / 1000 + 14 * 86_400) }
    fun dismissRecap() = viewModelScope.launch { recap.value?.let { c.settings.setRecapDismissed(it.month.toString()) } }

    val inboxCount: StateFlow<Int> = c.db.transactions().observeInboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val flex: StateFlow<com.emotionalleaves.sprout.data.budget.FlexState?> = combine(
        c.budgetRepository.todayFlow().map { java.time.YearMonth.from(it) }.distinctUntilChanged(), c.settings.settings.map { it.budgetMode },
    ) { m, mode -> m to mode }
        .flatMapLatest { (m, mode) -> if (mode == 1) c.flexRepository.observe(m) else kotlinx.coroutines.flow.flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ageOfMoney: StateFlow<Int?> = combine(c.budgetRepository.todayFlow(), c.db.transactions().observeCount()) { _, _ -> Unit }
        .mapLatest { runCatching { c.insightsRepository.ageOfMoney() }.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val watch: StateFlow<List<Triple<String, String, Long>>> = combine(c.settings.settings.map { it.watchlist }, c.db.transactions().observeCount()) { w, _ -> w }
        .mapLatest { runCatching { c.insightsRepository.watch(it) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val health: StateFlow<com.emotionalleaves.sprout.domain.challenge.HealthScore.Result?> =
        combine(c.budgetRepository.todayFlow(), c.db.transactions().observeCount()) { _, _ -> Unit }
            .mapLatest { runCatching { c.insightsRepository.health() }.getOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val challenges: StateFlow<List<com.emotionalleaves.sprout.domain.challenge.ChallengeProgress>> =
        combine(c.challengeRepository.challenges, c.db.transactions().observeCount(), c.budgetRepository.todayFlow()) { _, _, _ -> Unit }
            .mapLatest { runCatching { c.challengeRepository.progress() }.getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setHomeCard(key: String, shown: Boolean) = viewModelScope.launch { c.settings.setHomeCard(key, shown) }

    val privacy: StateFlow<Boolean> = c.settings.settings.map { it.privacyMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun togglePrivacy() = viewModelScope.launch { c.settings.setPrivacyMode(!privacy.value) }

    val goals: StateFlow<List<com.emotionalleaves.sprout.data.goals.GoalView>> = c.goalRepository.observe().map { it.take(2) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncing: StateFlow<Boolean> = WorkManager.getInstance(c.appContext)
        .getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_WORK)
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun now(): Long = c.clock.instant().epochSecond

    /** A crash report from a previous run, if any (local only; shared only if the user taps Share). */
    private val _crash = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val crash: StateFlow<String?> = _crash

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _crash.value = c.crashLog.read() }
    }

    fun dismissCrash() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { c.crashLog.clear() }
        _crash.value = null
    }

    fun syncNow() {
        viewModelScope.launch { SyncActions.requestSync(c)?.let { _messages.tryEmit(it) } }
    }
}
