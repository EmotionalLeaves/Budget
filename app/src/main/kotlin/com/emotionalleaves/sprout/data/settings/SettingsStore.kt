package com.emotionalleaves.sprout.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val onboardingComplete: Boolean = false,
    val firstSyncDays: Int = 90,
    val lastSuccessfulSyncAt: Long? = null,
    val lastSyncAttemptAt: Long? = null,
    val largeTxThresholdMinor: Long = 25_000,
    val notifyBudget: Boolean = true,
    val notifyUnusual: Boolean = true,
    val notifySyncProblems: Boolean = true,
    /** Set when /accounts answered 403. Periodic sync stops until a new token is saved. */
    val accessRevoked: Boolean = false,
    /** Set when /accounts answered 402. */
    val paymentRequired: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Non-secret app settings (Preferences DataStore). */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            onboardingComplete = p[ONBOARDING] ?: false,
            firstSyncDays = p[FIRST_SYNC_DAYS] ?: 90,
            lastSuccessfulSyncAt = p[LAST_SUCCESS],
            lastSyncAttemptAt = p[LAST_ATTEMPT],
            largeTxThresholdMinor = p[LARGE_TX] ?: 25_000,
            notifyBudget = p[NOTIFY_BUDGET] ?: true,
            notifyUnusual = p[NOTIFY_UNUSUAL] ?: true,
            notifySyncProblems = p[NOTIFY_SYNC] ?: true,
            accessRevoked = p[REVOKED] ?: false,
            paymentRequired = p[PAYMENT] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingComplete(done: Boolean) = dataStore.edit { it[ONBOARDING] = done }
    suspend fun setFirstSyncDays(days: Int) = dataStore.edit { it[FIRST_SYNC_DAYS] = days }
    suspend fun setLastSyncAttempt(at: Long) = dataStore.edit { it[LAST_ATTEMPT] = at }
    suspend fun setLastSuccessfulSync(at: Long) = dataStore.edit { it[LAST_SUCCESS] = at }
    suspend fun setAccessRevoked(revoked: Boolean) = dataStore.edit { it[REVOKED] = revoked }
    suspend fun setPaymentRequired(required: Boolean) = dataStore.edit { it[PAYMENT] = required }

    /** A new token: start fresh sync bookkeeping (first-sync window) but keep all other settings. */
    suspend fun onNewConnection() = dataStore.edit {
        it.remove(LAST_SUCCESS)
        it[REVOKED] = false
        it[PAYMENT] = false
    }

    suspend fun clearAll() = dataStore.edit { it.clear() }

    private companion object {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val FIRST_SYNC_DAYS = intPreferencesKey("first_sync_days")
        val LAST_SUCCESS = longPreferencesKey("last_successful_sync_at")
        val LAST_ATTEMPT = longPreferencesKey("last_sync_attempt_at")
        val LARGE_TX = longPreferencesKey("large_tx_threshold_minor")
        val NOTIFY_BUDGET = booleanPreferencesKey("notify_budget")
        val NOTIFY_UNUSUAL = booleanPreferencesKey("notify_unusual")
        val NOTIFY_SYNC = booleanPreferencesKey("notify_sync")
        val REVOKED = booleanPreferencesKey("access_revoked")
        val PAYMENT = booleanPreferencesKey("payment_required")
    }
}
