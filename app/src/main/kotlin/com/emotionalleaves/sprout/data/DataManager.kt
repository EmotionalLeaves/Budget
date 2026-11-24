package com.emotionalleaves.sprout.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.secure.SecureStore
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Disconnect and delete-all. Both really delete: database rows, settings and the Keystore key. */
class DataManager(
    private val context: Context?,
    private val db: SproutDatabase,
    private val settings: SettingsStore,
    private val secure: SecureStore,
    private val sync: SyncRepository,
) {
    /**
     * Forgets the SimpleFIN connection: deletes the stored access URL and its Keystore key and stops background
     * sync. Local data is kept unless [alsoDeleteBankData], which removes synced accounts and their transactions
     * (imported accounts stay).
     */
    suspend fun disconnect(alsoDeleteBankData: Boolean) = withContext(Dispatchers.IO) {
        secure.clear()
        context?.let { WorkManager.getInstance(it).cancelAllWork() }
        if (alsoDeleteBankData) {
            db.withTransaction {
                db.accounts().deleteSimpleFinAccounts()
                db.connections().deleteAll()
            }
        }
        settings.onNewConnection()
    }

    /** Wipes everything: every table, all settings and the Keystore entry. Sprout starts over at onboarding. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        context?.let { WorkManager.getInstance(it).cancelAllWork() }
        secure.clear()
        db.clearAllTables()
        settings.clearAll()
        sync.ensureSeeded()
    }
}
