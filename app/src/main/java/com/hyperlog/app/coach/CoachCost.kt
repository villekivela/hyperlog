package com.hyperlog.app.coach

import androidx.room.withTransaction
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * What check-ins cost this month (SPEC, Coaching → With a Claude key, Cost): each reply's usage
 * at its model's prices, added up by month in table `setting` as `coach_cost_<yyyy-MM>`, in
 * millionths of a dollar. It goes into backups with the other settings. The figure is the app's
 * own sum; the Anthropic Console has the bill and the limit.
 */
class CoachCost(private val db: HyperlogDatabase, private val month: () -> YearMonth = YearMonth::now) {
    /** This month's cost so far, in millionths of a dollar. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val thisMonth: Flow<Long> = flowOf(Unit).flatMapLatest {
        db.settings().observe(key(month())).map { it?.toLongOrNull() ?: 0 }
    }

    suspend fun add(usage: List<CoachUsage>) {
        val micros = usage.sumOf { price(it) }
        if (micros == 0L) return
        val key = key(month())
        db.withTransaction {
            val sum = (db.settings().get(key)?.toLongOrNull() ?: 0) + micros
            db.settings().put(SettingEntity(key, sum.toString()))
        }
    }

    /** A model's prices in dollars per million tokens, which makes tokens × price millionths of a dollar. */
    private class Prices(val input: Double, val cacheWrite: Double, val cacheRead: Double, val output: Double)

    private fun price(usage: CoachUsage): Long {
        val prices = PRICES[usage.model] ?: PRICES.getValue(ClaudeClient.MODEL)
        return Math.round(
            usage.input * prices.input + usage.cacheWrite * prices.cacheWrite +
                usage.cacheRead * prices.cacheRead + usage.output * prices.output,
        )
    }

    companion object {
        /**
         * Opus 5.5, and the models a refused reply may fall back to. Cache writes are the
         * five-minute kind, 1.25 × input. A model not listed is counted at Opus 5.5's prices.
         */
        private val PRICES = mapOf(
            ClaudeClient.MODEL to Prices(input = 4.0, cacheWrite = 5.0, cacheRead = 0.20, output = 20.0),
            "claude-opus-5" to Prices(input = 5.0, cacheWrite = 6.25, cacheRead = 0.50, output = 25.0),
            "claude-opus-4-8" to Prices(input = 5.0, cacheWrite = 6.25, cacheRead = 0.50, output = 25.0),
        )

        private fun key(month: YearMonth) = "coach_cost_$month"

        /** `≈ $2.40`. */
        fun dollars(micros: Long): String = "≈ $" + String.format(Locale.ROOT, "%.2f", micros / 1_000_000.0)
    }
}

/** A [CoachModel] that adds each reply's usage to [cost], a refused one's too. */
class MeteredCoach(private val model: CoachModel, private val cost: CoachCost) : CoachModel {
    override suspend fun reply(request: CoachRequest): CoachReply {
        val reply = try {
            model.reply(request)
        } catch (refused: CoachError.Refused) {
            cost.add(refused.usage)
            throw refused
        }
        cost.add(reply.usage)
        return reply
    }
}
