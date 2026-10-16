package com.hyperlog.app.ui.program

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.coach.AskClaude
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.history.History
import com.hyperlog.app.library.toExercise
import java.time.DayOfWeek
import java.time.LocalDate
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
            val ends = active.history.filter { it.id in change.replaces }.map { it.change.summary }
            _review.value = ChangeReview(change, diff, active.program, block, ends)
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

    /** The problems of the reply last pasted, until it's pasted again or the words change. */
    val pasteErrors: StateFlow<List<String>?> = _pasteErrors.asStateFlow()

    /**
     * The prompt Ask Claude shares for [words], with the [gym] picked, the days off since
     * [lastTrained], the weekly sets and Build me a workout's [options] (null while closed).
     */
    suspend fun askClaudePrompt(
        words: String,
        gym: Gym?,
        elsewhere: Boolean,
        lastTrained: LocalDate?,
        weekdays: Set<DayOfWeek>,
        options: com.hyperlog.app.coach.WorkoutOptions? = null,
    ): String {
        _reply.value = null
        val active = programs.active.first()
        val db = HyperlogDatabase.get(getApplication())
        val library = db.exercises().all().map { it.toExercise() }
        val sessions = PastSessionEditor(db, programs).sessions()
        val weeks = History(db, programs).weeklySets(AskClaude.SET_WEEKS)
        return withContext(Dispatchers.Default) {
            AskClaude.prompt(
                words, active, sessions, library, gym = gym, elsewhere = elsewhere, lastTrained = lastTrained, weekdays = weekdays,
                weeklySets = weeks, options = options,
            )
        }
    }

    /** Reads a reply pasted back from Claude: a Change's diff or a workout opens for review, or its problems show. */
    fun paste(text: String) {
        viewModelScope.launch {
            when (val result = read(text)) {
                is AskClaude.Reply.Invalid -> _pasteErrors.value = result.errors
                else -> { _pasteErrors.value = null; _reply.value = null; open(result) }
            }
        }
    }

    private val _reply = MutableStateFlow<AskClaude.Reply?>(null)

    /** A valid Change or workout found on the clipboard on coming back from Claude, until reviewed or asked again. */
    val reply: StateFlow<AskClaude.Reply?> = _reply.asStateFlow()

    /** Reads the clipboard [text] quietly on return from Claude: a valid reply is kept for Review; anything else is ignored. */
    fun checkReply(text: String) {
        viewModelScope.launch {
            _reply.value = read(text).takeIf { it !is AskClaude.Reply.Invalid }
        }
    }

    fun clearReply() {
        _reply.value = null
    }

    /** Opens the reply found for review. */
    fun reviewReply() {
        val reply = _reply.value ?: return
        _reply.value = null
        _pasteErrors.value = null
        open(reply)
    }

    private fun open(reply: AskClaude.Reply) {
        when (reply) {
            is AskClaude.Reply.ChangeReply -> propose(reply.change)
            is AskClaude.Reply.WorkoutReply -> _workout.value = reply.workout
            is AskClaude.Reply.Invalid -> Unit
        }
    }

    private val _workout = MutableStateFlow<com.hyperlog.app.coach.Workout?>(null)

    /** A workout from Claude waiting for Add to Today or Reject; null when none is. */
    val workout: StateFlow<com.hyperlog.app.coach.Workout?> = _workout.asStateFlow()

    /** Drops the workout under review; nothing changes. */
    fun rejectWorkout() {
        _workout.value = null
    }

    private val workouts = com.hyperlog.app.coach.Workouts(HyperlogDatabase.get(application))

    /** The workouts waiting on Today, newest first; Today shows the one added today. */
    val waiting: StateFlow<List<com.hyperlog.app.coach.Workouts.Waiting>> =
        workouts.waiting.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Puts the workout under review on Today, to start at [gymId] (the gym picked on Coach); any other waiting goes. */
    fun addWorkout(gymId: Long?) {
        val workout = _workout.value ?: return
        _workout.value = null
        viewModelScope.launch { workouts.add(workout, gymId) }
    }

    /** Takes waiting workout [id] off Today. */
    fun removeWorkout(id: Long) {
        viewModelScope.launch { workouts.remove(id) }
    }

    private val _lowest = MutableStateFlow<List<Pair<String, Double>>>(emptyList())

    /** The focus's marks on Coach: the muscles trained lately with the fewest sets this week. */
    val lowest: StateFlow<List<Pair<String, Double>>> = _lowest.asStateFlow()

    /** Reads the focus's marks again, as Coach opens. */
    fun refreshLowest() {
        viewModelScope.launch {
            val db = HyperlogDatabase.get(getApplication())
            _lowest.value = com.hyperlog.app.coach.WorkoutOptions.lowest(History(db, programs).weeklySets(AskClaude.SET_WEEKS))
        }
    }

    /** [text] read as a Change for the running program or a workout. */
    private suspend fun read(text: String): AskClaude.Reply {
        val active = programs.active.first()
        val ids = HyperlogDatabase.get(getApplication()).exercises().all().map { it.id }.toSet()
        val inForce = active?.inForce.orEmpty().map { it.id }.toSet()
        return withContext(Dispatchers.Default) { AskClaude.read(text, active?.program, ids, inForce) }
    }

    fun closePasteErrors() {
        _pasteErrors.value = null
    }

    private val _inForce = MutableStateFlow<Pair<Long, ChangeReview>?>(null)

    /** A Change in force opened from its card on Coach, read-only, with its id for Revert; null when none is open. */
    val inForce: StateFlow<Pair<Long, ChangeReview>?> = _inForce.asStateFlow()

    /** Opens the Change in force [id]: what it does from here on, with the other Changes applied. */
    fun openInForce(id: Long) {
        viewModelScope.launch {
            val active = programs.active.first() ?: return@launch
            val record = active.inForce.firstOrNull { it.id == id } ?: return@launch
            val diff = withContext(Dispatchers.Default) {
                Changes.inForceDiff(active.program, active.position, active.today, active.changes, record.accepted)
            }
            val block = active.program.blocks.firstOrNull { it.id == record.accepted.blockId }?.name
            _inForce.value = id to ChangeReview(record.change, diff, active.program, block)
        }
    }

    fun closeInForce() {
        _inForce.value = null
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
