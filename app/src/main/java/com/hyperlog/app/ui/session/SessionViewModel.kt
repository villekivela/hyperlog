package com.hyperlog.app.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.session.SessionSummary
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the session screen can ask for. Each call is saved as soon as it is made. */
interface SessionActions {
    fun press(key: PadKey)
    fun done()
    fun selectRir(value: Int)
    fun focus(field: Field)
    fun partialMode(on: Boolean)
    fun pickWeight(kg: Double)
    fun setPadVisible(visible: Boolean)
    fun addExercise(exerciseId: String)
    fun switchTo(sessionExerciseId: Long)
    fun adjustRest(deltaMs: Long)
    fun endRest()
    fun finish()
    /** Answers "Which machine?" with one of the gym's equipment items. */
    fun chooseMachine(itemId: Long)
    /** Answers "Which machine?" with a new machine at the gym. */
    fun addMachine(name: String, ladder: LadderSpec)
    /** Asks "Which machine?" again, before any set is logged on it. */
    fun changeMachine()
    /** Closes the open drop set, myo-reps or hold before its technique runs out. */
    fun finishSet()
    fun startHold()
    fun stopHold()
    /** Gives set [setIndex] of the exercise on screen a technique for this session, or none. */
    fun setTechnique(setIndex: Int, technique: Technique?)
    /** Splits the active set of a unilateral exercise into left and right, or back. */
    fun split(on: Boolean)
    /** Swaps a session exercise to another exercise, for this session only. */
    fun swap(sessionExerciseId: Long, exerciseId: String)
    fun addSet(sessionExerciseId: Long)
    fun removeSet(sessionExerciseId: Long, setIndex: Int)
    /** Changes a planned set's target reps and RIR for this session. */
    fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?)
    /** Takes an exercise with nothing logged out of this session. */
    fun removeExercise(sessionExerciseId: Long)
    /** Moves an exercise (a superset as a pair) [by] places in the session's order. */
    fun move(sessionExerciseId: Long, by: Int)
}

class SessionViewModel(application: Application) : AndroidViewModel(application), SessionActions {
    private val db = HyperlogDatabase.get(application)
    private val library = ExerciseLibrary(db)
    private val programs = ProgramStore.get(application)
    private val logger = SessionLogger(db, programs = programs)
    private val gyms = Gyms(db)
    private val _loaded = MutableStateFlow(false)
    private val _summary = MutableStateFlow<SessionSummary?>(null)

    val session: StateFlow<ActiveSession?> = logger.state
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** The summary of the session just finished, until it is dismissed. */
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    /** The whole library, hidden exercises included. */
    val exercises: StateFlow<List<Exercise>> = library.exercises.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // The shipped library comes first: sessions point at its exercises.
            library.sync(withContext(Dispatchers.IO) { LibrarySeed.fromAssets(application) })
            logger.load()
            _loaded.value = true
            // An edit to the shared dumbbells, plates or a gym's equipment reaches the running session at once.
            combine(gyms.dumbbells, gyms.plates, gyms.gyms) { d, p, g -> Triple(d, p, g) }.drop(1).collect { logger.load() }
        }
    }

    /** The gym to preselect for a session started now; null with no gyms. */
    val usualGym: StateFlow<Long?> = gyms.usualGymToday().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Starts a session at [gymId]: the program's next day, or ad hoc when [adHoc] or with no day to train. */
    fun start(gymId: Long?, adHoc: Boolean = false) = run {
        val program = if (adHoc) null else programs.active.first()
        if (program?.next != null) logger.startDay(program, gymId) else logger.start(gymId)
    }
    fun dismissSummary() { _summary.value = null }

    /** Runs the session just finished again. */
    fun resumeFinished() {
        val finished = _summary.value ?: return
        _summary.value = null
        run { logger.resume(finished.sessionId) }
    }

    override fun press(key: PadKey) = run { logger.press(key) }
    override fun done() = run { logger.done() }
    override fun selectRir(value: Int) = run { logger.selectRir(value) }
    override fun focus(field: Field) = run { logger.focus(field) }
    override fun partialMode(on: Boolean) = run { logger.partialMode(on) }
    override fun pickWeight(kg: Double) = run { logger.pickWeight(kg) }
    override fun setPadVisible(visible: Boolean) = run { logger.setPadVisible(visible) }
    override fun addExercise(exerciseId: String) = run { logger.addExercise(exerciseId) }
    override fun switchTo(sessionExerciseId: Long) = run { logger.switchTo(sessionExerciseId) }
    override fun adjustRest(deltaMs: Long) = run { logger.adjustRest(deltaMs) }
    override fun endRest() = run { logger.endRest() }
    override fun finish() = run { _summary.value = logger.finish() }
    override fun chooseMachine(itemId: Long) = run { logger.chooseMachine(itemId) }
    override fun addMachine(name: String, ladder: LadderSpec) = run { logger.addMachine(name, ladder) }
    override fun changeMachine() = run { logger.changeMachine() }
    override fun finishSet() = run { logger.finishSet() }
    override fun startHold() = run { logger.startHold() }
    override fun stopHold() = run { logger.stopHold() }
    override fun setTechnique(setIndex: Int, technique: Technique?) = run { logger.setTechnique(setIndex, technique) }
    override fun split(on: Boolean) = run { logger.split(on) }
    override fun swap(sessionExerciseId: Long, exerciseId: String) = run { logger.swap(sessionExerciseId, exerciseId) }
    override fun addSet(sessionExerciseId: Long) = run { logger.addSet(sessionExerciseId) }
    override fun removeSet(sessionExerciseId: Long, setIndex: Int) = run { logger.removeSet(sessionExerciseId, setIndex) }
    override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?) =
        run { logger.setTarget(sessionExerciseId, setIndex, reps, rir) }
    override fun removeExercise(sessionExerciseId: Long) = run { logger.removeExercise(sessionExerciseId) }
    override fun move(sessionExerciseId: Long, by: Int) = run { logger.move(sessionExerciseId, by) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
