package com.emotionalleaves.sprout.data.alerts

import com.emotionalleaves.sprout.data.budget.BudgetRepository
import com.emotionalleaves.sprout.data.db.BudgetAlertEntity
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.settings.SettingsStore
import com.emotionalleaves.sprout.data.sync.AlertSink
import com.emotionalleaves.sprout.data.sync.SyncProblem
import com.emotionalleaves.sprout.domain.alerts.BudgetAlerts
import com.emotionalleaves.sprout.domain.alerts.UnusualDetector
import com.emotionalleaves.sprout.domain.alerts.UnusualReason
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock

/** What to show. Text is plain; the notifier hides it on the lock screen. */
sealed interface SproutAlert {
    val title: String
    val body: String

    data class Budget(val categoryId: Long, val yearMonth: String, val threshold: Int, override val title: String, override val body: String) : SproutAlert
    data class Transaction(val transactionId: String, val reason: UnusualReason, override val title: String, override val body: String) : SproutAlert
    data class SyncTrouble(val key: String, override val title: String, override val body: String) : SproutAlert
}

/** Shows alerts. The Android implementation posts notifications; tests capture them. */
interface Notifier {
    /** Returns true if the alert was shown (permission granted and channel enabled). */
    fun show(alert: SproutAlert): Boolean
}

/**
 * Decides which alerts to show and makes sure each fires once:
 * budget thresholds via the budget_alerts table, transaction alerts via alertedAt.
 */
class AlertRepository(
    private val db: SproutDatabase,
    private val settings: SettingsStore,
    private val budgets: BudgetRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) : AlertSink {
    private val mutex = Mutex()

    override suspend fun afterSync(newOutflowIds: List<String>, problems: List<SyncProblem>) {
        checkTransactions(newOutflowIds)
        evaluateBudgets()
        val s = settings.current()
        if (s.notifySyncProblems) problems.forEach { notifier.show(SproutAlert.SyncTrouble(it.key, it.title, it.message)) }
    }

    /** After a sync, an import or a recategorization. Fires 80%/100% alerts at most once per category per month. */
    suspend fun evaluateBudgets() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val month = budgets.currentMonth()
            val ym = month.toString()
            val m = budgets.observeMonth(month).first()
            val fired = db.alerts().firedIn(ym).map { it.categoryId to it.threshold }.toSet()
            val decisions = BudgetAlerts.evaluate(m.budgeted, fired)
            if (decisions.isEmpty()) return@withLock
            val enabled = settings.current().notifyBudget
            val now = clock.instant().epochSecond
            for (d in decisions) {
                // Recorded even when notifications are off, so turning them on later doesn't replay old crossings.
                db.alerts().record(d.record.map { BudgetAlertEntity(d.categoryId, ym, it, now) })
                if (!enabled) continue
                val title = if (d.threshold >= 100) "${d.categoryName}: over budget" else "${d.categoryName}: 80% of budget used"
                val body = "${Money.format(d.spentMinor)} of ${Money.format(d.budgetMinor)} this month"
                notifier.show(SproutAlert.Budget(d.categoryId, ym, d.threshold, title, body))
            }
        }
    }

    /** Large or unusual charges among newly synced outflows. Never used for CSV imports. */
    suspend fun checkTransactions(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val s = settings.current()
        if (!s.notifyUnusual) return@withContext
        for (id in ids) {
            val tx = db.transactions().getById(id) ?: continue
            if (tx.alertedAt != null || tx.amountMinor >= 0) continue
            val priors = db.alerts().priorAmounts(tx.merchantKey, tx.id, tx.occurredAt)
            val reason = UnusualDetector.check(tx.amountMinor, priors, s.largeTxThresholdMinor) ?: continue
            val merchant = MerchantNormalizer.displayName(tx.merchantKey)
            val amount = Money.format(-tx.amountMinor)
            val (title, body) = when (reason) {
                UnusualReason.LARGE -> "Large charge: $amount" to "$merchant"
                UnusualReason.UNUSUAL_FOR_MERCHANT -> "Unusual charge: $amount" to "Much more than you usually spend at $merchant"
                UnusualReason.NEW_MERCHANT -> "New merchant: $amount" to "First charge from $merchant"
            }
            if (notifier.show(SproutAlert.Transaction(tx.id, reason, title, body))) {
                db.alerts().markAlerted(tx.id, clock.instant().epochSecond)
            }
        }
    }
}
