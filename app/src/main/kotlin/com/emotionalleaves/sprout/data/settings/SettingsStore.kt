package com.emotionalleaves.sprout.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** The notification explainer has been shown (Android 13+). */
    val notificationsAsked: Boolean = false,
    /** Ask for the phone's screen lock when opening Sprout. */
    val appLock: Boolean = false,
    /** FLAG_SECURE: hide Sprout from screenshots and the recent-apps preview. */
    val hideInRecents: Boolean = false,
    /** Epoch seconds until which syncing pauses after a SimpleFIN rate-limit warning. */
    val rateLimitedUntil: Long? = null,
    /** Date transactions by purchase date (transacted_at) when the bank sends it. */
    val usePurchaseDate: Boolean = false,
    /** 0 = follow the phone, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
    /** Use the wallpaper's colors (Android 12+) instead of Sprout's own palette. */
    val dynamicColor: Boolean = false,
    /** Hide amounts on Home behind dots until tapped (for using the app in public). */
    val privacyMode: Boolean = false,
    /** A notification the day before a bill is due. Off until you turn it on. */
    val notifyBills: Boolean = false,
    /** "YYYY-MM" of the last monthly recap you closed. */
    val recapDismissed: String? = null,
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
            notificationsAsked = p[NOTIFY_ASKED] ?: false,
            appLock = p[APP_LOCK] ?: false,
            hideInRecents = p[HIDE_RECENTS] ?: false,
            rateLimitedUntil = p[RATE_LIMITED_UNTIL],
            usePurchaseDate = p[PURCHASE_DATE] ?: false,
            themeMode = p[THEME_MODE] ?: 0,
            dynamicColor = p[DYNAMIC_COLOR] ?: false,
            privacyMode = p[PRIVACY_MODE] ?: false,
            notifyBills = p[NOTIFY_BILLS] ?: false,
            recapDismissed = p[RECAP_DISMISSED],
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingComplete(done: Boolean) = dataStore.edit { it[ONBOARDING] = done }
    suspend fun setFirstSyncDays(days: Int) = dataStore.edit { it[FIRST_SYNC_DAYS] = days }
    suspend fun setLastSyncAttempt(at: Long) = dataStore.edit { it[LAST_ATTEMPT] = at }
    suspend fun setLastSuccessfulSync(at: Long) = dataStore.edit { it[LAST_SUCCESS] = at }
    suspend fun setAccessRevoked(revoked: Boolean) = dataStore.edit { it[REVOKED] = revoked }
    suspend fun setPaymentRequired(required: Boolean) = dataStore.edit { it[PAYMENT] = required }
    suspend fun setNotifyBudget(on: Boolean) = dataStore.edit { it[NOTIFY_BUDGET] = on }
    suspend fun setNotifyUnusual(on: Boolean) = dataStore.edit { it[NOTIFY_UNUSUAL] = on }
    suspend fun setNotifySyncProblems(on: Boolean) = dataStore.edit { it[NOTIFY_SYNC] = on }
    suspend fun setLargeTxThreshold(minor: Long) = dataStore.edit { it[LARGE_TX] = minor.coerceAtLeast(100) }
    suspend fun setNotificationsAsked() = dataStore.edit { it[NOTIFY_ASKED] = true }
    suspend fun setAppLock(on: Boolean) = dataStore.edit { it[APP_LOCK] = on }
    suspend fun setHideInRecents(on: Boolean) = dataStore.edit { it[HIDE_RECENTS] = on }
    suspend fun setThemeMode(mode: Int) = dataStore.edit { it[THEME_MODE] = mode.coerceIn(0, 2) }
    suspend fun setDynamicColor(on: Boolean) = dataStore.edit { it[DYNAMIC_COLOR] = on }
    suspend fun setRecapDismissed(month: String) = dataStore.edit { it[RECAP_DISMISSED] = month }
    suspend fun setNotifyBills(on: Boolean) = dataStore.edit { it[NOTIFY_BILLS] = on }
    suspend fun setPrivacyMode(on: Boolean) = dataStore.edit { it[PRIVACY_MODE] = on }
    suspend fun setUsePurchaseDate(on: Boolean) = dataStore.edit { it[PURCHASE_DATE] = on }
    suspend fun setRateLimitedUntil(at: Long?) = dataStore.edit { if (at == null) it.remove(RATE_LIMITED_UNTIL) else it[RATE_LIMITED_UNTIL] = at }

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
        val NOTIFY_ASKED = booleanPreferencesKey("notifications_asked")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val HIDE_RECENTS = booleanPreferencesKey("hide_in_recents")
        val RATE_LIMITED_UNTIL = longPreferencesKey("rate_limited_until")
        val PURCHASE_DATE = booleanPreferencesKey("use_purchase_date")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val NOTIFY_BILLS = booleanPreferencesKey("notify_bills")
        val RECAP_DISMISSED = stringPreferencesKey("recap_dismissed")
    }
}
