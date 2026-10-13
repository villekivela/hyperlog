package com.hyperlog.app.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.PastSessionEditor
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.SessionRuntime
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a past exercise's table asks for: the pad, and loading a logged row into it. */
interface PastSessionActions : PadActions {
    fun correct(sessionExerciseId: Long, set: Int, extension: Int?)
}

/**
 * Past sessions, to look at and correct: Session done after finishing, and the list reached from
 * Today. A correction tells the running session, whose suggestion may rest on it.
 */
class PastSessionsViewModel(application: Application) : AndroidViewModel(application), PastSessionActions {
    private val runtime = SessionRuntime.get(application)
    private val editor = PastSessionEditor(
        HyperlogDatabase.get(application),
        ProgramStore.get(application),
        onChanged = { runtime.logger.readAgain() },
    )
    private val _sessions = MutableStateFlow<List<PastSessionItem>?>(null)

    /** The session open; null when none is. */
    val session: StateFlow<PastSession?> = editor.state

    /** Every finished session, newest first; null until read. */
    val sessions: StateFlow<List<PastSessionItem>?> = _sessions.asStateFlow()

    /** Reads the list again, with any corrections made since. */
    fun refresh() = run { _sessions.value = editor.sessions() }

    fun open(sessionId: Long) = run { editor.open(sessionId) }

    /** Opens an exercise's table, or with null goes back to the summary. */
    fun show(sessionExerciseId: Long?) = run { editor.show(sessionExerciseId) }

    fun close() = editor.close()

    override fun correct(sessionExerciseId: Long, set: Int, extension: Int?) = run { editor.correct(sessionExerciseId, set, extension) }
    override fun cancelCorrection() = run { editor.cancelCorrection() }
    override fun press(key: PadKey) = run { editor.press(key) }
    override fun done() = run { editor.done() }
    override fun selectRir(value: Int) = run { editor.selectRir(value) }
    override fun focus(field: Field) = run { editor.focus(field) }
    override fun partialMode(on: Boolean) = run { editor.partialMode(on) }
    override fun pickWeight(kg: Double) = run { editor.pickWeight(kg) }
    override fun split(on: Boolean) = run { editor.split(on) }

    // A correction is saved or cancelled; it has no hidden pad, timer or open set.
    override fun setPadVisible(visible: Boolean) = Unit
    override fun finishSet() = Unit
    override fun startHold() = Unit
    override fun stopHold() = Unit

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
