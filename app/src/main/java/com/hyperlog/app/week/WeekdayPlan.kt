package com.hyperlog.app.week

import androidx.room.withTransaction
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.WeekdayPlanEntity
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/**
 * The weekday plan (SPEC, Training days): the weekdays you train on, each with an optional gym
 * (null: none named) and the [minutes] you have (none: no limit). It is yours, kept once for the
 * app, not per gym (ADR 0004). Empty: no plan.
 */
data class WeekdayPlan(val gyms: Map<DayOfWeek, Long?> = emptyMap(), val minutes: Map<DayOfWeek, Int> = emptyMap()) {
    /** The weekdays you train on. */
    val days: Set<DayOfWeek> get() = gyms.keys

    companion object {
        val None = WeekdayPlan()

        /** The minutes a training day may have: 30 to 120 in steps of 5. */
        val Minutes: List<Int> = (30..120 step 5).toList()
    }
}

/** Where the [WeekdayPlan] is kept. */
class WeekdayPlans(private val db: HyperlogDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.weekdayPlan()

    val plan: Flow<WeekdayPlan> = dao.observe().map { rows -> rows.toPlan() }

    /** The plan as it is now. */
    suspend fun current(): WeekdayPlan = dao.all().toPlan()

    /**
     * Replaces the plan with [plan]. The program running keeps its place: its rotation is set again
     * from today on the new days, or it runs as a sequence with none (ADR 0006).
     */
    suspend fun set(plan: WeekdayPlan) = db.withTransaction {
        val before = current().days
        dao.replace(plan.gyms.entries.sortedBy { it.key }.map { (day, gym) -> WeekdayPlanEntity(day.value, gym, plan.minutes[day]) })
        if (plan.days != before) Programs(db, clock).weekdayPlanChanged(before, plan.days)
    }

    /** A gym deleted or archived: its days stay training days, naming no gym. */
    suspend fun forgetGym(gymId: Long) = dao.forgetGym(gymId)
}

private fun List<WeekdayPlanEntity>.toPlan() = WeekdayPlan(
    gyms = associate { DayOfWeek.of(it.weekday) to it.gymId },
    minutes = filter { it.minutes != null }.associate { DayOfWeek.of(it.weekday) to it.minutes!! },
)
