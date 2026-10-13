package com.hyperlog.app.session

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.SessionLadder
import com.hyperlog.app.gym.SharedLadders
import com.hyperlog.app.gym.ladderFor
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.SetPrescriptionJson
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.TechniqueJson

/**
 * Reads a stored session back the way both the running session and a past one need it: logged sets
 * with their drops, mini-sets and hold, and each exercise with its machine, ladder, slot and
 * prescription. The running session ([SessionLogger]) and corrections after the fact
 * ([PastSessionEditor]) share it, so they read a session alike.
 */
internal class StoredSessions(private val db: HyperlogDatabase, private val gyms: Gyms, private val programs: Programs) {
    private val dao = db.sessions()

    /** One logged set with its drops, mini-sets or hold. */
    suspend fun set(row: LoggedSetEntity): LoggedSet = row.toLoggedSet(dao.extensions(row.id).map { it.toExtension() })

    /** [rows] as logged sets. */
    suspend fun sets(rows: List<LoggedSetEntity>): List<LoggedSet> = rows.map { set(it) }

    /** The sets logged on session exercise [sessionExerciseId], in order. */
    suspend fun sets(sessionExerciseId: Long): List<LoggedSet> = sets(dao.sets(sessionExerciseId))

    /** The session's techniques changed for this session, by set index (null: taken off). */
    suspend fun techniques(sessionExerciseId: Long): Map<Int, Technique?> =
        dao.techniques(sessionExerciseId).associate { it.setIndex to it.technique?.let(TechniqueJson::decode) }

    /** What [row]'s exercises are read against: the library, the shared ladders, its gym and its program day. */
    suspend fun context(row: SessionEntity): StoredSession = StoredSession(
        library = db.exercises().all().associate { it.id to it.toExercise() },
        shared = gyms.shared(),
        gym = row.gymId?.let { gyms.gym(it) },
        day = programs.plannedDay(row),
    )
}

/** A stored session's surroundings, from [StoredSessions.context]. */
internal data class StoredSession(
    val library: Map<String, Exercise>,
    val shared: SharedLadders,
    val gym: Gym?,
    val day: PlannedDay?,
) {
    /** [se] as stored: its exercise, its machine (machines and cables only, ADR 0002), ladder, slot and prescription. */
    fun exercise(se: SessionExerciseEntity): StoredExercise {
        val exercise = library.getValue(se.exerciseId)
        val machine = if (exercise.equipment.perMachine) gym?.items?.firstOrNull { it.id == se.equipmentItemId } else null
        val slot = day?.slot(se.slotId)
        return StoredExercise(
            exercise = exercise,
            machine = machine,
            ladder = shared.ladderFor(exercise.equipment, machine),
            slot = slot,
            prescription = se.sets?.let(SetPrescriptionJson::decode) ?: slot?.setsFor(se.exerciseId),
        )
    }
}

/** One exercise of a stored session, as [StoredSession.exercise] reads it. */
internal data class StoredExercise(
    val exercise: Exercise,
    val machine: EquipmentItem?,
    val ladder: SessionLadder,
    val slot: PlannedSlot?,
    /** Its set prescriptions: the session's edits, else the program's; null when added ad hoc. */
    val prescription: List<SetPrescription>?,
) {
    val perMachine: Boolean get() = exercise.equipment.perMachine
}
