package com.emotionalleaves.sprout.domain.sync

import com.emotionalleaves.sprout.data.simplefin.AccountSet
import com.emotionalleaves.sprout.data.simplefin.SfError

enum class SyncOutcome { SUCCESS, PARTIAL, FAILED }

/** How the errors in one Account Set map onto connections, accounts and the sync result. */
data class ErrorPlan(
    /** con.* errors that name a connection: that connection NEEDS_ATTENTION. */
    val connectionErrors: Map<String, SfError>,
    /** act.* errors that name an account. Key: account id (and conn id when given). */
    val accountErrors: List<SfError>,
    /** gen.* errors, legacy strings, and anything that names nothing. */
    val generalErrors: List<SfError>,
    val outcome: SyncOutcome,
) {
    fun accountError(connId: String, accountId: String): SfError? =
        accountErrors.firstOrNull { it.accountId == accountId && (it.connId == null || it.connId == connId) }

    /** Comma-separated codes for SyncLog. */
    val codes: String
        get() = (connectionErrors.values + accountErrors + generalErrors).map { it.code }.distinct().joinToString(",")
}

object SyncErrors {
    fun plan(set: AccountSet): ErrorPlan {
        val conn = linkedMapOf<String, SfError>()
        val acct = mutableListOf<SfError>()
        val general = mutableListOf<SfError>()
        for (e in set.errors) {
            when {
                e.prefix == "con" && e.connId != null -> conn.putIfAbsent(e.connId, e)
                e.prefix == "act" && e.accountId != null -> acct += e
                e.accountId != null -> acct += e
                e.connId != null -> conn.putIfAbsent(e.connId, e)
                else -> general += e
            }
        }
        val outcome = when {
            set.errors.isEmpty() && set.skippedRows == 0 -> SyncOutcome.SUCCESS
            set.accounts.isNotEmpty() -> SyncOutcome.PARTIAL
            set.errors.isEmpty() -> SyncOutcome.SUCCESS
            else -> SyncOutcome.FAILED
        }
        return ErrorPlan(conn, acct, general, outcome)
    }

    /** SimpleFIN Bridge warns in its messages before it disables an over-quota token. Back off for this long. */
    const val RATE_LIMIT_BACKOFF_SECONDS = 12 * 3600L
    private val RATE_LIMIT_WORDS = Regex("(?i)rate.?limit|too many requests|request limit|quota|exceeded|slow down|throttl")

    /** True when any error reads like a rate-limit warning (structured or legacy). */
    fun isRateLimitWarning(errors: List<SfError>): Boolean =
        errors.any { RATE_LIMIT_WORDS.containsMatchIn(it.msg) || RATE_LIMIT_WORDS.containsMatchIn(it.code) }

    /** Plain-language explanation for a code, used when a server message is missing. */
    fun explain(code: String): String = when {
        code == "con.auth" -> "Your bank needs you to sign in again in SimpleFIN Bridge."
        code.startsWith("con") -> "There's a problem with this bank connection in SimpleFIN Bridge."
        code == "act.missingdata" -> "Some recent transactions may be missing. Sprout will try again later."
        code.startsWith("act") -> "This account couldn't be updated. Sprout will try again later."
        code == "gen.auth" -> "SimpleFIN couldn't sign in. You may need a new setup token."
        else -> "SimpleFIN reported a problem."
    }
}
