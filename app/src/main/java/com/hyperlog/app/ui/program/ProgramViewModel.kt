package com.hyperlog.app.ui.program

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.coach.AskClaude
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeValidator
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.session.PastSessionEditor
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.ui.coach.ChangeReview
import kotlinx.coroutines.flow.first
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Program screen's state: the program running, and the problems of a file just rejected. */
class ProgramViewModel(application: Application) : AndroidViewModel(application) {
    private val programs = ProgramStore.get(application)

    val active: StateFlow<ActiveProgram?> = programs.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _errors = MutableStateFlow<List<String>?>(null)

    /** The problems of the last file rejected, until closed. */
    val errors: StateFlow<List<String>?> = _errors.asStateFlow()

    /** Reads the file at [uri] and imports it if it is valid. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            }
            if (text == null) {
                _errors.value = listOf(getApplication<Application>().getString(com.hyperlog.app.R.string.program_unreadable))
                return@launch
            }
            importText(text)
        }
    }

    fun importText(text: String) {
        viewModelScope.launch {
            val result = programs.import(text)
            _errors.value = (result as? ProgramParser.Result.Invalid)?.errors
        }
    }

    /** The starter program, offered while no program runs; null if its file can't be read. */
    val starter: Program? by lazy {
        runCatching { StarterProgram.source(getApplication()) }.getOrNull()
            ?.let { ProgramParser.parse(it, AnyExerciseId) as? ProgramParser.Result.Valid }
            ?.program
    }

    /** Imports the starter program like any other file. */
    fun runStarter() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { StarterProgram.source(getApplication()) }.getOrNull() }
            if (text != null) importText(text)
        }
    }

    fun closeErrors() {
        _errors.value = null
    }

    private val _review = MutableStateFlow<ChangeReview?>(null)

    /** The Change waiting for accept or reject, with its diff; null when none is. */
    val review: StateFlow<ChangeReview?> = _review.asStateFlow()

    /** Shows [change]'s diff against the running program's plan, for accept or reject. */
    fun propose(change: Change) {
        viewModelScope.launch {
            val active = programs.active.first() ?: return@launch
            val diff = programs.preview(change) ?: return@launch
            val block = active.program.blockAt(active.position.week.coerceAtMost(active.program.weeks))?.name
            _review.value = ChangeReview(change, diff, active.program, block)
        }
    }

    /** Applies the Change under review and keeps it as history. */
    fun accept() {
        val review = _review.value ?: return
        _review.value = null
        viewModelScope.launch { programs.accept(review.change) }
    }

    /** Drops the Change under review; nothing changes. */
    fun reject() {
        _review.value = null
    }

    private val _pasteErrors = MutableStateFlow<List<String>?>(null)

    /** The problems of the Change last pasted, until it's pasted again or the words change. */
    val pasteErrors: StateFlow<List<String>?> = _pasteErrors.asStateFlow()

    /** The prompt Ask Claude shares for [words]; null with no program running. */
    suspend fun askClaudePrompt(words: String): String? {
        val active = programs.active.first() ?: return null
        val db = HyperlogDatabase.get(getApplication())
        val library = db.exercises().all().map { it.toExercise() }
        val sessions = PastSessionEditor(db, programs).sessions()
        return withContext(Dispatchers.Default) { AskClaude.prompt(words, active, sessions, library) }
    }

    /** Reads a Change pasted back from Claude: its diff opens for review, or its problems show. */
    fun paste(text: String) {
        viewModelScope.launch {
            val active = programs.active.first() ?: return@launch
            val ids = HyperlogDatabase.get(getApplication()).exercises().all().map { it.id }.toSet()
            when (val result = withContext(Dispatchers.Default) { AskClaude.read(text, active.program, ids) }) {
                is ChangeValidator.Result.Valid -> { _pasteErrors.value = null; propose(result.change) }
                is ChangeValidator.Result.Invalid -> _pasteErrors.value = result.errors
            }
        }
    }

    fun closePasteErrors() {
        _pasteErrors.value = null
    }

    fun revert(changeId: Long) {
        viewModelScope.launch { programs.revert(changeId) }
    }
}

/** One [Programs] for the app, so its parsed programs are shared by the screens and the session. */
object ProgramStore {
    @Volatile private var instance: Pair<HyperlogDatabase, Programs>? = null

    fun get(application: Application): Programs {
        val db = HyperlogDatabase.get(application)
        instance?.let { (d, p) -> if (d === db) return p }
        return synchronized(this) {
            instance?.takeIf { it.first === db }?.second ?: Programs(db, merged = mergedIds(application)).also { instance = db to it }
        }
    }

    private fun mergedIds(application: Application) =
        runCatching { com.hyperlog.app.library.LibrarySeed.fromAssets(application).merged }.getOrDefault(emptyMap())
}

/** Accepts every exercise id: the starter is only read here to show it; importing checks it. */
private object AnyExerciseId : Set<String> by emptySet() {
    override fun contains(element: String) = true
}
