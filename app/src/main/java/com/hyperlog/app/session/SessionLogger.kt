package com.hyperlog.app.session

import androidx.room.withTransaction
import com.hyperlog.app.coach.RuleCoach
import com.hyperlog.app.data.EntryColumns
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.data.SetExtensionEntity
import com.hyperlog.app.data.SetTechniqueEntity
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.Reference
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.counting
import com.hyperlog.app.domain.countingSets
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.LadderState
import com.hyperlog.app.domain.StackReading
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.legacy.StartingWeights
import com.hyperlog.app.legacy.suggestion
import com.hyperlog.app.legacy.text
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.SetPrescriptionJson
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.TechniqueJson
import com.hyperlog.app.program.backoffWeight
import com.hyperlog.app.program.plannedEntry
import com.hyperlog.app.progression.Performance
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.progression.WorkSet
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
    /**
     * The progression engine. Everything a suggestion depends on goes in through its arguments,
     * which is how a test shows that a lift goal never reaches it.
     */
    private val suggest: (plan: List<SetPrescription>, today: Performance, history: List<Performance>, ladder: WeightLadder) -> Suggestion? = Progression::suggest,
) {
    private val dao = db.sessions()
    private val gyms = Gyms(db)
    private val startingWeights = StartingWeights(db)
    private val bodyweights = Bodyweights(db)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ActiveSession?>(null)
    /** When ✓ last logged a set, to swallow a stutter tap right after it. */
    private var lastLoggedAt: Long? = null
    /** The entry a weight check was raised for, and when: a second ✓ on it keeps the weight. */
    private var checked: Pair<SetEntry, Long>? = null
    private val corrections = SetCorrections(db)
    private val stored = StoredSessions(db, gyms, programs)
    /** The row being corrected and the finished exercise looked at: screen state, kept in memory only. */
    private var correction: Correction? = null
    private var viewingId: Long? = null
    /** The set logged last, by session exercise and index, for the one-tap fix; null when none. */
    private var lastLogged: Pair<Long, Int>? = null
    /** The correction on screen came from [fixLast]: saving or cancelling it goes back to the set to do. */
    private var fixingLast = false

    /** The running session, or null when there is none. */
    val state: StateFlow<ActiveSession?> = _state.asStateFlow()

    /** Picks up the running session. The exercise library must be synced first. */
    suspend fun load() = locked { reload() }

    /**
     * Picks up the session of a restored backup: what was in memory (a correction, a weight check,
     * the exercise looked at) belonged to the data that was replaced.
     */
    suspend fun restored() = locked {
        lastLoggedAt = null
        checked = null
        correction = null
        viewingId = null
        reload()
    }

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
     * program is finished. [swaps] (slot id -> exercise) swap slots for this session only, such
     * as the missing-equipment swaps proposed when the gym was picked.
     */
    suspend fun startDay(program: ActiveProgram, gymId: Long?, swaps: Map<String, String> = emptyMap()) = locked {
        if (program.finished || _state.value != null) return@locked
        // The day as trained today, with the Changes in force for today's date.
        val day = programs.plannedDay(program.id, program.position, programs.dateOf(clock())) ?: return@locked
        db.withTransaction {
            val sessionId = dao.insertSession(
                SessionEntity(
                    startedAt = clock(), gymId = gymId, programId = program.id,
                    week = day.position.week, dayIndex = day.position.day,
                ),
            )
            val ids = day.slots.mapIndexed { i, slot ->
                val exerciseId = swaps[slot.id] ?: slot.exercise
                val id = dao.insertSessionExercise(
                    SessionExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, position = i, slotId = slot.id),
                )
                rememberMachine(id, gymId, exerciseId)
                id
            }
            dao.updateSession(dao.session(sessionId)!!.copy(currentSessionExerciseId = ids.firstOrNull()))
        }
        reload()
    }

    /**
     * A machine done at [gymId] before is on the same machine again; one named for the exercise,
     * or for a cable exercise the gym's cable station, is taken (and remembered) without asking;
     * otherwise the screen asks.
     */
    private suspend fun rememberMachine(sessionExerciseId: Long, gymId: Long?, exerciseId: String) {
        val exercise = db.exercises().get(exerciseId)?.toExercise() ?: return
        if (gymId == null || !exercise.equipment.perMachine) return
        val remembered = gyms.remembered(gymId, exerciseId)
            ?: gyms.gym(gymId)?.items?.let { items ->
                namedFor(exercise, items) ?: items.takeIf { exercise.equipment == Equipment.Cable }?.firstOrNull { it.cableStation && !it.archived }
            }?.id?.also { gyms.remember(gymId, exerciseId, it) }
        remembered?.let { dao.setEquipmentItem(sessionExerciseId, it) }
    }

    /** Adds [exerciseId] after the others and makes it the one on screen. */
    suspend fun addExercise(exerciseId: String) = locked {
        val session = _state.value ?: return@locked
        db.withTransaction {
            val id = dao.insertSessionExercise(
                // After every other, removed ones included, so positions never tie.
                SessionExerciseEntity(
                    sessionId = session.id, exerciseId = exerciseId,
                    position = (dao.sessionExercises(session.id).maxOfOrNull { it.position } ?: -1) + 1,
                ),
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
        // The first machine added for a cable exercise is the gym's cable station.
        val station = _state.value?.current?.exercise?.equipment == Equipment.Cable && gym.items.none { it.cableStation && !it.archived }
        setMachine(gyms.addItem(EquipmentItem(gymId = gym.id, name = name, ladder = ladder, cableStation = station)))
    }

    /**
     * Answers "Which machine?" with "Not at this gym": the gym remembers it has no equipment for
     * the exercise on screen, and the exercise is swapped as the coach would (the slot's
     * alternatives, then the movement pattern) or, with nothing that fits, left out of this session.
     */
    suspend fun notAtGym() {
        val session = _state.value ?: return
        val current = session.current ?: return
        val gym = session.gym ?: return
        if (current.sets.isNotEmpty()) return
        gyms.markMissing(gym.id, current.exercise.id)
        val missing = gyms.gym(gym.id)?.missing.orEmpty()
        val library = db.exercises().all().map { it.toExercise() }
        val replacement = RuleCoach.replacement(current.slot, current.exercise.id, library, missing)
        if (replacement != null) swap(current.id, replacement) else removeExercise(current.id)
    }

    /** Sets up the ladder of the machine on screen from its stack's labels: confirmed from now on. */
    suspend fun setUpLadder(spec: LadderSpec) = locked {
        val machine = _state.value?.current?.machine ?: return@locked
        gyms.setLadder(machine.id, spec)
        reload()
    }

    /** Leaves the machine on screen's guessed ladder for now: weights are typed, and taught by them. */
    suspend fun skipLadderSetup() = locked {
        val machine = _state.value?.current?.machine ?: return@locked
        if (machine.state == LadderState.Unconfirmed) gyms.setLadderState(machine.id, LadderState.Guess)
        reload()
    }

    /** Takes the stack offered for the machine on screen as its ladder. */
    suspend fun takeLadderOffer() = locked {
        val current = _state.value?.current ?: return@locked
        val offer = current.ladderOffer ?: return@locked
        gyms.setLadder(current.machine!!.id, offer)
        reload()
    }

    /** Declines the offer for the machine on screen: its weights stay as typed, and it isn't offered again. */
    suspend fun declineLadderOffer() = locked {
        val current = _state.value?.current ?: return@locked
        if (current.ladderOffer == null) return@locked
        gyms.setLadderState(current.machine!!.id, LadderState.Offered)
        reload()
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

    /**
     * Swaps session exercise [sessionExerciseId] to [exerciseId] (an alternative or any library
     * exercise) for this session only. It keeps its slot, so a superset keeps its partner, and
     * takes the sets the program gives the new exercise. Nothing changes once a set is logged on it.
     */
    suspend fun swap(sessionExerciseId: Long, exerciseId: String) = locked {
        val session = _state.value ?: return@locked
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@locked
        if (exercise.sets.isNotEmpty() || exercise.exercise.id == exerciseId) return@locked
        db.withTransaction {
            dao.setExercise(sessionExerciseId, exerciseId)
            // The session's changes to the sets were for the exercise swapped out.
            dao.clearTechniques(sessionExerciseId)
            rememberMachine(sessionExerciseId, session.gym?.id, exerciseId)
            if (session.currentId == sessionExerciseId) dao.updateSession(dao.session(session.id)!!.copy(entry = null))
        }
        reload()
    }

    /** Adds a set to [sessionExerciseId]'s planned sets for this session, like its last one but with no technique. */
    suspend fun addSet(sessionExerciseId: Long) = editPlan(sessionExerciseId) { exercise, plan ->
        // A back-off's copy backs off the same set.
        dao.clearTechnique(exercise.id, plan.size)
        plan + plan.last().copy(technique = null)
    }

    /**
     * Takes planned set [setIndex] out of this session. A logged set stays, one set always
     * stays, and a set that others back off from stays (as in a program file).
     */
    suspend fun removeSet(sessionExerciseId: Long, setIndex: Int) = editPlan(sessionExerciseId) { exercise, plan ->
        if (!exercise.removable(setIndex)) return@editPlan null
        dao.clearTechnique(exercise.id, setIndex)
        dao.shiftTechniques(exercise.id, setIndex)
        plan.filterIndexed { i, _ -> i != setIndex }.map { set ->
            set.backoff?.takeIf { it.from > setIndex }?.let { set.copy(backoff = it.copy(from = it.from - 1)) } ?: set
        }
    }

    /** Changes planned set [setIndex]'s target reps and RIR for this session, until it is logged. */
    suspend fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?) = editPlan(sessionExerciseId) { exercise, plan ->
        if (!exercise.editable(setIndex) || reps.min < 1 || reps.max < reps.min) return@editPlan null
        plan.mapIndexed { i, set -> if (i == setIndex) set.copy(reps = reps, rir = rir) else set }
    }

    /**
     * Writes [change]'s planned sets for [sessionExerciseId] as this session's own (null changes
     * nothing). The active set is prefilled again from its new target, and once the exercise
     * on screen has no sets left the session moves on, as after its last set.
     */
    private suspend fun editPlan(
        sessionExerciseId: Long,
        change: suspend (SessionExercise, List<SetPrescription>) -> List<SetPrescription>?,
    ) = locked {
        val session = _state.value ?: return@locked
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@locked
        val plan = exercise.prescription ?: return@locked
        val onScreen = session.currentId == sessionExerciseId && session.openSetId == null
        val edited = db.withTransaction {
            val sets = change(exercise, plan) ?: return@withTransaction false
            dao.setSets(sessionExerciseId, SetPrescriptionJson.encode(sets))
            if (onScreen) dao.updateSession(dao.session(session.id)!!.copy(entry = null))
            true
        }
        if (!edited) return@locked
        reload()
        val after = _state.value ?: return@locked
        val current = after.current ?: return@locked
        if (onScreen && current.done && current.sets.isNotEmpty()) {
            val step = SessionOrder.after(after.exercises, current.id, current.sets.size - 1)
            if (step.exerciseId != current.id) {
                dao.updateSession(dao.session(session.id)!!.copy(currentSessionExerciseId = step.exerciseId, entry = null))
                reload()
            }
        }
    }

    /**
     * Takes [sessionExerciseId] out of this session, as long as nothing is logged on it. The
     * program keeps it. If it was on screen, the next exercise with sets left comes up.
     */
    suspend fun removeExercise(sessionExerciseId: Long) = locked {
        val session = _state.value ?: return@locked
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@locked
        if (exercise.sets.isNotEmpty()) return@locked
        val left = session.exercises.filter { it.id != sessionExerciseId }
        db.withTransaction {
            dao.remove(sessionExerciseId)
            if (session.currentId == sessionExerciseId) {
                val after = left.drop(session.exercises.indexOf(exercise))
                val next = left.firstOrNull { it.prescription != null && !it.done } ?: after.firstOrNull() ?: left.lastOrNull()
                dao.updateSession(dao.session(session.id)!!.copy(currentSessionExerciseId = next?.id, entry = null))
            }
        }
        reload()
    }

    /**
     * Moves [sessionExerciseId] [by] places in the session's order (−1 up, +1 down). A superset
     * moves as a pair, and moves past a pair as one place.
     */
    suspend fun move(sessionExerciseId: Long, by: Int) = locked {
        val session = _state.value ?: return@locked
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@locked
        val blocks = SessionOrder.blocks(session.exercises).toMutableList()
        val from = blocks.indexOfFirst { block -> block.any { it.id == exercise.id } }
        val to = from + by
        if (to !in blocks.indices) return@locked
        blocks.add(to, blocks.removeAt(from))
        db.withTransaction {
            blocks.flatten().forEachIndexed { i, e -> dao.setPosition(e.id, i) }
        }
        reload()
    }

    /** Puts another exercise of the session on screen. Rest keeps running. */
    suspend fun switchTo(sessionExerciseId: Long) = locked {
        val session = _state.value ?: return@locked
        correction = null
        viewingId = null
        if (session.currentId == sessionExerciseId) {
            reload()
            return@locked
        }
        val current = session.current
        var row = dao.session(session.id)!!
        // Leaving an open set closes it, so its rest runs while the other exercise comes up.
        if (session.openSetId != null && current != null) row = closed(row, session, current, current.sets.size - 1, clock(), to = sessionExerciseId)
        dao.updateSession(row.copy(currentSessionExerciseId = sessionExerciseId, entry = null))
        reload()
    }

    /**
     * Opens [sessionExerciseId] from the session list: a finished exercise is shown to look at or
     * correct, while the session stays where it is; any other comes up as [switchTo] does.
     */
    suspend fun open(sessionExerciseId: Long) {
        val session = _state.value ?: return
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return
        if (!exercise.done || sessionExerciseId == session.currentId) return switchTo(sessionExerciseId)
        locked {
            correction = null
            viewingId = sessionExerciseId
            reload()
        }
    }

    /** Back from a finished exercise to the one the session is on. */
    suspend fun closeView() = locked {
        correction = null
        viewingId = null
        reload()
    }

    /**
     * Loads set [set] of [sessionExerciseId], or its drop, mini-set or hold [extension], into the
     * pad to correct it. Nothing else changes: rest runs on and the set to do keeps its entry.
     * Not while a set is open for its drops, mini-sets or hold.
     */
    suspend fun correct(sessionExerciseId: Long, set: Int, extension: Int? = null) = locked {
        val session = _state.value ?: return@locked
        if (!session.correctable) return@locked
        correction = corrections.load(sessionExerciseId, set, extension) ?: return@locked
        checked = null
        _state.value = session.copy(correction = correction, weightCheck = null)
    }

    /** The hide key while correcting: the row keeps what it had. */
    suspend fun cancelCorrection() = locked {
        val session = _state.value ?: return@locked
        correction = null
        checked = null
        if (fixingLast) {
            fixingLast = false
            viewingId = null
            reload()
            return@locked
        }
        _state.value = session.copy(correction = null, weightCheck = null)
    }

    /**
     * **Fix** on the last set logged, wherever the session has moved: its exercise comes on screen
     * with the set loaded into the pad, on the weight if it was kept past a weight check. Saving
     * or cancelling goes back to the set to do.
     */
    suspend fun fixLast() = locked {
        val session = _state.value ?: return@locked
        val (id, index) = lastLogged ?: return@locked
        if (!session.correctable) return@locked
        val exercise = session.exercises.firstOrNull { it.id == id } ?: return@locked
        val loaded = corrections.load(id, index) ?: return@locked
        correction = if (exercise.sets.getOrNull(index)?.kept == true) loaded.copy(entry = loaded.entry.focus(Field.Weight)) else loaded
        checked = null
        fixingLast = true
        viewingId = id.takeIf { it != session.currentId }
        reload()
    }

    /**
     * Something the session reads changed outside it: a past session was corrected, or a Change
     * was accepted or reverted. The day, the suggestion and last time are worked out again, and an
     * untouched prefill with them.
     */
    suspend fun readAgain() = locked {
        val session = _state.value ?: return@locked
        if (untouched(session, session.currentId)) dao.updateSession(dao.session(session.id)!!.copy(entry = null))
        reload()
    }

    /** The set to do of [exerciseId] still has the prefill it started with: nothing has been typed into it. */
    private suspend fun untouched(session: ActiveSession, exerciseId: Long?): Boolean {
        val current = session.current ?: return false
        if (current.id != exerciseId || session.followUp != null || current.done || current.needsMachine || current.needsLadder) return false
        return session.entry != null && session.entry == prefill(current)
    }

    private suspend fun saveCorrection(session: ActiveSession, fixing: Correction) {
        when (val step = fixing.done()) {
            is Correction.Step.Moved -> {
                correction = step.correction
                _state.value = session.copy(correction = step.correction)
            }
            is Correction.Step.SaveSet -> {
                // A changed weight gets the check a logged one does: the first ✓ warns, a second keeps it.
                val exercise = session.exercises.firstOrNull { it.id == fixing.sessionExerciseId }
                val before = exercise?.sets?.getOrNull(fixing.set)
                val check = if (exercise != null && before != null && step.set.weightKg != before.weightKg) {
                    WeightCheck.of(step.set.weightKg, exercise.referenceBefore(fixing.set), exercise.ladder, exercise.ladderKnown)
                } else null
                if (check != null) {
                    val now = clock()
                    val raised = checked
                    if (raised == null || raised.first != fixing.entry) {
                        checked = fixing.entry to now
                        _state.value = session.copy(weightCheck = check)
                        return
                    }
                    if (now - raised.second < DOUBLE_TAP_MS) return
                }
                checked = null
                finishCorrection(session, fixing) {
                    corrections.saveSet(fixing, step.set)
                    if (check != null) corrections.keep(fixing)
                }
            }
            is Correction.Step.SaveExtension -> finishCorrection(session, fixing) { corrections.saveExtension(fixing, step.extension) }
            Correction.Step.Nothing -> Unit
        }
    }

    /**
     * Saves a correction and reloads. Sets after it are prefilled from it, so an untouched prefill
     * of the set to do is worked out again; rest is never touched.
     */
    private suspend fun finishCorrection(session: ActiveSession, fixing: Correction, save: suspend () -> Unit) {
        val untouched = untouched(session, fixing.sessionExerciseId)
        save()
        if (untouched) dao.updateSession(dao.session(session.id)!!.copy(entry = null))
        correction = null
        if (fixingLast) {
            fixingLast = false
            viewingId = null
            lastLogged = null
        }
        // A second ✓ right after saving is a stutter, not the set to do.
        lastLoggedAt = clock()
        reload()
    }

    suspend fun press(key: PadKey) = editEntry { entry, ladder -> entry.press(key, ladder) }
    suspend fun selectRir(value: Int) = editEntry { entry, _ -> entry.selectRir(value) }

    /** A myo mini-set and a hold are done at the set's weight, so their weight can't be focused. */
    suspend fun focus(field: Field) = editEntry { entry, _ ->
        val fixing = _state.value?.correction
        val locked = if (fixing != null) fixing.weightLocked else _state.value?.followUp.let { it is FollowUp.MiniSet || it is FollowUp.Hold }
        if (field == Field.Weight && locked) entry else entry.focus(field)
    }
    suspend fun partialMode(on: Boolean) = editEntry { entry, _ -> entry.partialMode(on) }

    /** Splits the active set of a unilateral exercise into left and right, or back to one number. */
    suspend fun split(on: Boolean) = editEntry { entry, _ ->
        val session = _state.value
        val fixing = session?.correction
        val exercise = (if (fixing != null) session.exercises.firstOrNull { it.id == fixing.sessionExerciseId } else session?.current)?.exercise
        val splittable = if (fixing != null) fixing.kind == null else session?.followUp == null
        if (exercise?.unilateral == true && splittable) entry.split(on) else entry
    }

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
        session.correction?.let {
            saveCorrection(session, it)
            return@locked
        }
        val current = session.current ?: return@locked
        var entry = session.entry ?: return@locked
        val followUp = session.followUp
        // ✓ during a hold stops its timer and logs the seconds held.
        session.holdStartedAt?.let { entry = entry.copy(full = heldSeconds(it).toString()) }
        if (followUp != null && entry.field == Field.Reps) {
            logFollowUp(session, current, followUp, entry)
            return@locked
        }
        when (val result = entry.done()) {
            is SetEntry.Done.Moved -> saveEntry(session, result.entry)
            is SetEntry.Done.Log -> {
                val now = clock()
                if (lastLoggedAt?.let { now - it < DOUBLE_TAP_MS } == true) return@locked
                val technique = current.techniqueAt(current.sets.size)
                var result: SetEntry.Done.Log = result
                if (technique is Technique.LengthenedPartials && !entry.partialMode && entry.partial.isEmpty()) {
                    val partials = technique.partials?.toString().orEmpty()
                    if (session.padVisible) {
                        // Lengthened partials: the first ✓ moves to the partials, the second logs.
                        saveEntry(session, entry.partialMode(true).copy(partial = partials))
                        return@locked
                    }
                    // The hidden pad's ✓ logs the set as shown: the target partials, or none to failure.
                    entry = entry.copy(partial = partials)
                    result = entry.done() as? SetEntry.Done.Log ?: result
                }
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
                val set = result.set.copy(kept = check != null, technique = technique)
                db.withTransaction {
                    val id = dao.insertSet(set.toEntity(current.id, current.sets.size, now))
                    lastLogged = current.id to current.sets.size
                    val row = dao.session(session.id)!!.copy(entry = null)
                    // A drop set, myo-reps or iso hold stays open for what follows it, with no rest
                    // (a breathing pause before a mini-set); any other set closes at once.
                    when (set.followUp()) {
                        null -> dao.updateSession(closed(row, session, current, current.sets.size, now))
                        is FollowUp.MiniSet -> dao.updateSession(
                            row.copy(openSetId = id, restEndsAt = now + BREATHING_MS, restTotalMs = BREATHING_MS, breathing = true),
                        )
                        else -> dao.updateSession(row.copy(openSetId = id, restEndsAt = null, breathing = false))
                    }
                }
                reload()
            }
            SetEntry.Done.Nothing -> Unit
        }
    }

    /** Logs the open set's next drop, mini-set or hold; the set closes once its technique has none left. */
    private suspend fun logFollowUp(session: ActiveSession, current: SessionExercise, followUp: FollowUp, entry: SetEntry) {
        val extension = entry.toExtension(followUp.kind) ?: return
        val openId = session.openSetId ?: return
        val now = clock()
        if (lastLoggedAt?.let { now - it < DOUBLE_TAP_MS } == true) return
        lastLoggedAt = now
        val set = current.sets.last()
        val updated = set.copy(extensions = set.extensions + extension)
        db.withTransaction {
            dao.insertExtension(
                SetExtensionEntity(loggedSetId = openId, position = set.extensions.size, kind = extension.kind.name, weightKg = extension.weightKg, reps = extension.reps),
            )
            val row = dao.session(session.id)!!.copy(entry = null, holdStartedAt = null)
            dao.updateSession(
                when (updated.followUp()) {
                    null -> closed(row, session, current, current.sets.size - 1, now)
                    is FollowUp.MiniSet -> row.copy(restEndsAt = now + BREATHING_MS, restTotalMs = BREATHING_MS, breathing = true)
                    else -> row
                },
            )
        }
        reload()
    }

    /** **Finish set** on the pad: closes the open set before its technique runs out, and rest starts. */
    suspend fun finishSet() = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        if (session.openSetId == null) return@locked
        dao.updateSession(closed(dao.session(session.id)!!.copy(entry = null, holdStartedAt = null), session, current, current.sets.size - 1, clock()))
        reload()
    }

    /** Starts the iso hold's timer; ✓ or [stopHold] records the seconds held. */
    suspend fun startHold() = locked {
        val session = _state.value ?: return@locked
        if (session.followUp !is FollowUp.Hold || session.holdStartedAt != null) return@locked
        val now = clock()
        _state.value = session.copy(holdStartedAt = now)
        dao.updateSession(dao.session(session.id)!!.copy(holdStartedAt = now))
    }

    /** Stops the hold's timer and puts the seconds held in the entry, to log with ✓. */
    suspend fun stopHold() = locked {
        val session = _state.value ?: return@locked
        val started = session.holdStartedAt ?: return@locked
        val entry = session.entry ?: return@locked
        val next = entry.copy(full = heldSeconds(started).toString(), fresh = true)
        _state.value = session.copy(holdStartedAt = null, entry = next)
        dao.updateSession(dao.session(session.id)!!.copy(holdStartedAt = null, entry = next.toColumns()))
    }

    private fun heldSeconds(startedAt: Long) = ((clock() - startedAt) / 1000).toInt().coerceAtLeast(1)

    /**
     * [row] with set [setIndex] of [current] closed: no set open and the next set in the session's
     * order on screen ([SessionOrder]). The set's rest runs, except on the way to a superset
     * partner's set of the same round, which comes straight away. With [to], that exercise comes
     * up instead, and rest runs unless it is the one that would have come straight away.
     */
    private fun closed(row: SessionEntity, session: ActiveSession, current: SessionExercise, setIndex: Int, now: Long, to: Long? = null): SessionEntity {
        val rest = current.prescription?.getOrNull(setIndex)?.rest?.let { it * 1000L } ?: DEFAULT_REST_MS
        val step = SessionOrder.after(session.exercises, current.id, setIndex)
        val rests = step.rest || (to != null && to != step.exerciseId)
        return row.copy(
            openSetId = null, breathing = false, holdStartedAt = null,
            restEndsAt = if (rests) now + rest else null,
            restTotalMs = if (rests) rest else row.restTotalMs,
            currentSessionExerciseId = to ?: step.exerciseId,
        )
    }

    /**
     * Adds, changes or (with null) takes off the technique of set [setIndex] of the exercise on
     * screen, for this session. Logged sets keep what they were done with.
     */
    suspend fun setTechnique(setIndex: Int, technique: Technique?) = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        if (setIndex < current.sets.size) return@locked
        db.withTransaction {
            dao.setTechnique(SetTechniqueEntity(current.id, setIndex, technique?.let(TechniqueJson::encode)))
            // A set taken to failure has nothing in reserve.
            val entry = session.entry
            if (setIndex == current.sets.size && technique == Technique.Failure && entry != null) {
                dao.updateSession(dao.session(session.id)!!.copy(entry = entry.selectRir(0).toColumns()))
            }
        }
        reload()
    }

    /**
     * Ticks warm-up [index] of the exercise on screen off, with the ones before it; on the last
     * one ticked, takes it back. Warm-ups aren't sets: rest, the pad and the work sets stay as they are.
     */
    suspend fun tickWarmup(index: Int) = locked {
        val session = _state.value ?: return@locked
        val current = session.current ?: return@locked
        if (current.sets.isNotEmpty() || index !in session.warmups.indices) return@locked
        val done = if (index == current.warmupsDone - 1) index else index + 1
        // The screen shows the tick at once; the write follows before the next call runs.
        _state.value = session.copy(exercises = session.exercises.map { if (it.id == current.id) it.copy(warmupsDone = done) else it })
        dao.setWarmupsDone(current.id, done)
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
        correction = null
        viewingId = null
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
        session.correction?.let { fixing ->
            val ladder = session.exercises.firstOrNull { it.id == fixing.sessionExerciseId }?.ladder ?: return@locked
            val next = fixing.copy(entry = change(fixing.entry, ladder))
            correction = next
            if (next != fixing) checked = null
            _state.value = session.copy(correction = next, weightCheck = if (next != fixing) null else session.weightCheck)
            return@locked
        }
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
        val context = stored.context(row)
        val (_, _, gym, day) = context
        val shared = context.here
        checked = null
        val exercises = dao.sessionExercises(row.id).filter { !it.removed }.map { se ->
            val (exercise, machine, ladder, slot, plan) = context.exercise(se)
            val perMachine = exercise.equipment.perMachine
            val needsMachine = perMachine && gym != null && machine == null
            val needsLadder = perMachine && machine?.state == LadderState.Unconfirmed && dao.sets(se.id).isEmpty()
            // A planned exercise's last time is the session progression judges (same slot and rep
            // range), so the Last column, the prefill and the why-line name the same sets.
            val past = if (plan == null || needsMachine) emptyList() else history(se.exerciseId, row.id, perMachine, machine?.id, exercise.weighted, exercise.implements)
            val judged = plan?.firstOrNull()?.let { Progression.judged(it, past.map { p -> p.second }, se.slotId) }
            // Machines and cables only compare with sets on the same machine (ADR 0002).
            val lastTime = when {
                needsMachine -> emptyList()
                judged != null -> dao.sets(past[judged].first)
                perMachine -> dao.lastTimeOn(se.exerciseId, row.id, machine?.id)
                else -> dao.lastTime(se.exerciseId, row.id)
            }.let { stored.sets(it) }
            val sets = stored.sets(se.id)
            val onScreen = se.id == row.currentSessionExerciseId && !needsMachine && !needsLadder
            // The old app's weight stands in until the exercise (on this machine) has its own history.
            val starting = if (onScreen && sets.isEmpty() && lastTime.isEmpty()) startingWeights.get(se.exerciseId) else null
            val start = starting?.takeIf { !perMachine }
            val suggestion = when {
                !onScreen -> null
                plan == null -> start?.suggestion(null, ladder.ladder)
                else -> suggest(
                    plan,
                    // A weighted bodyweight exercise counts the latest bodyweight with the belt (SPEC, Bodyweight).
                    Performance(
                        workSets(sets, plan),
                        deload = day?.deload == true,
                        bodyweightKg = if (exercise.weighted) bodyweights.latest() ?: 0.0 else 0.0,
                        slot = se.slotId,
                        dumbbells = exercise.implements,
                    ),
                    past.map { it.second },
                    ladder.ladder,
                ) ?: start?.takeIf { sets.isEmpty() }?.suggestion(plan.first(), ladder.ladder)
            }?.let { s -> slot?.loadPercent?.takeIf { it != 0 }?.let { s.adjusted(it, ladder.ladder) } ?: s }
            SessionExercise(
                id = se.id,
                exercise = exercise,
                sets = sets,
                lastTime = lastTime,
                ladder = ladder.ladder,
                ladderKnown = ladder.known,
                machine = machine,
                needsMachine = needsMachine,
                needsLadder = needsLadder,
                ladderOffer = machine?.takeIf { onScreen && it.state in Learning }?.let { m ->
                    StackReading.learn(gyms.weightsOn(m.id), m.ladder, gym?.pinPlates.orEmpty())
                },
                slot = slot,
                prescription = plan,
                suggestion = suggestion,
                startingHint = starting?.takeIf { perMachine }?.text(),
                techniques = stored.techniques(se.id),
                plates = when {
                    exercise.equipment == Equipment.Barbell -> shared.plates
                    else -> shared.barPlates(exercise.equipment) ?: machine?.ladder as? LadderSpec.Plates
                },
                warmupsDone = se.warmupsDone,
                pinPlates = gym?.pinPlates.orEmpty(),
            )
        }
        val current = exercises.firstOrNull { it.id == row.currentSessionExerciseId }
        val openSet = if (row.openSetId != null) current?.sets?.lastOrNull() else null
        val followUp = openSet?.followUp()
        var entry = row.entry?.toEntry()
        if (followUp != null && current != null) {
            if (entry == null) {
                entry = followUpEntry(followUp, openSet, current.lastTime.getOrNull(current.sets.size - 1), current.ladder)
                dao.updateSession(row.copy(entry = entry.toColumns()))
            }
        } else if (current?.needsMachine == true || current?.needsLadder == true || current?.done == true) {
            entry = null
        }
        if (entry == null && current != null && !current.needsMachine && !current.needsLadder && !current.done) {
            val fresh = prefill(current)
            entry = fresh
            dao.updateSession(row.copy(entry = fresh.toColumns()))
        }
        viewingId = exercises.firstOrNull { it.id == viewingId && it.id != current?.id }?.id
        correction = correction?.takeIf { c -> followUp == null && exercises.any { it.id == c.sessionExerciseId } }
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
            followUp = followUp,
            breathing = row.breathing,
            holdStartedAt = row.holdStartedAt,
            openSetId = row.openSetId.takeIf { followUp != null },
            viewingId = viewingId,
            correction = correction,
            lastLogged = lastLogged?.takeIf { (id, i) -> exercises.any { it.id == id && i < it.sets.size } },
        )
    }

    /** The entry the next set of [current] starts with, before anything is typed. */
    private suspend fun prefill(current: SessionExercise): SetEntry {
        val plan = current.prescription
        val entry = if (plan != null) plannedEntry(plan, current.sets, current.suggestion)
        else current.suggestion?.let { s ->
            SetEntry(formatKg(s.kg), s.reps.toString(), "", startingWeights.get(current.exercise.id)?.rir, Field.Reps, partialMode = false, fresh = true)
        } ?: SetEntry.prefill(current.sets.countingSets().lastOrNull() ?: current.lastTime.countingSets().firstOrNull())
        // A plain bodyweight exercise has no weight to type: it starts on the reps at 0 kg.
        val bodyweight = if (current.exercise.equipment == Equipment.Bodyweight && entry.weightKg == null) {
            entry.copy(weight = "0", field = if (entry.field == Field.Weight) Field.Reps else entry.field)
        } else entry
        // A split set is followed by a split set, starting from its right side's reps.
        return current.sets.lastOrNull()?.rightReps?.let { bodyweight.copy(right = it.toString()) } ?: bodyweight
    }


    /**
     * The entry for an open set's next drop, mini-set or hold. A drop is the weight before it
     * minus the technique's percent, rounded down to a rung, with last time's reps for that drop
     * (else [DEFAULT_DROP_REPS]); a mini-set and a hold are at the set's weight.
     */
    private fun followUpEntry(followUp: FollowUp, set: LoggedSet, lastTime: LoggedSet?, ladder: WeightLadder): SetEntry {
        val (kg, reps) = when (followUp) {
            is FollowUp.Drop -> {
                val technique = set.technique as Technique.Dropset
                val before = set.extensions.lastOrNull()?.weightKg ?: set.weightKg
                val kg = backoffWeight(before, technique.percent, ladder)
                val last = lastTime?.extensions?.filter { it.kind == Extension.Kind.Drop }?.getOrNull(followUp.index)?.reps
                kg to (last ?: DEFAULT_DROP_REPS)
            }
            is FollowUp.MiniSet -> set.weightKg to followUp.target
            is FollowUp.Hold -> set.weightKg to followUp.seconds
        }
        return SetEntry(formatKg(kg), reps.toString(), "", null, Field.Reps, partialMode = false, fresh = true)
    }

    /**
     * The exercise's past sessions (on [itemId] for machines), oldest first: each one's session
     * exercise id, and its performance with the slot it filled, the
     * prescription it was done under, whether its week was a deload and, for a [weighted]
     * bodyweight exercise, the bodyweight of the day.
     */
    private suspend fun history(exerciseId: String, sessionId: Long, perMachine: Boolean, itemId: Long?, weighted: Boolean, dumbbells: Int): List<Pair<Long, Performance>> =
        dao.history(exerciseId, sessionId, anyItem = !perMachine, itemId = itemId, limit = HISTORY_SESSIONS).reversed().map { past ->
            val day = if (past.programId == null || past.week == null || past.dayIndex == null) null
            else programs.plannedDay(past.programId, Position(past.week, past.dayIndex), programs.dateOf(past.startedAt), asOf = past.finishedAt)
            val plan = past.sets?.let(SetPrescriptionJson::decode) ?: day?.slot(past.slotId)?.setsFor(exerciseId)
            past.id to Performance(
                workSets(stored.sets(past.id), plan),
                deload = day?.deload == true,
                bodyweightKg = if (weighted) bodyweights.on(programs.dateOf(past.startedAt)) ?: 0.0 else 0.0,
                slot = past.slotId,
                dumbbells = dumbbells,
            )
        }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { block() }

    companion object {
        /** Rest after a set in an ad-hoc session, which has no prescription to take it from. */
        const val DEFAULT_REST_MS = 120_000L

        /** No two sets are logged this close together on purpose; a second ✓ is a stutter. */
        const val DOUBLE_TAP_MS = 1_000L

        /** The ladder states typed weights teach. */
        private val Learning = setOf(LadderState.Guess, LadderState.Unconfirmed)

        /** How many past sessions of an exercise progression reads. */
        const val HISTORY_SESSIONS = 12
    }
}

/**
 * What a new set's weight is checked against: the last set that counts, or last time's first. A
 * weight kept past a check doesn't count until a set is logged near it.
 */
/** Weighted bodyweight: the body plus the load on the belt or held. */
private val Exercise.weighted get() = equipment == Equipment.WeightedBodyweight

/** What set [index]'s corrected weight is checked against: the last set before it that counts, or last time's first. */
private fun SessionExercise.referenceBefore(index: Int): Reference? =
    sets.take(index).countingSets().lastOrNull()?.let { Reference(it.weightKg, fromThisSession = true) }
        ?: lastTime.countingSets().firstOrNull()?.let { Reference(it.weightKg, fromThisSession = false) }

private fun SessionExercise.reference(): Reference? =
    sets.countingSets().lastOrNull()?.let { Reference(it.weightKg, fromThisSession = true) }
        ?: lastTime.countingSets().firstOrNull()?.let { Reference(it.weightKg, fromThisSession = false) }

/**
 * [sets] as progression reads them: the main set only (the weaker side of a split set, and no
 * partials, drops or mini-sets), with its prescription and whether it counts.
 */
private fun workSets(sets: List<LoggedSet>, plan: List<SetPrescription>?): List<WorkSet> {
    val counting = sets.counting()
    return sets.mapIndexed { i, set ->
        WorkSet(LoggedSet(set.weightKg, set.progressionReps, 0, set.rir, set.kept), plan?.getOrNull(i), counting[i])
    }
}

private fun LoggedSet.toEntity(sessionExerciseId: Long, position: Int, loggedAt: Long) =
    LoggedSetEntity(
        sessionExerciseId = sessionExerciseId, position = position, weightKg = weightKg,
        fullReps = fullReps, partialReps = partialReps, rir = rir, loggedAt = loggedAt, kept = kept,
        technique = technique?.let(TechniqueJson::encode), rightReps = rightReps,
    )

internal fun LoggedSetEntity.toLoggedSet(extensions: List<Extension>) =
    LoggedSet(weightKg, fullReps, partialReps, rir, kept, technique?.let(TechniqueJson::decode), extensions, rightReps)

internal fun SetExtensionEntity.toExtension() = Extension(Extension.Kind.valueOf(kind), weightKg, reps)

private fun SetEntry.toColumns() = EntryColumns(weight, full, partial, rir, field.name, partialMode, fresh, right)

private fun EntryColumns.toEntry() = SetEntry(weight, full, partial, rir, Field.valueOf(field), partialMode, fresh, right)
