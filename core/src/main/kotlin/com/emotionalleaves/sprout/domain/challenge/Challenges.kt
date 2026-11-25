package com.emotionalleaves.sprout.domain.challenge

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ChallengeType {
    /** Spend nothing in a category for [Challenge.days] days. */
    NO_SPEND_CATEGORY,
    /** Keep a category under [Challenge.amountMinor] for [Challenge.days] days. */
    UNDER_AMOUNT,
    /** Have at least [Challenge.goal] no-spend days within [Challenge.days] days. */
    NO_SPEND_DAYS,
}

data class Challenge(
    val id: String,
    val type: ChallengeType,
    val title: String,
    val start: LocalDate,
    val days: Int,
    val categoryId: Long? = null,
    val amountMinor: Long = 0,
    val goal: Int = 0,
    /** When you tapped start (epoch seconds). Spending earlier that same day doesn't count against you. */
    val startedAt: Long? = null,
) {
    val end: LocalDate get() = start.plusDays(days.toLong() - 1)
}

enum class ChallengeStatus { ON_TRACK, WON, MISSED }

data class ChallengeProgress(
    val challenge: Challenge,
    val status: ChallengeStatus,
    /** Days elapsed (including today), capped at the challenge length. */
    val daysIn: Int,
    /** 0..1 for the progress bar. */
    val fraction: Float,
    val detail: String,
)

/** Small, time-boxed spending challenges with plain progress. Pure. */
object Challenges {
    /** [spend] maps a day to (categoryId → positive spending that day). */
    fun evaluate(ch: Challenge, spend: Map<LocalDate, Map<Long, Long>>, today: LocalDate): ChallengeProgress {
        val last = minOf(today, ch.end)
        val daysIn = if (today.isBefore(ch.start)) 0 else (ChronoUnit.DAYS.between(ch.start, last).toInt() + 1).coerceAtMost(ch.days)
        val window = (0 until daysIn).map { ch.start.plusDays(it.toLong()) }
        val finished = today.isAfter(ch.end)
        return when (ch.type) {
            ChallengeType.NO_SPEND_CATEGORY -> {
                val firstSpend = window.firstOrNull { (spend[it]?.get(ch.categoryId) ?: 0L) > 0 }
                when {
                    firstSpend != null -> ChallengeProgress(ch, ChallengeStatus.MISSED, daysIn, daysIn.toFloat() / ch.days,
                        "Spent on ${firstSpend.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} — try again?")
                    finished -> ChallengeProgress(ch, ChallengeStatus.WON, ch.days, 1f, "${ch.days} days done!")
                    else -> ChallengeProgress(ch, ChallengeStatus.ON_TRACK, daysIn, daysIn.toFloat() / ch.days, "$daysIn of ${ch.days} days")
                }
            }
            ChallengeType.UNDER_AMOUNT -> {
                val spent = window.sumOf { spend[it]?.get(ch.categoryId) ?: 0L }
                val status = when {
                    spent > ch.amountMinor -> ChallengeStatus.MISSED
                    finished -> ChallengeStatus.WON
                    else -> ChallengeStatus.ON_TRACK
                }
                val left = ch.amountMinor - spent
                ChallengeProgress(ch, status, daysIn, if (ch.amountMinor <= 0) 1f else (spent.toFloat() / ch.amountMinor).coerceIn(0f, 1f),
                    if (left >= 0) "${com.emotionalleaves.sprout.domain.money.Money.format(left)} left · day $daysIn of ${ch.days}"
                    else "${com.emotionalleaves.sprout.domain.money.Money.format(-left)} over")
            }
            ChallengeType.NO_SPEND_DAYS -> {
                val done = window.count { d -> (spend[d]?.values?.sum() ?: 0L) == 0L && d.isBefore(today) }
                val remaining = ch.days - daysIn + 1
                val status = when {
                    done >= ch.goal -> ChallengeStatus.WON
                    finished || done + remaining < ch.goal -> ChallengeStatus.MISSED
                    else -> ChallengeStatus.ON_TRACK
                }
                ChallengeProgress(ch, status, daysIn, (done.toFloat() / ch.goal.coerceAtLeast(1)).coerceIn(0f, 1f), "$done of ${ch.goal} no-spend days")
            }
        }
    }
}

/**
 * A simple money-health score out of 100 from four parts (25 each): savings rate, emergency fund, Age of Money and
 * budget pace. Missing parts are left out and the score is scaled to what's known. Each part comes with a tip.
 */
object HealthScore {
    data class Part(val name: String, val points: Int, val max: Int, val tip: String)
    data class Result(val score: Int?, val parts: List<Part>)

    fun compute(savingsRatePct: Int?, emergencyMonths: Double?, ageOfMoneyDays: Int?, budgetOnPace: Boolean?): Result {
        val parts = buildList {
            savingsRatePct?.let { r ->
                add(Part("Savings rate", (r.coerceIn(0, 20) * 25 / 20), 25,
                    if (r >= 20) "Keeping $r% of income. Great." else "Keeping $r% of income; 20% is a strong target."))
            }
            emergencyMonths?.let { m ->
                add(Part("Emergency fund", ((m.coerceIn(0.0, 3.0) / 3.0) * 25).toInt(), 25,
                    if (m >= 3) "%.1f months of spending in cash. Solid.".format(m) else "%.1f months of spending in cash; aim for 3.".format(m)))
            }
            ageOfMoneyDays?.let { d ->
                add(Part("Age of money", (d.coerceIn(0, 30) * 25 / 30), 25,
                    if (d >= 30) "Your money is $d days old. Real breathing room." else "$d days; 30+ means this month runs on last month's pay."))
            }
            budgetOnPace?.let { ok ->
                add(Part("Budget pace", if (ok) 25 else 10, 25, if (ok) "On pace to stay within budget." else "On pace to go over budget this month."))
            }
        }
        if (parts.isEmpty()) return Result(null, parts)
        val max = parts.sumOf { it.max }
        return Result(parts.sumOf { it.points } * 100 / max, parts)
    }
}
