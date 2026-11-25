package com.emotionalleaves.sprout.data.backup

import androidx.room.withTransaction
import com.emotionalleaves.sprout.data.db.AccountEntity
import com.emotionalleaves.sprout.data.db.AccountSource
import com.emotionalleaves.sprout.data.db.BudgetAlertEntity
import com.emotionalleaves.sprout.data.db.BudgetOverrideEntity
import com.emotionalleaves.sprout.data.db.BudgetRolloverEntity
import com.emotionalleaves.sprout.data.db.CategoryBudgetEntity
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.ConnectionEntity
import com.emotionalleaves.sprout.data.db.ConnectionStatus
import com.emotionalleaves.sprout.data.db.MerchantRuleEntity
import com.emotionalleaves.sprout.data.db.RecurringChargeEntity
import com.emotionalleaves.sprout.data.db.RecurringStatus
import com.emotionalleaves.sprout.data.db.RuleOrigin
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.data.db.TransactionEntity
import com.emotionalleaves.sprout.data.db.TransactionSplitEntity
import com.emotionalleaves.sprout.data.db.TagEntity
import com.emotionalleaves.sprout.data.db.TransactionTagEntity
import com.emotionalleaves.sprout.domain.accounts.AccountType
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.domain.recurring.Cadence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Clock

data class RestoreSummary(val transactions: Int, val accounts: Int, val budgets: Int, val rules: Int, val bills: Int)

/**
 * Password-encrypted backups of every table except the sync log. The SimpleFIN access URL is never included:
 * it lives in the encrypted secure store, which this class doesn't touch. After a restore, reconnect with a new token.
 */
class BackupRepository(private val db: SproutDatabase, private val clock: Clock) {

    suspend fun create(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        BackupCrypto.encrypt(toJson().toByteArray(Charsets.UTF_8), password)
    }

    /** Decrypts and checks the file. Throws [BackupException] with a friendly message on any problem. */
    suspend fun read(file: ByteArray, password: CharArray): JsonObject = withContext(Dispatchers.Default) {
        val obj = try {
            Json.parseToJsonElement(String(BackupCrypto.decrypt(file, password), Charsets.UTF_8)).jsonObject
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("This backup file is damaged.")
        }
        val schema = obj["schema"]?.let { (it as? JsonPrimitive)?.intOrNull } ?: throw BackupException("This backup file is damaged.")
        if (schema > SproutDatabase.VERSION) throw BackupException("This backup was made by a newer version of Sprout. Update Sprout first.")
        obj
    }

    /** Replaces all budgeting data with the backup's, in one transaction: either everything is restored or nothing changes. */
    suspend fun restore(backup: JsonObject): RestoreSummary = withContext(Dispatchers.IO) {
        val conns = arr(backup, "connections").map(::connection)
        val accounts = arr(backup, "accounts").map(::account)
        val cats = arr(backup, "categories").map(::category)
        val txs = arr(backup, "transactions").map(::transaction)
        val budgets = arr(backup, "categoryBudgets").map { o -> CategoryBudgetEntity(o.long("categoryId"), o.long("amountMinor"), o.long("updatedAt")) }
        val overrides = arr(backup, "budgetOverrides").map { o -> BudgetOverrideEntity(o.long("categoryId"), o.str("yearMonth"), o.long("amountMinor")) }
        val alerts = arr(backup, "budgetAlerts").map { o -> BudgetAlertEntity(o.long("categoryId"), o.str("yearMonth"), o.int("threshold"), o.long("firedAt")) }
        val rules = arr(backup, "merchantRules").map(::rule)
        val recurring = arr(backup, "recurringCharges").map(::recurringCharge)
        // Added in schema 2; older backups simply have none.
        val goals = arr(backup, "savingsGoals").map(::goal)
        // Added in schema 5.
        val tags = arr(backup, "tags").map { o -> TagEntity(o.long("id"), o.str("name"), o.int("colorArgb"), o.long("createdAt")) }
        val txTags = arr(backup, "transactionTags").map { o -> TransactionTagEntity(o.str("transactionId"), o.long("tagId")) }
        // Added in schema 4.
        val splits = arr(backup, "transactionSplits").map { o ->
            TransactionSplitEntity(o.long("id"), o.str("transactionId"), o.long("categoryId"), o.long("amountMinor"), o.long("createdAt"))
        }
        // Added in schema 3.
        val rollovers = arr(backup, "budgetRollovers").map { o -> BudgetRolloverEntity(o.long("categoryId"), o.str("sinceMonth")) }
        if (cats.isEmpty()) throw BackupException("This backup file is damaged.")
        val b = db.backup()
        db.withTransaction {
            b.wipeTransactionTags(); b.wipeTags(); b.wipeTransactionSplits(); b.wipeTransactions(); b.wipeBudgetAlerts(); b.wipeBudgetRollovers(); b.wipeBudgetOverrides(); b.wipeCategoryBudgets()
            b.wipeMerchantRules(); b.wipeRecurring(); db.goals().wipe(); b.wipeAccounts(); b.wipeConnections(); b.wipeCategories()
            b.putCategories(cats); b.putConnections(conns); b.putAccounts(accounts)
            txs.chunked(500).forEach { b.putTransactions(it) }
            b.putCategoryBudgets(budgets); b.putBudgetOverrides(overrides); b.putBudgetAlerts(alerts)
            b.putMerchantRules(rules); b.putRecurring(recurring); db.goals().putAll(goals)
            b.putBudgetRollovers(rollovers)
            splits.chunked(500).forEach { b.putTransactionSplits(it) }
            b.putTags(tags)
            txTags.chunked(500).forEach { b.putTransactionTags(it) }
        }
        RestoreSummary(txs.size, accounts.size, budgets.size, rules.size, recurring.size)
    }

    // --- Writing ---

    suspend fun toJson(): String = withContext(Dispatchers.IO) {
        val b = db.backup()
        buildJsonObject {
            put("app", "Sprout")
            put("schema", SproutDatabase.VERSION)
            put("createdAt", clock.instant().epochSecond)
            put("connections", buildJsonArray { db.connections().getAll().forEach { c ->
                add(buildJsonObject {
                    put("connId", c.connId); put("name", c.name); put("orgId", c.orgId); put("orgUrl", c.orgUrl); put("sfinUrl", c.sfinUrl)
                    put("status", c.status.name); put("lastErrorCode", c.lastErrorCode); put("lastErrorMessage", c.lastErrorMessage)
                    put("updatedAt", c.updatedAt)
                })
            } })
            put("accounts", buildJsonArray { db.accounts().getAll().forEach { a ->
                add(buildJsonObject {
                    put("id", a.id); put("source", a.source.name); put("remoteId", a.remoteId); put("connId", a.connId); put("name", a.name)
                    put("displayName", a.displayName); put("type", a.type.name); put("currency", a.currency); put("balanceMinor", a.balanceMinor)
                    put("availableBalanceMinor", a.availableBalanceMinor); put("balanceDate", a.balanceDate); put("includeInTotals", a.includeInTotals)
                    put("hidden", a.hidden); put("firstSyncCompleted", a.firstSyncCompleted); put("lastSyncedAt", a.lastSyncedAt)
                    put("createdAt", a.createdAt); put("lastErrorCode", a.lastErrorCode); put("lastErrorMessage", a.lastErrorMessage)
                })
            } })
            put("categories", buildJsonArray { db.categories().getAll().forEach { c ->
                add(buildJsonObject {
                    put("id", c.id); put("key", c.key); put("name", c.name); put("kind", c.kind.name); put("iconKey", c.iconKey)
                    put("colorArgb", c.colorArgb); put("sortOrder", c.sortOrder); put("archived", c.archived)
                })
            } })
            put("transactions", buildJsonArray { b.transactions().forEach { t ->
                add(buildJsonObject {
                    put("id", t.id); put("accountId", t.accountId); put("remoteId", t.remoteId); put("postedAt", t.postedAt)
                    put("transactedAt", t.transactedAt); put("occurredAt", t.occurredAt); put("amountMinor", t.amountMinor)
                    put("currency", t.currency); put("description", t.description); put("merchantKey", t.merchantKey); put("pending", t.pending)
                    put("categoryId", t.categoryId); put("categorySource", t.categorySource.name); put("note", t.note)
                    put("excludedFromBudget", t.excludedFromBudget); put("recurringChargeId", t.recurringChargeId); put("alertedAt", t.alertedAt)
                    put("importedAt", t.importedAt); put("updatedAt", t.updatedAt)
                })
            } })
            put("categoryBudgets", buildJsonArray { db.budgets().observeDefaults().first().forEach { x ->
                add(buildJsonObject { put("categoryId", x.categoryId); put("amountMinor", x.amountMinor); put("updatedAt", x.updatedAt) })
            } })
            put("budgetOverrides", buildJsonArray { b.budgetOverrides().forEach { x ->
                add(buildJsonObject { put("categoryId", x.categoryId); put("yearMonth", x.yearMonth); put("amountMinor", x.amountMinor) })
            } })
            put("tags", buildJsonArray { b.tags().forEach { x ->
                add(buildJsonObject { put("id", x.id); put("name", x.name); put("colorArgb", x.colorArgb); put("createdAt", x.createdAt) })
            } })
            put("transactionTags", buildJsonArray { b.transactionTags().forEach { x ->
                add(buildJsonObject { put("transactionId", x.transactionId); put("tagId", x.tagId) })
            } })
            put("transactionSplits", buildJsonArray { b.transactionSplits().forEach { x ->
                add(buildJsonObject {
                    put("id", x.id); put("transactionId", x.transactionId); put("categoryId", x.categoryId)
                    put("amountMinor", x.amountMinor); put("createdAt", x.createdAt)
                })
            } })
            put("budgetRollovers", buildJsonArray { b.budgetRollovers().forEach { x ->
                add(buildJsonObject { put("categoryId", x.categoryId); put("sinceMonth", x.sinceMonth) })
            } })
            put("budgetAlerts", buildJsonArray { b.budgetAlerts().forEach { x ->
                add(buildJsonObject { put("categoryId", x.categoryId); put("yearMonth", x.yearMonth); put("threshold", x.threshold); put("firedAt", x.firedAt) })
            } })
            put("merchantRules", buildJsonArray { db.merchantRules().getAll().forEach { r ->
                add(buildJsonObject {
                    put("id", r.id); put("matchType", r.matchType.name); put("pattern", r.pattern); put("categoryId", r.categoryId)
                    put("origin", r.origin.name); put("hitCount", r.hitCount); put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                })
            } })
            put("recurringCharges", buildJsonArray { db.recurring().getAll().forEach { r ->
                add(buildJsonObject {
                    put("id", r.id); put("merchantKey", r.merchantKey); put("displayName", r.displayName); put("categoryId", r.categoryId)
                    put("accountId", r.accountId); put("cadence", r.cadence.name); put("typicalAmountMinor", r.typicalAmountMinor)
                    put("lastChargeAt", r.lastChargeAt); put("nextDueAt", r.nextDueAt); put("status", r.status.name)
                    put("userEdited", r.userEdited); put("confidence", r.confidence); put("occurrences", r.occurrences); put("updatedAt", r.updatedAt)
                })
            } })
            put("savingsGoals", buildJsonArray { db.goals().getAll().forEach { g ->
                add(buildJsonObject {
                    put("id", g.id); put("name", g.name); put("targetMinor", g.targetMinor); put("targetDate", g.targetDate)
                    put("accountId", g.accountId); put("savedMinor", g.savedMinor); put("archived", g.archived)
                    put("createdAt", g.createdAt); put("updatedAt", g.updatedAt)
                })
            } })
        }.toString()
    }

    // --- Reading ---

    private fun arr(o: JsonObject, key: String): List<JsonObject> =
        (o[key] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    private fun JsonObject.p(k: String): JsonPrimitive? = (this[k] as? JsonPrimitive)?.takeIf { this[k] !is JsonNull }
    private fun JsonObject.str(k: String): String = p(k)?.contentOrNull ?: bad()
    private fun JsonObject.strOrNull(k: String): String? = p(k)?.contentOrNull
    private fun JsonObject.long(k: String): Long = p(k)?.longOrNull ?: bad()
    private fun JsonObject.longOrNull(k: String): Long? = p(k)?.longOrNull
    private fun JsonObject.int(k: String): Int = p(k)?.intOrNull ?: bad()
    private fun JsonObject.bool(k: String): Boolean = p(k)?.booleanOrNull ?: bad()
    private fun bad(): Nothing = throw BackupException("This backup file is damaged.")
    private inline fun <reified E : Enum<E>> JsonObject.enum(k: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == strOrNull(k) } ?: fallback

    private fun connection(o: JsonObject) = ConnectionEntity(
        o.str("connId"), o.str("name"), o.strOrNull("orgId"), o.strOrNull("orgUrl"), o.strOrNull("sfinUrl"),
        o.enum("status", ConnectionStatus.NEEDS_ATTENTION), o.strOrNull("lastErrorCode"), o.strOrNull("lastErrorMessage"), o.long("updatedAt"),
    )

    private fun account(o: JsonObject) = AccountEntity(
        id = o.str("id"), source = o.enum("source", AccountSource.MANUAL), remoteId = o.strOrNull("remoteId"), connId = o.strOrNull("connId"),
        name = o.str("name"), displayName = o.strOrNull("displayName"), type = o.enum("type", AccountType.OTHER), currency = o.str("currency"),
        balanceMinor = o.long("balanceMinor"), availableBalanceMinor = o.longOrNull("availableBalanceMinor"), balanceDate = o.long("balanceDate"),
        includeInTotals = o.bool("includeInTotals"), hidden = o.bool("hidden"), firstSyncCompleted = o.bool("firstSyncCompleted"),
        lastSyncedAt = o.longOrNull("lastSyncedAt"), createdAt = o.long("createdAt"),
        lastErrorCode = o.strOrNull("lastErrorCode"), lastErrorMessage = o.strOrNull("lastErrorMessage"),
    )

    private fun category(o: JsonObject) = CategoryEntity(
        id = o.long("id"), key = o.strOrNull("key"), name = o.str("name"), kind = o.enum("kind", CategoryKind.EXPENSE),
        iconKey = o.strOrNull("iconKey") ?: "custom", colorArgb = o.int("colorArgb"), sortOrder = o.int("sortOrder"), archived = o.bool("archived"),
    )

    private fun transaction(o: JsonObject) = TransactionEntity(
        id = o.str("id"), accountId = o.str("accountId"), remoteId = o.str("remoteId"), postedAt = o.long("postedAt"),
        transactedAt = o.longOrNull("transactedAt"), occurredAt = o.long("occurredAt"), amountMinor = o.long("amountMinor"),
        currency = o.str("currency"), description = o.str("description"), merchantKey = o.str("merchantKey"), pending = o.bool("pending"),
        categoryId = o.long("categoryId"), categorySource = o.enum("categorySource", CategorySource.NONE), note = o.strOrNull("note"),
        excludedFromBudget = o.bool("excludedFromBudget"), recurringChargeId = o.longOrNull("recurringChargeId"),
        alertedAt = o.longOrNull("alertedAt"), importedAt = o.long("importedAt"), updatedAt = o.long("updatedAt"),
    )

    private fun rule(o: JsonObject) = MerchantRuleEntity(
        id = o.long("id"), matchType = o.enum("matchType", RuleType.EXACT), pattern = o.str("pattern"), categoryId = o.long("categoryId"),
        origin = o.enum("origin", RuleOrigin.USER_CREATED), hitCount = o.int("hitCount"), createdAt = o.long("createdAt"), updatedAt = o.long("updatedAt"),
    )

    private fun goal(o: JsonObject) = com.emotionalleaves.sprout.data.db.SavingsGoalEntity(
        id = o.long("id"), name = o.str("name"), targetMinor = o.long("targetMinor"), targetDate = o.strOrNull("targetDate"),
        accountId = o.strOrNull("accountId"), savedMinor = o.long("savedMinor"), archived = o.bool("archived"),
        createdAt = o.long("createdAt"), updatedAt = o.long("updatedAt"),
    )

    private fun recurringCharge(o: JsonObject) = RecurringChargeEntity(
        id = o.long("id"), merchantKey = o.str("merchantKey"), displayName = o.str("displayName"), categoryId = o.long("categoryId"),
        accountId = o.strOrNull("accountId"), cadence = o.enum("cadence", Cadence.MONTHLY), typicalAmountMinor = o.long("typicalAmountMinor"),
        lastChargeAt = o.long("lastChargeAt"), nextDueAt = o.long("nextDueAt"), status = o.enum("status", RecurringStatus.DETECTED),
        userEdited = o.bool("userEdited"), confidence = o.p("confidence")?.floatOrNull ?: 0f, occurrences = o.int("occurrences"),
        updatedAt = o.long("updatedAt"),
    )
}
