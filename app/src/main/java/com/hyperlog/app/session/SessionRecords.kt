package com.hyperlog.app.session

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Performance
import com.hyperlog.app.progression.Record
import com.hyperlog.app.progression.Records

/**
 * The records a session's sets make (SPEC, Records), read from every earlier session of the
 * exercise (on the same machine for machines, ADR 0002) each time, so a correction moves them.
 * The running session and a past one read them alike.
 */
internal class SessionRecords(db: HyperlogDatabase, private val programs: Programs) {
    private val dao = db.sessions()
    private val bodyweights = Bodyweights(db)

    /** The record each of [sets] makes, logged on [se] of [row] in a [deload] week or not. */
    suspend fun of(row: SessionEntity, se: SessionExerciseEntity, exercise: Exercise, sets: List<LoggedSet>, deload: Boolean): List<Record?> {
        if (sets.isEmpty()) return emptyList()
        val perMachine = exercise.equipment.perMachine
        val earlier = dao.setsBefore(se.exerciseId, row.id, row.startedAt, anyItem = !perMachine, itemId = se.equipmentItemId)
        val weighted = exercise.equipment == Equipment.WeightedBodyweight
        val bodyweight = if (weighted) bodyweights.byDate() else { _ -> null }
        fun performance(sets: List<LoggedSet>, startedAt: Long, deload: Boolean) = Performance(
            workSets(sets, null),
            deload = deload,
            bodyweightKg = bodyweight(programs.dateOf(startedAt)) ?: 0.0,
            dumbbells = exercise.implements,
        )
        val history = earlier.groupBy { it.set.sessionExerciseId }.values.map { rows ->
            performance(rows.map { it.set.toLoggedSet(emptyList()) }, rows.first().startedAt, deload = false)
        }
        return Records.of(history, performance(sets, row.startedAt, deload))
    }
}
