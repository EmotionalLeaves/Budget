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
    /** Sunday "your week" summary; off unless you turn it on. */
    val notifyWeekly: Boolean = false,
    /** "YYYY-MM" of the last monthly recap you closed. */
    val recapDismissed: String? = null,
    /** 0 = a budget per category, 1 = Flex (fixed costs + one Flex amount). */
    val budgetMode: Int = 0,
    /** The one Flex amount (cents) in Flex mode. */
    val flexBudgetMinor: Long = 0,
    /** Categories counted as fixed in Flex mode (besides bills). */
    val fixedCategoryIds: Set<Long> = emptySet(),
    /** Pinned to Home: "c:<categoryId>" or "m:<merchantKey>". */
    val watchlist: Set<String> = emptySet(),
    /** Get test builds (prereleases) as updates too. */
    val betaUpdates: Boolean = false,
    /** Home cards you've hidden (keys from HomeCards). */
    val homeHidden: Set<String> = emptySet(),
    /** Epoch seconds of the last saved encrypted backup, and until when the "back up" nudge is snoozed. */
    val lastBackupAt: Long? = null,
    val backupNudgeUntil: Long = 0,
    /** Which repeating income counts as payday (its display name); null = the soonest one. */
    val paydaySource: String? = null,
    /** Activity searches you saved (encoded by TxFilter.encode). */
    val savedSearches: Set<String> = emptySet(),
    /** Which version of the built-in merchant list last re-sorted your history (CategoryRepository.CATALOG_VERSION). */
    val catalogVersion: Int = 0,
    /** Start of the oldest history step fetched (epoch seconds); null = not tracked yet. */
    val historyOldest: Long? = null,
    /** The bank has no older history (two empty steps in a row) for the current setting. */
    val historyDone: Boolean = false,
    /** Accounts Sprout hid as a copy of another; never hidden again if you show them yourself. */
    val dedupedAccounts: Set<String> = emptySet(),
    /** "hiddenId|keptId" for each copy Sprout hid, so it can switch if the kept one stops syncing. */
    val dedupPairs: Set<String> = emptySet(),
    /** One-time tips you've dismissed (by key). */
    val dismissedTips: Set<String> = emptySet(),
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
            notifyWeekly = p[NOTIFY_WEEKLY] ?: false,
            recapDismissed = p[RECAP_DISMISSED],
            budgetMode = p[BUDGET_MODE] ?: 0,
            flexBudgetMinor = p[FLEX_BUDGET] ?: 0,
            fixedCategoryIds = p[FIXED_IDS]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
            watchlist = p[WATCHLIST] ?: emptySet(),
            betaUpdates = p[BETA] ?: false,
            homeHidden = p[HOME_HIDDEN] ?: emptySet(),
            lastBackupAt = p[LAST_BACKUP],
            backupNudgeUntil = p[BACKUP_NUDGE] ?: 0,
            paydaySource = p[PAYDAY_SOURCE],
            savedSearches = p[SAVED_SEARCHES] ?: emptySet(),
            catalogVersion = p[CATALOG_VERSION] ?: 0,
            historyOldest = p[HISTORY_OLDEST],
            historyDone = p[HISTORY_DONE] ?: false,
            dedupedAccounts = p[DEDUPED_ACCOUNTS] ?: emptySet(),
            dedupPairs = p[DEDUP_PAIRS] ?: emptySet(),
            dismissedTips = p[DISMISSED_TIPS] ?: emptySet(),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboardingComplete(done: Boolean) = dataStore.edit { it[ONBOARDING] = done }
    /** How much history to keep fetching; asking for more than before starts fetching older steps again. */
    suspend fun setFirstSyncDays(days: Int) = dataStore.edit {
        if (days > (it[FIRST_SYNC_DAYS] ?: 90)) it[HISTORY_DONE] = false
        it[FIRST_SYNC_DAYS] = days
    }
    /**
     * Once (2.26.0): everyone still on the old 90-day default gets "Everything the bank has", so older history
     * loads without a trip to Settings. A shorter choice made on purpose (30 days) is kept.
     */
    suspend fun upgradeHistoryDefaultOnce() = dataStore.edit {
        if (it[HISTORY_DEFAULT_UPGRADED] == true) return@edit
        val cur = it[FIRST_SYNC_DAYS] ?: 90
        if (cur == 90) {
            it[FIRST_SYNC_DAYS] = com.emotionalleaves.sprout.domain.sync.SyncPolicy.ALL_HISTORY_DAYS
            it[HISTORY_DONE] = false
        }
        it[HISTORY_DEFAULT_UPGRADED] = true
    }
    suspend fun dismissTip(key: String) = dataStore.edit { it[DISMISSED_TIPS] = (it[DISMISSED_TIPS] ?: emptySet()) + key }
    suspend fun addDedupedAccount(id: String) = dataStore.edit { it[DEDUPED_ACCOUNTS] = (it[DEDUPED_ACCOUNTS] ?: emptySet()) + id }
    suspend fun setDedupPair(hidden: String, kept: String) = dataStore.edit {
        val others = (it[DEDUP_PAIRS] ?: emptySet()).filterNot { p -> p.startsWith("$kept|") || p.startsWith("$hidden|") }
        it[DEDUP_PAIRS] = (others + "$hidden|$kept").toSet()
        it[DEDUPED_ACCOUNTS] = (it[DEDUPED_ACCOUNTS] ?: emptySet()) + hidden
    }
    suspend fun setHistoryProgress(oldest: Long, done: Boolean) = dataStore.edit {
        it[HISTORY_OLDEST] = oldest
        it[HISTORY_DONE] = done
    }
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
    suspend fun setThemeMode(mode: Int) = dataStore.edit { it[THEME_MODE] = mode.coerceIn(0, 3) }
    suspend fun setDynamicColor(on: Boolean) = dataStore.edit { it[DYNAMIC_COLOR] = on }
    suspend fun setBudgetMode(mode: Int) = dataStore.edit { it[BUDGET_MODE] = mode.coerceIn(0, 1) }
    suspend fun setFlexBudget(minor: Long) = dataStore.edit { it[FLEX_BUDGET] = minor.coerceAtLeast(0) }
    suspend fun setFixedCategories(ids: Set<Long>) = dataStore.edit { it[FIXED_IDS] = ids.map { id -> id.toString() }.toSet() }
    suspend fun toggleWatch(item: String) = dataStore.edit {
        val cur = it[WATCHLIST] ?: emptySet()
        it[WATCHLIST] = if (item in cur) cur - item else (cur + item).toList().takeLast(8).toSet()
    }
    suspend fun setHomeCard(key: String, shown: Boolean) = dataStore.edit {
        val cur = it[HOME_HIDDEN] ?: emptySet()
        it[HOME_HIDDEN] = if (shown) cur - key else cur + key
    }
    suspend fun setBetaUpdates(on: Boolean) = dataStore.edit { it[BETA] = on }
    suspend fun setRecapDismissed(month: String) = dataStore.edit { it[RECAP_DISMISSED] = month }
    suspend fun setLastBackupAt(epoch: Long) = dataStore.edit { it[LAST_BACKUP] = epoch }
    suspend fun setCatalogVersion(v: Int) = dataStore.edit { it[CATALOG_VERSION] = v }
    suspend fun snoozeBackupNudge(until: Long) = dataStore.edit { it[BACKUP_NUDGE] = until }
    suspend fun saveSearch(encoded: String, on: Boolean) = dataStore.edit {
        val cur = it[SAVED_SEARCHES] ?: emptySet()
        it[SAVED_SEARCHES] = if (on) (cur + encoded).toList().takeLast(8).toSet() else cur - encoded
    }
    suspend fun setPaydaySource(name: String?) = dataStore.edit { if (name == null) it.remove(PAYDAY_SOURCE) else it[PAYDAY_SOURCE] = name }
    suspend fun setNotifyBills(on: Boolean) = dataStore.edit { it[NOTIFY_BILLS] = on }
    suspend fun setNotifyWeekly(on: Boolean) = dataStore.edit { it[NOTIFY_WEEKLY] = on }
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
        val NOTIFY_WEEKLY = booleanPreferencesKey("notify_weekly")
        val RECAP_DISMISSED = stringPreferencesKey("recap_dismissed")
        val LAST_BACKUP = androidx.datastore.preferences.core.longPreferencesKey("last_backup_at")
        val BACKUP_NUDGE = androidx.datastore.preferences.core.longPreferencesKey("backup_nudge_until")
        val PAYDAY_SOURCE = stringPreferencesKey("payday_source")
        val SAVED_SEARCHES = androidx.datastore.preferences.core.stringSetPreferencesKey("saved_searches")
        val BUDGET_MODE = intPreferencesKey("budget_mode")
        val FLEX_BUDGET = longPreferencesKey("flex_budget_minor")
        val FIXED_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("fixed_category_ids")
        val WATCHLIST = androidx.datastore.preferences.core.stringSetPreferencesKey("watchlist")
        val BETA = booleanPreferencesKey("beta_updates")
        val HOME_HIDDEN = androidx.datastore.preferences.core.stringSetPreferencesKey("home_hidden")
        val CATALOG_VERSION = intPreferencesKey("catalog_version")
        val HISTORY_OLDEST = longPreferencesKey("history_oldest")
        val HISTORY_DONE = booleanPreferencesKey("history_done")
        val HISTORY_DEFAULT_UPGRADED = booleanPreferencesKey("history_default_upgraded")
        val DEDUPED_ACCOUNTS = androidx.datastore.preferences.core.stringSetPreferencesKey("deduped_accounts")
        val DEDUP_PAIRS = androidx.datastore.preferences.core.stringSetPreferencesKey("dedup_pairs")
        val DISMISSED_TIPS = androidx.datastore.preferences.core.stringSetPreferencesKey("dismissed_tips")
    }
}
