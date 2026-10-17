package com.hyperlog.app.week

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.WeekdayPlanEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/**
 * The weekday plan (SPEC, Training days): the weekdays you train on, each with an optional gym
 * (null: none named). It is yours, kept once for the app, not per gym (ADR 0004). Empty: no plan.
 */
data class WeekdayPlan(val gyms: Map<DayOfWeek, Long?> = emptyMap()) {
    /** The weekdays you train on. */
    val days: Set<DayOfWeek> get() = gyms.keys

    companion object {
        val None = WeekdayPlan()

        /**
         * The plan from before it was yours, when each gym had its own weekdays (database v22): every
         * gym's days, each with that gym, the first gym in [gyms] keeping a day two share. [gyms] are
         * the gyms not deleted, in the gym list's order, each with its days as bits (bit 0 Monday).
         */
        fun fromGymWeekdays(gyms: List<Pair<Long, Int>>): WeekdayPlan = WeekdayPlan(
            DayOfWeek.entries.mapNotNull { day ->
                gyms.firstOrNull { (_, bits) -> bits and (1 shl (day.value - 1)) != 0 }?.let { day to it.first }
            }.toMap(),
        )
    }
}

/** Where the [WeekdayPlan] is kept. */
class WeekdayPlans(db: HyperlogDatabase) {
    private val dao = db.weekdayPlan()

    val plan: Flow<WeekdayPlan> = dao.observe().map { rows -> rows.toPlan() }

    /** Replaces the plan with [plan]. */
    suspend fun set(plan: WeekdayPlan) =
        dao.replace(plan.gyms.entries.sortedBy { it.key }.map { (day, gym) -> WeekdayPlanEntity(day.value, gym) })
}

internal fun List<WeekdayPlanEntity>.toPlan() = WeekdayPlan(associate { DayOfWeek.of(it.weekday) to it.gymId })
