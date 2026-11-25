package com.emotionalleaves.sprout.data.debt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.debtStore: DataStore<Preferences> by preferencesDataStore(name = "debt_planner")

/** Your inputs for the debt planner: interest rate and minimum payment per account, and the monthly amount. */
data class DebtInputs(val aprPercent: Double?, val minPaymentMinor: Long?)

class DebtStore(context: Context) {
    private val store = context.applicationContext.debtStore

    val inputs: Flow<Map<String, DebtInputs>> = store.data.map { p ->
        p.asMap().keys.map { it.name }.filter { it.startsWith("d:") }.associate { k ->
            val (apr, min) = (p[stringPreferencesKey(k)] ?: "|").split('|').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            k.removePrefix("d:") to DebtInputs(apr.toDoubleOrNull(), min.toLongOrNull())
        }
    }
    val monthlyPayment: Flow<Long?> = store.data.map { it[MONTHLY] }

    suspend fun set(accountId: String, aprPercent: Double?, minPaymentMinor: Long?) = store.edit {
        it[stringPreferencesKey("d:$accountId")] = "${aprPercent ?: ""}|${minPaymentMinor ?: ""}"
    }

    suspend fun setMonthly(minor: Long) = store.edit { it[MONTHLY] = minor.coerceAtLeast(0) }

    private companion object { val MONTHLY = longPreferencesKey("monthly_payment") }
}
