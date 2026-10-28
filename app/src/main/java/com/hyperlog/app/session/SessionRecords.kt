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
    private val exerciseHistory = ExerciseHistory(db, programs)

    /** The record each of [sets] (in set order) makes, logged on [se] of [row] in a [deload] week or not. */
    suspend fun of(row: SessionEntity, se: SessionExerciseEntity, exercise: Exercise, sets: List<LoggedSet>, deload: Boolean): List<Record?> {
        if (sets.isEmpty()) return emptyList()
        val history = exerciseHistory.of(exercise, se.equipmentItemId, before = row).map { it.performance }
        val bodyweight = if (exercise.equipment == Equipment.WeightedBodyweight) bodyweights.on(programs.dateOf(row.startedAt)) ?: 0.0 else 0.0
        // In the order they were logged, so a set logged out of order never loses its mark to one logged after it.
        val at = dao.sets(se.id).map { it.loggedAt }
        val order = sets.indices.sortedBy { at.getOrNull(it) ?: 0L }
        val marks = Records.of(history, Performance(workSets(order.map(sets::get), null), deload = deload, bodyweightKg = bodyweight, dumbbells = exercise.implements))
        return sets.indices.map { marks[order.indexOf(it)] }
    }
}
