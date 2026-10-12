package com.hyperlog.app.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.Exercise
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.session.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
}

class SessionViewModel(application: Application) : AndroidViewModel(application), SessionActions {
    private val logger = SessionLogger(HyperlogDatabase.get(application))
    private val _loaded = MutableStateFlow(false)
    private val _summary = MutableStateFlow<SessionSummary?>(null)

    val session: StateFlow<ActiveSession?> = logger.state
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** The summary of the session just finished, until it is dismissed. */
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    val starterExercises: List<Exercise> get() = logger.starterExercises

    init {
        viewModelScope.launch {
            logger.load()
            _loaded.value = true
        }
    }

    fun start() = run { logger.start() }
    fun dismissSummary() { _summary.value = null }

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

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
