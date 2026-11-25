package com.emotionalleaves.sprout.ui

import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.data.settings.Settings
import com.emotionalleaves.sprout.ui.home.HomeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {
    private val day = 86_400L
    private val account = AccountEntity(
        id = "a", source = AccountSource.SIMPLEFIN, remoteId = "A", connId = null, name = "Checking", displayName = null,
        type = AccountType.CHECKING, currency = "USD", balanceMinor = 0, availableBalanceMinor = null, balanceDate = 0,
        includeInTotals = true, hidden = false, firstSyncCompleted = true, lastSyncedAt = null, createdAt = 1_000 * day,
    )

    @Test fun quietUntilThereIsAWeekOfData() {
        assertFalse(HomeState().backupDue(2_000 * day))
        assertFalse(HomeState(accounts = listOf(account)).backupDue(1_005 * day))
        assertTrue(HomeState(accounts = listOf(account)).backupDue(1_008 * day))
    }

    @Test fun monthlyAfterABackupAndSnoozable() {
        val s = HomeState(accounts = listOf(account), settings = Settings(lastBackupAt = 1_010 * day))
        assertFalse(s.backupDue(1_030 * day))
        assertTrue(s.backupDue(1_041 * day))
        assertFalse(s.copy(settings = s.settings.copy(backupNudgeUntil = 1_050 * day)).backupDue(1_041 * day))
    }
}
