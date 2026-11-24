package com.emotionalleaves.sprout.data.csv

import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.accounts.AccountTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class ExportKind(val label: String, val fileStem: String) {
    TRANSACTIONS("Transactions", "transactions"),
    ACCOUNTS("Accounts", "accounts"),
    CATEGORIES("Categories", "categories"),
    BUDGETS("Budgets", "budgets"),
    RULES("Merchant rules", "rules"),
    RECURRING("Recurring charges", "recurring"),
}

/**
 * Builds CSV exports. Only data columns are read here; the SimpleFIN access URL lives in a separate encrypted
 * store and is never touched, so it can't end up in a file.
 */
class ExportRepository(private val db: SproutDatabase, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {

    /** [from]/[to] are inclusive dates; null means all time. */
    suspend fun export(kind: ExportKind, from: LocalDate? = null, to: LocalDate? = null): String = withContext(Dispatchers.IO) {
        val z = zone()
        val cats = db.categories().getAll().associateBy { it.id }
        fun catName(id: Long) = cats[id]?.name ?: ""
        when (kind) {
            ExportKind.TRANSACTIONS -> {
                val start = from?.atStartOfDay(z)?.toEpochSecond() ?: Long.MIN_VALUE
                val end = to?.plusDays(1)?.atStartOfDay(z)?.toEpochSecond() ?: Long.MAX_VALUE
                CsvExport.transactions(
                    db.transactions().exportRows(start, end).map {
                        ExportTx(it.occurredAt, it.description, it.amountMinor, it.categoryName, it.accountName, it.pending, it.note, it.merchantKey)
                    },
                    z,
                )
            }
            ExportKind.ACCOUNTS -> CsvExport.table(
                listOf("Name", "Type", "Currency", "Balance", "Available", "Balance date", "In total", "Hidden", "Source"),
                db.accounts().getAll().map { a ->
                    listOf(a.label, a.type.name.lowercase(), AccountTypes.currencyLabel(a.currency), CsvExport.amount(a.balanceMinor),
                        a.availableBalanceMinor?.let(CsvExport::amount).orEmpty(), CsvExport.isoDate(a.balanceDate, z),
                        yn(a.includeInTotals), yn(a.hidden), a.source.name.lowercase())
                },
            )
            ExportKind.CATEGORIES -> CsvExport.table(
                listOf("Name", "Kind", "Color", "Archived"),
                cats.values.sortedBy { it.sortOrder }.map { c ->
                    listOf(c.name, c.kind.name.lowercase(), "#%06X".format(c.colorArgb and 0xFFFFFF), yn(c.archived))
                },
            )
            ExportKind.BUDGETS -> CsvExport.table(
                listOf("Category", "Month", "Amount"),
                db.budgets().observeDefaults().first().map { listOf(catName(it.categoryId), "every month", CsvExport.amount(it.amountMinor)) } +
                    db.budgets().allOverrides().map { listOf(catName(it.categoryId), it.yearMonth, CsvExport.amount(it.amountMinor)) },
            )
            ExportKind.RULES -> CsvExport.table(
                listOf("Match", "Pattern", "Category", "Origin"),
                db.merchantRules().getAll().map { listOf(it.matchType.name.lowercase(), it.pattern, catName(it.categoryId), it.origin.name.lowercase()) },
            )
            ExportKind.RECURRING -> CsvExport.table(
                listOf("Name", "Merchant", "Category", "How often", "Amount", "Last charge", "Next due", "Status"),
                db.recurring().getAll().map { r ->
                    listOf(r.displayName, r.merchantKey, catName(r.categoryId), r.cadence.name.lowercase(), CsvExport.amount(r.typicalAmountMinor),
                        if (r.lastChargeAt > 0) CsvExport.isoDate(r.lastChargeAt, z) else "", CsvExport.isoDate(r.nextDueAt, z), r.status.name.lowercase())
                },
            )
        }
    }

    private fun yn(b: Boolean) = if (b) "yes" else "no"
}
