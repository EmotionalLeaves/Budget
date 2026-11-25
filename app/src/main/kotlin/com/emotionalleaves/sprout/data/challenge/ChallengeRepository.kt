package com.emotionalleaves.sprout.data.challenge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emotionalleaves.sprout.data.db.SproutDatabase
import com.emotionalleaves.sprout.domain.challenge.Challenge
import com.emotionalleaves.sprout.domain.challenge.ChallengeProgress
import com.emotionalleaves.sprout.domain.challenge.ChallengeType
import com.emotionalleaves.sprout.domain.challenge.Challenges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val Context.challengeStore: DataStore<Preferences> by preferencesDataStore(name = "challenges")

/** Your spending challenges (kept on the phone), and their progress from your transactions. */
class ChallengeRepository(context: Context, private val db: SproutDatabase, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private val store = context.applicationContext.challengeStore
    private val key = stringPreferencesKey("list")

    val challenges: Flow<List<Challenge>> = store.data.map { p -> decode(p[key]) }

    suspend fun add(type: ChallengeType, title: String, days: Int, categoryId: Long? = null, amountMinor: Long = 0, goal: Int = 0) {
        val ch = Challenge(UUID.randomUUID().toString(), type, title.take(60), LocalDate.now(zone()), days.coerceIn(1, 90), categoryId, amountMinor, goal)
        store.edit { it[key] = encode((decode(it[key]) + ch).takeLast(20)) }
    }

    suspend fun remove(id: String) = store.edit { it[key] = encode(decode(it[key]).filter { c -> c.id != id }) }

    /** Progress for every challenge, newest first. */
    suspend fun progress(today: LocalDate = LocalDate.now(zone())): List<ChallengeProgress> = withContext(Dispatchers.IO) {
        val list = challenges.first()
        if (list.isEmpty()) return@withContext emptyList()
        val z = zone()
        val from = list.minOf { it.start }.atStartOfDay(z).toEpochSecond()
        val to = today.plusDays(1).atStartOfDay(z).toEpochSecond()
        val spend = db.budgets().spendRows(from, to).filter { it.amountMinor < 0 }
            .groupBy { Instant.ofEpochSecond(it.occurredAt).atZone(z).toLocalDate() }
            .mapValues { (_, l) -> l.groupBy { it.categoryId }.mapValues { (_, x) -> -x.sumOf { it.amountMinor } } }
        list.reversed().map { Challenges.evaluate(it, spend, today) }
    }

    private fun encode(list: List<Challenge>): String = buildJsonArray {
        list.forEach { c ->
            add(buildJsonObject {
                put("id", c.id); put("type", c.type.name); put("title", c.title); put("start", c.start.toString()); put("days", c.days)
                c.categoryId?.let { put("categoryId", it) }; put("amountMinor", c.amountMinor); put("goal", c.goal)
            })
        }
    }.toString()

    private fun decode(raw: String?): List<Challenge> = runCatching {
        (Json.parseToJsonElement(raw ?: "[]") as JsonArray).mapNotNull { e ->
            runCatching {
                val o = e.jsonObject
                Challenge(
                    o.getValue("id").jsonPrimitive.content, ChallengeType.valueOf(o.getValue("type").jsonPrimitive.content),
                    o.getValue("title").jsonPrimitive.content, LocalDate.parse(o.getValue("start").jsonPrimitive.content),
                    o.getValue("days").jsonPrimitive.int, (o["categoryId"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
                    o.getValue("amountMinor").jsonPrimitive.long, o.getValue("goal").jsonPrimitive.int,
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
