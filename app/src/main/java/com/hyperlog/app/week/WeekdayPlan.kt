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
    }
}

/** Where the [WeekdayPlan] is kept. */
class WeekdayPlans(db: HyperlogDatabase) {
    private val dao = db.weekdayPlan()

    val plan: Flow<WeekdayPlan> = dao.observe().map { rows -> rows.toPlan() }

    /** The plan as it is now. */
    suspend fun current(): WeekdayPlan = dao.all().toPlan()

    /** Replaces the plan with [plan]. */
    suspend fun set(plan: WeekdayPlan) =
        dao.replace(plan.gyms.entries.sortedBy { it.key }.map { (day, gym) -> WeekdayPlanEntity(day.value, gym) })

    /** A gym deleted or archived: its days stay training days, naming no gym. */
    suspend fun forgetGym(gymId: Long) = dao.forgetGym(gymId)
}

private fun List<WeekdayPlanEntity>.toPlan() = WeekdayPlan(associate { DayOfWeek.of(it.weekday) to it.gymId })
