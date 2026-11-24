package com.emotionalleaves.sprout.data.simplefin

import com.emotionalleaves.sprout.domain.money.Money
import com.emotionalleaves.sprout.domain.text.Sanitize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

enum class ProtocolMode { V1, V2 }

/** One error from the server: structured (v2 "errlist") or legacy (v1/v2 "errors" strings, code "legacy"). */
data class SfError(val code: String, val msg: String, val connId: String? = null, val accountId: String? = null) {
    /** Error codes per spec: prefix.subcode. Unknown subcodes are handled as the bare prefix. */
    val prefix: String get() = code.substringBefore('.', code)
    val isLegacy: Boolean get() = code == LEGACY

    companion object { const val LEGACY = "legacy" }
}

data class SfConnection(
    val connId: String,
    val name: String,
    val orgId: String?,
    val orgUrl: String?,
    val sfinUrl: String?,
)

data class SfTransaction(
    val id: String,
    /** Epoch seconds; 0 when pending. */
    val postedAt: Long,
    val amountMinor: Long,
    val description: String,
    val transactedAt: Long?,
    val pending: Boolean,
)

data class SfAccount(
    val connId: String,
    val id: String,
    val name: String,
    val currency: String,
    val balanceMinor: Long,
    val availableBalanceMinor: Long?,
    val balanceDate: Long,
    val transactions: List<SfTransaction>,
)

data class AccountSet(
    val mode: ProtocolMode,
    val errors: List<SfError>,
    val connections: List<SfConnection>,
    val accounts: List<SfAccount>,
    /** Number of rows (accounts or transactions) that were malformed and skipped. */
    val skippedRows: Int,
)

class AccountSetParseException(message: String) : Exception(message)

/**
 * Parses a SimpleFIN Account Set. Works on the JSON tree rather than strict classes so that one
 * odd field (a number instead of a numeric string, a missing optional) never loses a whole sync.
 */
object AccountSetParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun parse(body: String, mode: ProtocolMode): AccountSet {
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            throw AccountSetParseException("The bank data could not be read (not valid JSON).")
        }
        if (root !is JsonObject) throw AccountSetParseException("The bank data had an unexpected shape.")

        val errors = mutableListOf<SfError>()
        (root["errlist"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val code = o.str("code") ?: "gen."
            val msg = o.str("msg") ?: code
            errors += SfError(Sanitize.text(code, 40), Sanitize.text(msg, Sanitize.MAX_MESSAGE), o.str("conn_id"), o.str("account_id"))
        }
        (root["errors"] as? JsonArray)?.forEach { el ->
            val msg = (el as? JsonPrimitive)?.contentOrNull?.let { Sanitize.text(it, Sanitize.MAX_MESSAGE) } ?: return@forEach
            if (msg.isNotBlank() && errors.none { it.msg == msg }) errors += SfError(SfError.LEGACY, msg)
        }

        val connections = linkedMapOf<String, SfConnection>()
        (root["connections"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = o.str("conn_id") ?: return@forEach
            connections[id] = SfConnection(
                connId = id,
                name = Sanitize.text(o.str("name") ?: o.str("org_name") ?: "Bank", 120),
                orgId = o.str("org_id"),
                orgUrl = o.str("org_url"),
                sfinUrl = o.str("sfin_url"),
            )
        }

        var skipped = 0
        val accounts = mutableListOf<SfAccount>()
        (root["accounts"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject
            if (o == null) { skipped++; return@forEach }
            val id = o.str("id")
            val balance = Money.parseMinor(o.str("balance"))
            if (id == null || balance == null) { skipped++; return@forEach }

            val connId: String = o.str("conn_id") ?: run {
                // v1: derive a connection from the org object (domain, else name).
                val org = o["org"] as? JsonObject
                val domain = org?.str("domain")?.takeIf { it.isNotBlank() }
                val orgName = org?.str("name")?.takeIf { it.isNotBlank() }
                val derived = (domain ?: orgName ?: "unknown").lowercase()
                if (!connections.containsKey(derived)) {
                    connections[derived] = SfConnection(
                        connId = derived,
                        name = Sanitize.text(orgName ?: domain ?: "Bank", 120),
                        orgId = org?.str("id"),
                        orgUrl = org?.str("url") ?: domain,
                        sfinUrl = org?.str("sfin-url"),
                    )
                }
                derived
            }

            val txs = mutableListOf<SfTransaction>()
            (o["transactions"] as? JsonArray)?.forEach { tEl ->
                val t = tEl as? JsonObject
                val tid = t?.str("id")
                val amount = Money.parseMinor(t?.str("amount"))
                if (t == null || tid == null || amount == null) { skipped++; return@forEach }
                val posted = t.long("posted") ?: 0L
                val pendingFlag = t.bool("pending") ?: false
                txs += SfTransaction(
                    id = tid,
                    postedAt = if (posted < 0) 0 else posted,
                    amountMinor = amount,
                    description = Sanitize.text(t.str("description") ?: t.str("payee") ?: "", 300),
                    transactedAt = t.long("transacted_at")?.takeIf { it > 0 },
                    pending = pendingFlag || posted <= 0L,
                )
            }

            accounts += SfAccount(
                connId = connId,
                id = id,
                name = Sanitize.text(o.str("name") ?: "Account", 120),
                currency = Sanitize.text(o.str("currency") ?: "USD", 200),
                balanceMinor = balance,
                availableBalanceMinor = Money.parseMinor(o.str("available-balance")),
                balanceDate = o.long("balance-date") ?: 0L,
                transactions = txs,
            )
        }

        return AccountSet(mode, errors, connections.values.toList(), accounts, skipped)
    }

    private fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        else -> null
    }

    private fun JsonObject.long(key: String): Long? = when (val v: JsonElement? = this[key]) {
        is JsonPrimitive -> v.longOrNull ?: v.contentOrNull?.toBigDecimalOrNull()?.toLong()
        else -> null
    }

    private fun JsonObject.bool(key: String): Boolean? = when (val v = this[key]) {
        is JsonPrimitive -> v.booleanOrNull ?: when (v.contentOrNull) { "1" -> true; "0" -> false; else -> null }
        else -> null
    }
}
