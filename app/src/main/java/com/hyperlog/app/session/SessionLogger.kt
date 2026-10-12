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
import com.hyperlog.app.domain.counting
import com.hyperlog.app.domain.countingSets
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.progression.Performance
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.progression.WorkSet
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.ladderFor
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.legacy.StartingWeights
import com.hyperlog.app.legacy.text
import com.hyperlog.app.legacy.suggestion
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.plannedEntry
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
class SessionLogger(
    private val db: HyperlogDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val programs: Programs = Programs(db, clock),
) {
    private val dao = db.sessions()
    private val gyms = Gyms(db)
    private val startingWeights = StartingWeights(db)
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

    /** Starts an ad-hoc session at [gymId] (null with no gyms set up), or does nothing if one is running. */
    suspend fun start(gymId: Long? = null) = locked {
        if (_state.value == null) {
            dao.insertSession(SessionEntity(startedAt = clock(), gymId = gymId))
            reload()
        }
    }

    /**
     * Starts a session of [program]'s next day at [gymId]: its slots become the session's
     * exercises, in order, each with this week's sets. Does nothing if a session is running or the
     * program is finished.
     */
    suspend fun startDay(program: ActiveProgram, gymId: Long?) = locked {
        val day = program.next ?: return@locked
        if (_state.value != null) return@locked
        db.withTransaction {
            val sessionId = dao.insertSession(
                SessionEntity(
                    startedAt = clock(), gymId = gymId, programId = program.id,
                    week = day.position.week, dayIndex = day.position.day,
                ),
            )
            val ids = day.slots.mapIndexed { i, slot ->
                val id = dao.insertSessionExercise(
                    SessionExerciseEntity(sessionId = sessionId, exerciseId = slot.exercise, position = i, slotId = slot.id),
                )
                rememberMachine(id, gymId, slot.exercise)
                id
            }
            dao.updateSession(dao.session(sessionId)!!.copy(currentSessionExerciseId = ids.firstOrNull()))
        }
        reload()
    }

    /** A machine done at [gymId] before is on the same machine again; otherwise the screen asks. */
    private suspend fun rememberMachine(sessionExerciseId: Long, gymId: Long?, exerciseId: String) {
        val equipment = db.exercises().get(exerciseId)?.toExercise()?.equipment
        if (gymId != null && equipment?.perMachine == true) {
            gyms.remembered(gymId, exerciseId)?.let { dao.setEquipmentItem(sessionExerciseId, it) }
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
            rememberMachine(id, session.gym?.id, exerciseId)
        }
        reload()
    }

    /**
     * Answers "Which machine?" for the exercise on screen with one of the gym's equipment items,
     * and remembers it for the next time the exercise is done at this gym.
     */
    suspend fun chooseMachine(itemId: Long) = locked { setMachine(itemId) }

    /** Adds a machine with its ladder to the session's gym and does the exercise on screen on it. */
    suspend fun addMachine(name: String, ladder: LadderSpec) = locked {
        val gym = _state.value?.gym ?: return@locked
        setMachine(gyms.addItem(EquipmentItem(gymId = gym.id, name = name, ladder = ladder)))
    }

    /** Asks "Which machine?" again for the exercise on screen, as long as nothing is logged on it. */
    suspend fun changeMachine() = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        if (current.machine == null || current.sets.isNotEmpty()) return@locked
        db.withTransaction {
            dao.setEquipmentItem(current.id, null)
            dao.updateSession(dao.session(session.id)!!.copy(entry = null))
        }
        reload()
    }

    private suspend fun setMachine(itemId: Long) {
        val session = _state.value ?: return
        val current = session.current ?: return
        val gym = session.gym ?: return
        if (current.sets.isNotEmpty()) return
        db.withTransaction {
            dao.setEquipmentItem(current.id, itemId)
            gyms.remember(gym.id, current.exercise.id, itemId)
            // The first set is prefilled again, from this machine's own history.
            dao.updateSession(dao.session(session.id)!!.copy(entry = null))
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
                val set = result.set.copy(kept = check != null)
                val rest = current.plan?.getOrNull(current.sets.size)?.rest?.let { it * 1000L } ?: DEFAULT_REST_MS
                // With the exercise's planned sets done, the first exercise with sets left comes up.
                val finishesExercise = current.plan != null && current.sets.size + 1 >= current.plan.size
                val next = if (finishesExercise) session.exercises.firstOrNull { it.id != current.id && it.plan != null && !it.done } else null
                db.withTransaction {
                    dao.insertSet(set.toEntity(current.id, current.sets.size, now))
                    // The next set's entry is worked out again from what was just logged.
                    dao.updateSession(
                        dao.session(session.id)!!.copy(
                            entry = null, restEndsAt = now + rest, restTotalMs = rest,
                            currentSessionExerciseId = next?.id ?: current.id,
                        ),
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
        session.day?.let { day -> dao.session(session.id)?.programId?.let { programs.advance(it, day.position) } }
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
        val gym = row.gymId?.let { gyms.gym(it) }
        val day = row.programId?.let { id ->
            if (row.week == null || row.dayIndex == null) null else programs.program(id)?.plannedDay(Position(row.week, row.dayIndex))
        }
        checked = null
        val exercises = dao.sessionExercises(row.id).map { se ->
            val exercise = library.getValue(se.exerciseId)
            val perMachine = exercise.equipment.perMachine
            val machine = if (perMachine) gym?.items?.firstOrNull { it.id == se.equipmentItemId } else null
            val needsMachine = perMachine && gym != null && machine == null
            val ladder = shared.ladderFor(exercise.equipment, machine)
            // Machines and cables only compare with sets on the same machine (ADR 0002).
            val lastTime = when {
                needsMachine -> emptyList()
                perMachine -> dao.lastTimeOn(se.exerciseId, row.id, machine?.id)
                else -> dao.lastTime(se.exerciseId, row.id)
            }
            val slot = day?.slot(se.slotId)
            val plan = slot?.setsFor(se.exerciseId)
            val sets = dao.sets(se.id).map { it.toLoggedSet() }
            val onScreen = se.id == row.currentSessionExerciseId && !needsMachine
            // The old app's weight stands in until the exercise (on this machine) has its own history.
            val starting = if (onScreen && sets.isEmpty() && lastTime.isEmpty()) startingWeights.get(se.exerciseId) else null
            val start = starting?.takeIf { !perMachine }
            val suggestion = when {
                !onScreen -> null
                plan == null -> start?.suggestion(null, ladder.ladder)
                else -> Progression.suggest(
                    plan,
                    Performance(workSets(sets, plan), deload = day.deload),
                    history(se.exerciseId, row.id, perMachine, machine?.id),
                    ladder.ladder,
                ) ?: start?.takeIf { sets.isEmpty() }?.suggestion(plan.first(), ladder.ladder)
            }
            SessionExercise(
                id = se.id,
                exercise = exercise,
                sets = sets,
                lastTime = lastTime.map { it.toLoggedSet() },
                ladder = ladder.ladder,
                ladderKnown = ladder.known,
                machine = machine,
                needsMachine = needsMachine,
                slot = slot,
                plan = plan,
                suggestion = suggestion,
                startingHint = starting?.takeIf { perMachine }?.text(),
            )
        }
        val current = exercises.firstOrNull { it.id == row.currentSessionExerciseId }
        var entry = row.entry?.toEntry()
        if (current?.needsMachine == true || current?.done == true) entry = null
        if (entry == null && current != null && !current.needsMachine && !current.done) {
            val plan = current.plan
            entry = if (plan != null) plannedEntry(plan, current.sets, current.suggestion)
            else current.suggestion?.let { s ->
                SetEntry(formatKg(s.kg), s.reps.toString(), "", startingWeights.get(current.exercise.id)?.rir, Field.Reps, partialMode = false, fresh = true)
            } ?: SetEntry.prefill(current.sets.countingSets().lastOrNull() ?: current.lastTime.countingSets().firstOrNull())
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
            gym = gym,
            day = day,
        )
    }

    /**
     * The exercise's past sessions (on [itemId] for machines), oldest first, each with the
     * prescription it was done under and whether its week was a deload.
     */
    private suspend fun history(exerciseId: String, sessionId: Long, perMachine: Boolean, itemId: Long?): List<Performance> =
        dao.history(exerciseId, sessionId, anyItem = !perMachine, itemId = itemId, limit = HISTORY_SESSIONS).reversed().map { past ->
            val day = if (past.programId == null || past.week == null || past.dayIndex == null) null
            else programs.program(past.programId)?.plannedDay(Position(past.week, past.dayIndex))
            val plan = day?.slot(past.slotId)?.setsFor(exerciseId)
            Performance(workSets(dao.sets(past.id).map { it.toLoggedSet() }, plan), deload = day?.deload == true)
        }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }

    companion object {
        /** Rest after a set in an ad-hoc session, which has no prescription to take it from. */
        const val DEFAULT_REST_MS = 120_000L

        /** No two sets are logged this close together on purpose; a second ✓ is a stutter. */
        const val DOUBLE_TAP_MS = 1_000L

        /** How many past sessions of an exercise progression reads. */
        const val HISTORY_SESSIONS = 12
    }
}

/**
 * What a new set's weight is checked against: the last set that counts, or last time's first. A
 * weight kept past a check doesn't count until a set is logged near it.
 */
private fun SessionExercise.reference(): Reference? =
    sets.countingSets().lastOrNull()?.let { Reference(it.weightKg, fromThisSession = true) }
        ?: lastTime.countingSets().firstOrNull()?.let { Reference(it.weightKg, fromThisSession = false) }

/** [sets] as progression reads them: with their prescriptions, and whether each counts. */
private fun workSets(sets: List<LoggedSet>, plan: List<PlannedSet>?): List<WorkSet> {
    val counting = sets.counting()
    return sets.mapIndexed { i, set -> WorkSet(set, plan?.getOrNull(i), counting[i]) }
}

private fun LoggedSet.toEntity(sessionExerciseId: Long, position: Int, loggedAt: Long) =
    LoggedSetEntity(
        sessionExerciseId = sessionExerciseId, position = position, weightKg = weightKg,
        fullReps = fullReps, partialReps = partialReps, rir = rir, loggedAt = loggedAt, kept = kept,
    )

private fun LoggedSetEntity.toLoggedSet() = LoggedSet(weightKg, fullReps, partialReps, rir, kept)

private fun SetEntry.toColumns() = EntryColumns(weight, full, partial, rir, field.name, partialMode, fresh)

private fun EntryColumns.toEntry() = SetEntry(weight, full, partial, rir, Field.valueOf(field), partialMode, fresh)
