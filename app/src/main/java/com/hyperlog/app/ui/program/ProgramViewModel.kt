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
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Program screen's state: the program running, and the problems of a file just rejected. */
class ProgramViewModel(application: Application) : AndroidViewModel(application) {
    private val programs = ProgramStore.get(application)

    val active: StateFlow<ActiveProgram?> = programs.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val weekdayPlans = WeekdayPlans(HyperlogDatabase.get(application))

    /** Your training days, each with its gym (SPEC, Training days). */
    val weekdayPlan: StateFlow<WeekdayPlan> =
        weekdayPlans.plan.stateIn(viewModelScope, SharingStarted.Eagerly, WeekdayPlan.None)

    fun setWeekdayPlan(plan: WeekdayPlan) {
        viewModelScope.launch { weekdayPlans.set(plan) }
    }

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

    /** Applies the Change under review and keeps it as history, with the check-in that made it. */
    fun accept(checkIn: com.hyperlog.app.coach.SavedCheckIn? = null) {
        val review = _review.value ?: return
        _review.value = null
        viewModelScope.launch { programs.accept(review.change, checkIn) }
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
     * [lastTrained], the weekly sets and the check-in's [tile].
     */
    suspend fun askClaudePrompt(
        words: String,
        gym: Gym?,
        elsewhere: Boolean,
        lastTrained: LocalDate?,
        weekdays: Set<DayOfWeek>,
        tile: com.hyperlog.app.coach.Tile? = null,
    ): String {
        _reply.value = null
        val active = programs.active.first()
        val db = HyperlogDatabase.get(getApplication())
        val library = db.exercises().all().map { it.toExercise() }
        val sessions = PastSessionEditor(db, programs).sessions()
        val weeks = History(db, programs).weeklySets(AskClaude.SET_WEEKS)
        val weekdayPlan = weekdayPlans.current()
        val gymNames = com.hyperlog.app.gym.Gyms(db).gyms.first().associate { it.id to it.name }
        return withContext(Dispatchers.Default) {
            AskClaude.prompt(
                words, active, sessions, library, gym = gym, elsewhere = elsewhere, lastTrained = lastTrained, weekdays = weekdays,
                weeklySets = weeks, tile = tile, weekdayPlan = weekdayPlan, gymNames = gymNames,
            )
        }
    }

    /** Reads a reply pasted back from Claude: a Change's diff, a workout or a program opens for review, or its problems show. */
    fun paste(text: String) {
        viewModelScope.launch {
            when (val result = read(text)) {
                is AskClaude.Reply.Invalid -> _pasteErrors.value = result.errors
                else -> { _pasteErrors.value = null; _reply.value = null; open(result) }
            }
        }
    }

    private val _reply = MutableStateFlow<AskClaude.Reply?>(null)

    /** A valid Change, workout or program found on the clipboard on coming back from Claude, until reviewed or asked again. */
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
            is AskClaude.Reply.ProgramReply -> _program.value = reply.proposal
            is AskClaude.Reply.Invalid -> Unit
        }
    }

    private val _program = MutableStateFlow<com.hyperlog.app.coach.ProgramProposal?>(null)

    /** A new program from Claude waiting for Start program or Reject; null when none is. */
    val program: StateFlow<com.hyperlog.app.coach.ProgramProposal?> = _program.asStateFlow()

    /** Opens a new program from a check-in for review. */
    fun reviewProgram(proposal: com.hyperlog.app.coach.ProgramProposal) {
        _program.value = proposal
    }

    /** Drops the program under review; nothing changes. */
    fun rejectProgram() {
        _program.value = null
    }

    /**
     * Start program: the program under review runs from week 1, day 1, as an import, and its new
     * exercises join the library with it; then [started]. The one running before stays as a record
     * of its sessions. One the library no longer takes (it gained one of the new exercises since)
     * shows its problems as a rejected import does, and [failed] runs instead.
     */
    fun startProgram(started: () -> Unit, failed: () -> Unit) {
        val proposal = _program.value ?: return
        viewModelScope.launch {
            val result = programs.import(proposal.source, proposal.newExercises)
            _program.value = null
            if (result is ProgramParser.Result.Invalid) {
                _errors.value = result.errors
                failed()
            } else {
                started()
            }
        }
    }

    /**
     * Save for later: the program under review goes on the Programs list with its new exercises,
     * and the one running goes on; then [saved]. One the library no longer takes shows its
     * problems as a rejected import does, and [failed] runs instead.
     */
    fun saveProgram(saved: () -> Unit, failed: () -> Unit) {
        val proposal = _program.value ?: return
        // A second tap while it saves would save it twice.
        if (saving?.isActive == true) return
        saving = viewModelScope.launch {
            val result = programs.save(proposal.source, proposal.newExercises)
            _program.value = null
            if (result is ProgramParser.Result.Invalid) {
                _errors.value = result.errors
                failed()
            } else {
                saved()
            }
        }
    }

    private var saving: kotlinx.coroutines.Job? = null

    /** Every program held, newest first, for Program → Programs. */
    val listed: StateFlow<List<com.hyperlog.app.program.ListedProgram>> = programs.listed.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Starts saved program [id] in place of the one running, then [started]; a new exercise the
     * library gained since shows its problems on Program, after [failed].
     */
    fun startSaved(id: Long, started: () -> Unit, failed: () -> Unit) {
        viewModelScope.launch {
            val result = programs.start(id)
            _errors.value = (result as? ProgramParser.Result.Invalid)?.errors
            if (result is ProgramParser.Result.Invalid) failed() else started()
        }
    }

    /** Resume: program [id], replaced mid-way, picks up where it was. */
    fun resume(id: Long) {
        viewModelScope.launch { programs.resume(id) }
    }

    /** Run again: program [id] runs afresh from week 1, day 1. */
    fun runAgain(id: Long) {
        viewModelScope.launch { _errors.value = (programs.runAgain(id) as? ProgramParser.Result.Invalid)?.errors }
    }

    /** Deletes saved program [id]; one that has run stays. */
    fun deleteProgram(id: Long) {
        viewModelScope.launch { programs.delete(id) }
    }

    /** Writes program [id]'s file, with the exercises it adds to the shipped library, to [uri], picked in the system save dialog. */
    fun export(id: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val shipped = com.hyperlog.app.library.LibrarySeed.fromAssets(app).exercises.map { it.id }.toSet()
            val file = programs.exportFile(id, shipped) ?: return@launch
            runCatching { app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(file.toByteArray()) } }
        }
    }

    private val _workout = MutableStateFlow<com.hyperlog.app.coach.Workout?>(null)

    /** A workout from Claude waiting for Add to Today or Reject; null when none is. */
    val workout: StateFlow<com.hyperlog.app.coach.Workout?> = _workout.asStateFlow()

    /** Opens a workout from a check-in for review. */
    fun reviewWorkout(workout: com.hyperlog.app.coach.Workout) {
        _workout.value = workout
    }

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

    /** The workout last removed, for Undo; null once undone or with none. */
    private var removed: com.hyperlog.app.data.WorkoutEntity? = null

    /** Takes waiting workout [id] off Today, keeping it for [undoRemoveWorkout]. */
    fun removeWorkout(id: Long) {
        viewModelScope.launch { removed = workouts.remove(id) }
    }

    /** Puts the workout just removed back as it was. */
    fun undoRemoveWorkout() {
        viewModelScope.launch {
            val workout = removed ?: return@launch
            removed = null
            workouts.restore(workout)
        }
    }

    private val _lowest = MutableStateFlow<List<Pair<String, Double>>>(emptyList())

    /** The focus's marks on Coach: the groups trained in the last 4 weeks with the fewest sets this week. */
    val lowest: StateFlow<List<Pair<String, Double>>> = _lowest.asStateFlow()

    /** Reads the focus's marks again, as Coach opens. */
    fun refreshLowest() {
        viewModelScope.launch {
            val db = HyperlogDatabase.get(getApplication())
            _lowest.value = com.hyperlog.app.coach.WorkoutOptions.lowest(History(db, programs).weeklyGroupSets(AskClaude.SET_WEEKS))
        }
    }

    /** [text] read as a Change for the running program, a workout or a new program. */
    private suspend fun read(text: String): AskClaude.Reply {
        val active = programs.active.first()
        val library = HyperlogDatabase.get(getApplication()).exercises().all()
        val inForce = active?.inForce.orEmpty().map { it.id }.toSet()
        return withContext(Dispatchers.Default) {
            AskClaude.read(text, active?.program, library.map { it.id }.toSet(), inForce, names = library.associate { it.id to it.name })
        }
    }

    fun closePasteErrors() {
        _pasteErrors.value = null
    }

    private val _inForce = MutableStateFlow<Pair<Long, ChangeReview>?>(null)

    /** A Change in force opened from its card on Coach or its row on Program, read-only, with its id for Revert; null when none is open. */
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
            _inForce.value = id to ChangeReview(record.change, diff, active.program, block, checkIn = record.checkIn)
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
