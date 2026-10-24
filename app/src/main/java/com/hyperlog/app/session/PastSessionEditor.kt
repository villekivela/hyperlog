package com.hyperlog.app.session

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A finished session as a list shows it: when, which day and gym, and what was logged. */
data class PastSessionItem(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    /** The program day's name; null for an ad-hoc session. */
    val title: String?,
    val gym: String?,
    val exercises: List<ExerciseSummary>,
    /** The program whose day it trained, and that day; null for an ad-hoc session or a workout. */
    val programId: Long? = null,
    val position: Position? = null,
)

/** A finished session opened to look at or correct. */
data class PastSession(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val title: String?,
    val gym: String?,
    /** Its exercises with something logged, in session order. */
    val exercises: List<SessionExercise>,
    /** The exercise whose table is open; null on the summary. */
    val shownId: Long? = null,
    /** The logged row loaded into the pad to be corrected; null when none. */
    val correction: Correction? = null,
) {
    val shown: SessionExercise? get() = exercises.firstOrNull { it.id == shownId }

    val summary: SessionSummary
        get() = SessionSummary(id, startedAt, finishedAt, exercises.map { it.summary })
}

/**
 * Opens finished sessions and corrects them (SPEC, Past sessions are editable). The session stays
 * a record: only the values of its logged sets, drops, mini-sets and holds change, never its
 * exercises or plan. Progression reads the corrected rows from then on; [onChanged] tells the
 * running session, whose suggestion may rest on them.
 */
class PastSessionEditor(
    private val db: HyperlogDatabase,
    private val programs: Programs,
    private val onChanged: suspend () -> Unit = {},
) {
    private val dao = db.sessions()
    private val gyms = Gyms(db)
    private val corrections = SetCorrections(db)
    private val stored = StoredSessions(db, gyms, programs)
    private val records = SessionRecords(db, programs)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<PastSession?>(null)

    /** The session open, or null. */
    val state: StateFlow<PastSession?> = _state.asStateFlow()

    /** Every finished session, newest first. */
    suspend fun sessions(): List<PastSessionItem> {
        val library = db.exercises().all().associate { it.id to it.name }
        return dao.finishedSessions().map { row -> item(row, library) }
    }

    private suspend fun item(row: SessionEntity, library: Map<String, String>): PastSessionItem {
        val exercises = dao.sessionExercises(row.id).filter { !it.removed }.mapNotNull { se ->
            val sets = stored.sets(se.id)
            if (sets.isEmpty()) null else ExerciseSummary(library[se.exerciseId] ?: se.exerciseId, sets)
        }
        return PastSessionItem(row.id, row.startedAt, row.finishedAt ?: row.startedAt, title(row), row.gymId?.let { gyms.gym(it)?.name }, exercises,
            programId = row.programId.takeIf { row.workoutId == null },
            position = if (row.workoutId == null && row.week != null && row.dayIndex != null) Position(row.week, row.dayIndex) else null,
        )
    }

    /** Opens finished session [sessionId] on its summary. */
    suspend fun open(sessionId: Long) = locked {
        _state.value = null
        load(sessionId, shownId = null)
    }

    /** Opens the table of [sessionExerciseId], or with null goes back to the summary. A correction under way is dropped. */
    suspend fun show(sessionExerciseId: Long?) = locked {
        val session = _state.value ?: return@locked
        _state.value = session.copy(shownId = sessionExerciseId?.takeIf { id -> session.exercises.any { it.id == id } }, correction = null)
    }

    fun close() {
        _state.value = null
    }

    /** Loads set [set] of [sessionExerciseId], or its drop, mini-set or hold [extension], into the pad. */
    suspend fun correct(sessionExerciseId: Long, set: Int, extension: Int? = null) = locked {
        val session = _state.value ?: return@locked
        val correction = corrections.load(sessionExerciseId, set, extension) ?: return@locked
        _state.value = session.copy(correction = correction)
    }

    suspend fun cancelCorrection() = locked {
        _state.value = _state.value?.copy(correction = null)
    }

    suspend fun press(key: PadKey) = edit { entry, ladder -> entry.press(key, ladder) }
    suspend fun selectRir(value: Int) = edit { entry, _ -> entry.selectRir(value) }
    suspend fun partialMode(on: Boolean) = edit { entry, _ -> entry.partialMode(on) }
    suspend fun pickWeight(kg: Double) = edit { entry, _ -> entry.copy(weight = formatKg(kg), fresh = true) }

    suspend fun focus(field: Field) = locked {
        val session = _state.value ?: return@locked
        val correction = session.correction ?: return@locked
        if (field == Field.Weight && correction.weightLocked) return@locked
        _state.value = session.copy(correction = correction.copy(entry = correction.entry.focus(field)))
    }

    /** Splits a unilateral set into left and right, or back to one number. */
    suspend fun split(on: Boolean) = locked {
        val session = _state.value ?: return@locked
        val correction = session.correction ?: return@locked
        val exercise = session.exercises.firstOrNull { it.id == correction.sessionExerciseId } ?: return@locked
        if (!exercise.exercise.unilateral || correction.kind != null) return@locked
        _state.value = session.copy(correction = correction.copy(entry = correction.entry.split(on)))
    }

    /** ✓: moves to the next value, or saves the correction. */
    suspend fun done() {
        val saved = mutex.withLock {
            val session = _state.value ?: return
            val correction = session.correction ?: return
            when (val step = correction.done()) {
                is Correction.Step.Moved -> {
                    _state.value = session.copy(correction = step.correction)
                    false
                }
                is Correction.Step.SaveSet -> {
                    corrections.saveSet(correction, step.set)
                    load(session.id, session.shownId)
                    true
                }
                is Correction.Step.SaveExtension -> {
                    corrections.saveExtension(correction, step.extension)
                    load(session.id, session.shownId)
                    true
                }
                Correction.Step.Nothing -> false
            }
        }
        if (saved) onChanged()
    }

    private suspend fun edit(change: (SetEntry, WeightLadder) -> SetEntry) = locked {
        val session = _state.value ?: return@locked
        val correction = session.correction ?: return@locked
        val ladder = session.exercises.firstOrNull { it.id == correction.sessionExerciseId }?.ladder ?: return@locked
        _state.value = session.copy(correction = correction.copy(entry = change(correction.entry, ladder)))
    }

    private suspend fun title(row: SessionEntity): String? {
        return programs.plannedDay(row)?.day?.name
    }

    /** Reads session [sessionId] as it is stored: each exercise with its sets, plan and last time before it. */
    private suspend fun load(sessionId: Long, shownId: Long?) {
        val row = dao.session(sessionId)?.takeIf { it.finishedAt != null } ?: run {
            _state.value = null
            return
        }
        val context = stored.context(row)
        val gym = context.gym
        val day = context.day
        val exercises = dao.sessionExercises(row.id).filter { !it.removed }.mapNotNull { se ->
            val rows = dao.sets(se.id)
            val sets = stored.sets(rows)
            if (sets.isEmpty()) return@mapNotNull null
            val (exercise, machine, ladder, slot, plan) = context.exercise(se)
            // Last time as it was then: the session before this one (on the same machine for machines, ADR 0002).
            val lastRows = dao.lastTimeBefore(se.exerciseId, row.id, row.startedAt, anyItem = !exercise.equipment.perMachine, itemId = se.equipmentItemId)
            val lastTime = stored.sets(lastRows)
            SessionExercise(
                id = se.id,
                exercise = exercise,
                sets = sets,
                lastTime = lastTime,
                ladder = ladder.ladder,
                ladderKnown = ladder.known,
                machine = machine,
                slot = slot,
                prescription = plan,
                techniques = stored.techniques(se.id),
                records = records.of(row, se, exercise, sets, deload = day?.deload == true),
                setIndices = rows.map { it.position },
                lastTimeIndices = lastRows.map { it.position },
            )
        }
        _state.value = PastSession(
            id = row.id,
            startedAt = row.startedAt,
            finishedAt = row.finishedAt!!,
            title = day?.day?.name,
            gym = gym?.name,
            exercises = exercises,
            shownId = shownId?.takeIf { id -> exercises.any { it.id == id } },
        )
    }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }
}
