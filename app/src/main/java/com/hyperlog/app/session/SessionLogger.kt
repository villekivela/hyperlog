package com.hyperlog.app.session

import androidx.room.withTransaction
import com.hyperlog.app.data.EntryColumns
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.Reference
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.ladderFor
import com.hyperlog.app.library.toExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the session being logged. Every call is saved before it returns, so the session survives
 * back gestures, app kills and reboots; [load] picks it up again exactly as it was.
 *
 * Calls run one at a time in the order they were made, so taps can be fired from the UI without
 * waiting for each other.
 */
class SessionLogger(private val db: HyperlogDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.sessions()
    private val gyms = Gyms(db)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ActiveSession?>(null)
    /** When ✓ last logged a set, to swallow a stutter tap right after it. */
    private var lastLoggedAt: Long? = null
    /** The entry a weight check was raised for, and when: a second ✓ on it keeps the weight. */
    private var checked: Pair<SetEntry, Long>? = null

    /** The running session, or null when there is none. */
    val state: StateFlow<ActiveSession?> = _state.asStateFlow()

    /** Picks up the running session. The exercise library must be synced first. */
    suspend fun load() = locked { reload() }

    /** Starts an ad-hoc session, or does nothing if one is running. */
    suspend fun start() = locked {
        if (_state.value == null) {
            dao.insertSession(SessionEntity(startedAt = clock()))
            reload()
        }
    }

    /** Adds [exerciseId] after the others and makes it the one on screen. */
    suspend fun addExercise(exerciseId: String) = locked {
        val session = _state.value ?: return@locked
        db.withTransaction {
            val id = dao.insertSessionExercise(
                SessionExerciseEntity(sessionId = session.id, exerciseId = exerciseId, position = session.exercises.size),
            )
            val row = dao.session(session.id)!!
            dao.updateSession(row.copy(currentSessionExerciseId = id, entry = null))
        }
        reload()
    }

    /** Puts another exercise of the session on screen. Rest keeps running. */
    suspend fun switchTo(sessionExerciseId: Long) = locked {
        val session = _state.value ?: return@locked
        if (session.currentId == sessionExerciseId) return@locked
        dao.updateSession(dao.session(session.id)!!.copy(currentSessionExerciseId = sessionExerciseId, entry = null))
        reload()
    }

    suspend fun press(key: PadKey) = editEntry { entry, ladder -> entry.press(key, ladder) }
    suspend fun selectRir(value: Int) = editEntry { entry, _ -> entry.selectRir(value) }
    suspend fun focus(field: Field) = editEntry { entry, _ -> entry.focus(field) }
    suspend fun partialMode(on: Boolean) = editEntry { entry, _ -> entry.partialMode(on) }

    /**
     * A rung picked from the pad's weight strip, or from a weight check's offers. The field stays,
     * so after a check the next ✓ logs the set with the picked weight.
     */
    suspend fun pickWeight(kg: Double) = editEntry { entry, _ -> entry.copy(weight = formatKg(kg), fresh = true) }

    /**
     * ✓: moves from the weight to reps, or logs the set and starts rest. A ✓ within
     * [DOUBLE_TAP_MS] of logging a set logs nothing: one press logs at most one set.
     */
    suspend fun done() = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        val entry = session.entry ?: return@locked
        when (val result = entry.done()) {
            is SetEntry.Done.Moved -> saveEntry(session, result.entry)
            is SetEntry.Done.Log -> {
                val now = clock()
                if (lastLoggedAt?.let { now - it < DOUBLE_TAP_MS } == true) return@locked
                val check = WeightCheck.of(result.set.weightKg, current.reference(), current.ladder, current.ladderKnown)
                val raised = checked
                if (check != null) {
                    if (raised == null || raised.first != entry) {
                        checked = entry to now
                        _state.value = session.copy(weightCheck = check)
                        return@locked
                    }
                    // The same stutter rule as logging: a ✓ right after the warning isn't a decision.
                    if (now - raised.second < DOUBLE_TAP_MS) return@locked
                }
                checked = null
                lastLoggedAt = now
                db.withTransaction {
                    dao.insertSet(result.set.toEntity(current.id, current.sets.size, now))
                    // The next set's entry is worked out again from what was just logged.
                    dao.updateSession(
                        dao.session(session.id)!!.copy(entry = null, restEndsAt = now + DEFAULT_REST_MS, restTotalMs = DEFAULT_REST_MS),
                    )
                }
                reload()
            }
            SetEntry.Done.Nothing -> Unit
        }
    }

    suspend fun setPadVisible(visible: Boolean) = locked {
        val session = _state.value ?: return@locked
        _state.value = session.copy(padVisible = visible)
        dao.updateSession(dao.session(session.id)!!.copy(padVisible = visible))
    }

    /** −15 / +15 on the rest sheet. Adding time also lengthens the pill's drain. */
    suspend fun adjustRest(deltaMs: Long) = locked {
        val session = _state.value ?: return@locked
        val ends = session.restEndsAt ?: return@locked
        val total = if (deltaMs > 0) session.restTotalMs + deltaMs else session.restTotalMs
        _state.value = session.copy(restEndsAt = ends + deltaMs, restTotalMs = total)
        dao.updateSession(dao.session(session.id)!!.copy(restEndsAt = ends + deltaMs, restTotalMs = total))
    }

    suspend fun endRest() = locked {
        val session = _state.value ?: return@locked
        _state.value = session.copy(restEndsAt = null)
        dao.updateSession(dao.session(session.id)!!.copy(restEndsAt = null))
    }

    /**
     * Ends the session and returns its summary. A session with nothing logged is discarded and
     * returns null.
     */
    suspend fun finish(): SessionSummary? = locked {
        val session = _state.value ?: return@locked null
        val logged = session.exercises.filter { it.sets.isNotEmpty() }
        val now = clock()
        if (logged.isEmpty()) {
            dao.deleteSession(session.id)
            _state.value = null
            return@locked null
        }
        dao.updateSession(dao.session(session.id)!!.copy(finishedAt = now, entry = null, restEndsAt = null))
        _state.value = null
        SessionSummary(session.id, session.startedAt, now, logged.map { ExerciseSummary(it.exercise.name, it.sets) })
    }

    /**
     * Runs a finished session again, as it was when it finished, so finishing by mistake costs
     * nothing. Does nothing while another session runs.
     */
    suspend fun resume(sessionId: Long) = locked {
        if (_state.value != null) return@locked
        val row = dao.session(sessionId) ?: return@locked
        dao.updateSession(row.copy(finishedAt = null))
        reload()
    }

    private suspend fun editEntry(change: (SetEntry, WeightLadder) -> SetEntry) = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        val entry = session.entry ?: return@locked
        val next = change(entry, current.ladder)
        if (next != entry) saveEntry(session, next)
    }

    private suspend fun saveEntry(session: ActiveSession, entry: SetEntry) {
        // The screen shows the change at once; the write follows before the next call runs.
        checked = null
        _state.value = session.copy(entry = entry, weightCheck = null)
        dao.updateSession(dao.session(session.id)!!.copy(entry = entry.toColumns()))
    }

    /** Reads the running session back, filling in the next set's entry if it has none. */
    private suspend fun reload() {
        val row = dao.runningSession()
        if (row == null) {
            _state.value = null
            return
        }
        val library = db.exercises().all().associate { it.id to it.toExercise() }
        val shared = gyms.shared()
        checked = null
        val exercises = dao.sessionExercises(row.id).map { se ->
            val exercise = library.getValue(se.exerciseId)
            val ladder = shared.ladderFor(exercise.equipment)
            SessionExercise(
                id = se.id,
                exercise = exercise,
                sets = dao.sets(se.id).map { it.toLoggedSet() },
                lastTime = dao.lastTime(se.exerciseId, row.id).map { it.toLoggedSet() },
                ladder = ladder.ladder,
                ladderKnown = ladder.known,
            )
        }
        val current = exercises.firstOrNull { it.id == row.currentSessionExerciseId }
        var entry = row.entry?.toEntry()
        if (entry == null && current != null) {
            entry = SetEntry.prefill(current.sets.lastOrNull() ?: current.lastTime.firstOrNull())
            dao.updateSession(row.copy(entry = entry.toColumns()))
        }
        _state.value = ActiveSession(
            id = row.id,
            startedAt = row.startedAt,
            exercises = exercises,
            currentId = current?.id,
            entry = if (current == null) null else entry,
            padVisible = row.padVisible,
            restEndsAt = row.restEndsAt,
            restTotalMs = row.restTotalMs,
        )
    }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }

    companion object {
        /** Rest after a set in an ad-hoc session, which has no prescription to take it from. */
        const val DEFAULT_REST_MS = 120_000L

        /** No two sets are logged this close together on purpose; a second ✓ is a stutter. */
        const val DOUBLE_TAP_MS = 1_000L
    }
}

/** What a new set's weight is checked against: the set before it, or last time's first set. */
private fun SessionExercise.reference(): Reference? =
    sets.lastOrNull()?.let { Reference(it.weightKg, fromThisSession = true) }
        ?: lastTime.firstOrNull()?.let { Reference(it.weightKg, fromThisSession = false) }

private fun LoggedSet.toEntity(sessionExerciseId: Long, position: Int, loggedAt: Long) =
    LoggedSetEntity(
        sessionExerciseId = sessionExerciseId, position = position, weightKg = weightKg,
        fullReps = fullReps, partialReps = partialReps, rir = rir, loggedAt = loggedAt,
    )

private fun LoggedSetEntity.toLoggedSet() = LoggedSet(weightKg, fullReps, partialReps, rir)

private fun SetEntry.toColumns() = EntryColumns(weight, full, partial, rir, field.name, partialMode, fresh)

private fun EntryColumns.toEntry() = SetEntry(weight, full, partial, rir, Field.valueOf(field), partialMode, fresh)
