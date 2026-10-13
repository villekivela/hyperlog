package com.hyperlog.app.history

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LiftGoalEntity
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A target [kg] for [exerciseId]'s estimate, optionally [by] a date. */
data class LiftGoal(val exerciseId: String, val kg: Double, val by: LocalDate?)

/**
 * Whether the exercise can have a lift goal: it needs one estimate in kg. A machine's estimate is
 * per machine (ADR 0002), and a plain bodyweight exercise tracks reps instead.
 */
val Exercise.takesGoal: Boolean get() = !equipment.perMachine && equipment != Equipment.Bodyweight

/**
 * Lift goals (SPEC, Lift goals): one an exercise. A goal is shown against the estimate on the
 * prescription sheet and in History; it never changes the program or the suggestions, so nothing
 * in progression or the session logger reads it.
 */
class LiftGoals(db: HyperlogDatabase) {
    private val dao = db.liftGoals()

    /** Every goal, by exercise id. */
    val all: Flow<List<LiftGoal>> = dao.observe().map { rows -> rows.map { it.toGoal() } }

    suspend fun of(exerciseId: String): LiftGoal? = dao.get(exerciseId)?.toGoal()

    /** Sets [exerciseId]'s goal to [kg], [by] a date or not, replacing any goal it had. */
    suspend fun set(exerciseId: String, kg: Double, by: LocalDate?) = dao.put(LiftGoalEntity(exerciseId, kg, by?.toEpochDay()))

    suspend fun clear(exerciseId: String) = dao.delete(exerciseId)

    private fun LiftGoalEntity.toGoal() = LiftGoal(exerciseId, kg, byDay?.let(LocalDate::ofEpochDay))
}
