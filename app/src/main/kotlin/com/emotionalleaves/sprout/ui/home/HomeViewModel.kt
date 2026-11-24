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
import com.emotionalleaves.sprout.ui.common.SyncActions
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val budget: StateFlow<BudgetMonth?> = c.budgetRepository.observeMonth(c.budgetRepository.currentMonth())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> = WorkManager.getInstance(c.appContext)
        .getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_WORK)
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun now(): Long = c.clock.instant().epochSecond

    fun syncNow() {
        viewModelScope.launch { SyncActions.requestSync(c)?.let { _messages.tryEmit(it) } }
    }
}
