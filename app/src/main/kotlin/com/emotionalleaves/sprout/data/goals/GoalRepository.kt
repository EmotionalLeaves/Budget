package com.emotionalleaves.sprout.data.goals

import kotlinx.coroutines.flow.map
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.SavingsGoalEntity
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.goals.GoalMath
import com.emotionalleaves.sprout.domain.goals.GoalProgress
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class GoalView(val goal: SavingsGoalEntity, val account: AccountEntity?, val progress: GoalProgress)

class GoalRepository(
    private val db: SproutDatabase,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private fun now() = clock.instant().epochSecond

    /** Goals with progress. A linked account's balance is the amount saved; otherwise the amount the user entered. */
    fun observe(): Flow<List<GoalView>> = combine(db.goals().observeActive(), db.accounts().observeAll()) { goals, accounts ->
        val today = LocalDate.now(clock.withZone(zone()))
        val byId = accounts.associateBy { it.id }
        goals.map { g ->
            val account = g.accountId?.let(byId::get)
            val saved = account?.balanceMinor ?: g.savedMinor
            GoalView(g, account, GoalMath.progress(saved, g.targetMinor, today, g.targetDate?.let(LocalDate::parse)))
        }
    }

    /** This month's total set-aside for planned (non-monthly) expenses, which safe to spend keeps back. */
    fun observePlannedSetAside(): Flow<Long> = observe().map { list ->
        val today = LocalDate.now(clock.withZone(zone()))
        list.filter { it.goal.kind == KIND_PLANNED && it.goal.targetDate != null }.sumOf { v ->
            com.emotionalleaves.sprout.domain.insights.PlannedExpense.monthlySetAside(
                v.goal.targetMinor, v.progress.savedMinor, LocalDate.parse(v.goal.targetDate), today,
            )
        }
    }

    suspend fun save(id: Long?, name: String, targetMinor: Long, targetDate: LocalDate?, accountId: String?, savedMinor: Long, kind: String = KIND_SAVINGS) =
        withContext(Dispatchers.IO) {
            val clean = Sanitize.text(name, 60).ifBlank { "Goal" }
            val now = now()
            if (id == null) {
                db.goals().insert(SavingsGoalEntity(name = clean, targetMinor = targetMinor.coerceAtLeast(1), targetDate = targetDate?.toString(),
                    accountId = accountId, savedMinor = savedMinor.coerceAtLeast(0), archived = false, createdAt = now, updatedAt = now, kind = kind))
            } else {
                val g = db.goals().getAll().firstOrNull { it.id == id } ?: return@withContext 0L
                db.goals().update(g.copy(name = clean, targetMinor = targetMinor.coerceAtLeast(1), targetDate = targetDate?.toString(),
                    accountId = accountId, savedMinor = savedMinor.coerceAtLeast(0), updatedAt = now, kind = kind))
                id
            }
        }

    /** For goals without a linked account: add (or with a negative amount, take out) money. */
    suspend fun addMoney(id: Long, amountMinor: Long) = withContext(Dispatchers.IO) {
        val g = db.goals().getAll().firstOrNull { it.id == id } ?: return@withContext
        db.goals().update(g.copy(savedMinor = (g.savedMinor + amountMinor).coerceAtLeast(0), updatedAt = now()))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { db.goals().delete(id) }

    companion object {
        const val KIND_SAVINGS = "SAVINGS"
        const val KIND_PLANNED = "PLANNED"
    }
}
