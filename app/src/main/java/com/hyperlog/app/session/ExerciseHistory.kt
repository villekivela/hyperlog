package com.hyperlog.app.session

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.SetPrescriptionJson
import com.hyperlog.app.progression.Performance

/** One past session of an exercise, as progression reads it. */
internal data class PastPerformance(
    val sessionId: Long,
    /** The session exercise the sets were logged on. */
    val sessionExerciseId: Long,
    /** When the session started. */
    val startedAt: Long,
    /** The sets as logged, drops, partials and all; [performance] holds the main set of each. */
    val logged: List<LoggedSet>,
    val performance: Performance,
)

/**
 * An exercise's past sessions as progression reads them (GLOSSARY, Exercise history). Suggestions,
 * records and trends all read it, so they learn the same constants from the same sessions.
 */
internal class ExerciseHistory(db: HyperlogDatabase, private val programs: Programs) {
    private val dao = db.sessions()
    private val stored = StoredSessions(db, Gyms(db), programs)
    private val bodyweights = Bodyweights(db)

    /**
     * [exercise]'s finished sessions, oldest first: on [itemId] for machines and cables (ADR 0002), on
     * any for the rest. Only those before session [before] when given, and only the latest [last]
     * when given. A session is a deload as of when it finished, never as the program is now; a
     * weighted bodyweight exercise carries the bodyweight of its day; the sets are the main set only.
     */
    suspend fun of(exercise: Exercise, itemId: Long?, before: SessionEntity? = null, last: Int = Int.MAX_VALUE): List<PastPerformance> {
        val perMachine = exercise.equipment.perMachine
        val bodyweight = if (exercise.equipment == Equipment.WeightedBodyweight) bodyweights.byDate() else { _ -> null }
        return dao.history(exercise.id, anyItem = !perMachine, itemId = itemId, before = before?.startedAt, beforeId = before?.id, limit = last).reversed().map { past ->
            val day = when {
                past.workoutId != null -> programs.workoutDay(past.workoutId)
                past.programId == null || past.week == null || past.dayIndex == null -> null
                else -> programs.plannedDay(past.programId, Position(past.week, past.dayIndex), programs.dateOf(past.startedAt), asOf = past.finishedAt)
            }
            val plan = past.sets?.let(SetPrescriptionJson::decode) ?: day?.slot(past.slotId)?.setsFor(exercise.id)
            val rows = dao.sets(past.id)
            val logged = stored.sets(rows)
            PastPerformance(
                sessionId = past.sessionId,
                sessionExerciseId = past.id,
                startedAt = past.startedAt,
                logged = logged,
                performance = Performance(
                    workSets(logged, plan, rows.map { it.position }),
                    deload = day?.deload == true,
                    bodyweightKg = bodyweight(programs.dateOf(past.startedAt)) ?: 0.0,
                    slot = past.slotId,
                    dumbbells = exercise.implements,
                ),
            )
        }
    }
}
