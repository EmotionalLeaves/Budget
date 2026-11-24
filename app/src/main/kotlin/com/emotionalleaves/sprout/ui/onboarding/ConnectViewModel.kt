package com.emotionalleaves.sprout.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.SyncLogEntity
import com.emotionalleaves.sprout.data.db.SyncTrigger
import com.emotionalleaves.sprout.data.simplefin.SetupTokenResult
import com.emotionalleaves.sprout.data.sync.ConnectResult
import com.emotionalleaves.sprout.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectStep { WELCOME, TOKEN, CONNECTING, SYNCING, ACCOUNTS }

data class ConnectUiState(
    val step: ConnectStep = ConnectStep.WELCOME,
    val token: String = "",
    val error: String? = null,
    /** Show the "Get a new token" button (claim 403, network problems). */
    val offerNewToken: Boolean = false,
)

class ConnectViewModel(private val c: AppContainer, startAtToken: Boolean) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState(step = if (startAtToken) ConnectStep.TOKEN else ConnectStep.WELCOME))
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    val accounts: StateFlow<List<AccountEntity>> =
        c.db.accounts().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastLog: StateFlow<SyncLogEntity?> =
        c.db.syncLog().observeRecent(1).map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showToken() = _state.update { it.copy(step = ConnectStep.TOKEN, error = null) }
    fun back() = _state.update { it.copy(step = ConnectStep.WELCOME, error = null) }
    fun onTokenChange(text: String) = _state.update { it.copy(token = text, error = null, offerNewToken = false) }

    fun connect() {
        val token = _state.value.token
        if (_state.value.step == ConnectStep.CONNECTING) return
        _state.update { it.copy(step = ConnectStep.CONNECTING, error = null, offerNewToken = false) }
        viewModelScope.launch {
            when (val r = c.connectRepository.connect(token)) {
                ConnectResult.Connected -> {
                    // Clear the token from memory right away; it's single-use and now spent.
                    _state.update { it.copy(step = ConnectStep.SYNCING, token = "") }
                    SyncScheduler.syncNow(c.appContext, SyncTrigger.ONBOARDING)
                    SyncScheduler.schedulePeriodic(c.appContext)
                    waitForFirstSync()
                }
                is ConnectResult.InvalidToken -> fail(invalidMessage(r.reason))
                ConnectResult.AlreadyUsed -> fail(ALREADY_USED, offerNewToken = true)
                ConnectResult.NetworkProblem -> fail(NETWORK, offerNewToken = true)
                is ConnectResult.ServerProblem -> fail(
                    "SimpleFIN answered with an error (${r.httpCode}). Your token may already have been used. " +
                        "If trying again fails, create a new token in SimpleFIN Bridge.",
                    offerNewToken = true,
                )
                ConnectResult.BadAccessUrl -> fail(
                    "SimpleFIN sent back a connection Sprout can't use safely, so nothing was saved. " +
                        "Create a new setup token in SimpleFIN Bridge and try again.",
                    offerNewToken = true,
                )
                ConnectResult.StorageFailed -> fail("This phone couldn't save the connection securely. Please try again with a new token.", true)
            }
        }
    }

    private suspend fun waitForFirstSync() {
        // Finished, or waiting to retry. Never wait forever: after 2 minutes show whatever is there.
        withTimeoutOrNull(120_000) {
            WorkManager.getInstance(c.appContext)
                .getWorkInfosForUniqueWorkFlow(SyncScheduler.ONE_TIME_WORK)
                .first { list ->
                    list.isNotEmpty() && list.all { it.state.isFinished || (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0) }
                }
        }
        _state.update { it.copy(step = ConnectStep.ACCOUNTS) }
    }

    fun setIncluded(account: AccountEntity, include: Boolean) {
        viewModelScope.launch { c.db.accounts().setIncludeInTotals(account.id, include) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            c.settings.setOnboardingComplete(true)
            onDone()
        }
    }

    private fun fail(message: String, offerNewToken: Boolean = false) =
        _state.update { it.copy(step = ConnectStep.TOKEN, error = message, offerNewToken = offerNewToken) }

    companion object {
        const val ALREADY_USED = "This setup token was already used or doesn't exist. If you didn't use it yourself, " +
            "someone else may have. Create a new token in SimpleFIN Bridge and disable the old connection there."
        const val NETWORK = "Couldn't reach SimpleFIN. The token may already have been used. Try again; " +
            "if it still fails, create a new token in SimpleFIN Bridge."

        fun invalidMessage(reason: SetupTokenResult.Reason): String = when (reason) {
            SetupTokenResult.Reason.EMPTY -> "Paste your setup token first."
            SetupTokenResult.Reason.NOT_BASE64 -> "That doesn't look like a SimpleFIN setup token. Copy the whole token from SimpleFIN Bridge and paste it again."
            SetupTokenResult.Reason.NOT_URL -> "That token didn't decode into a SimpleFIN address. Copy the whole token and try again."
            SetupTokenResult.Reason.NOT_HTTPS -> "That token points to an insecure (non-https) address, so Sprout won't use it."
            SetupTokenResult.Reason.NO_CLAIM_PATH -> "That isn't a setup token (it has no claim address). In SimpleFIN Bridge, create a new app connection and copy its setup token."
        }
    }
}
