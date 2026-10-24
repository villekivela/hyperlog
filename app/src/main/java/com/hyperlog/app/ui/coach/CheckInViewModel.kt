package com.hyperlog.app.ui.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.coach.AskClaude
import com.hyperlog.app.coach.BuildCoach
import com.hyperlog.app.coach.CheckInCoach
import com.hyperlog.app.coach.CheckInContext
import com.hyperlog.app.coach.CheckInFrame
import com.hyperlog.app.coach.CheckInRunner
import com.hyperlog.app.coach.CheckInStore
import com.hyperlog.app.coach.CheckInView
import com.hyperlog.app.coach.CheckInWorld
import com.hyperlog.app.coach.ClaudeClient
import com.hyperlog.app.coach.ClaudeKeys
import com.hyperlog.app.coach.CoachCost
import com.hyperlog.app.coach.CoachModel
import com.hyperlog.app.coach.MeteredCoach
import com.hyperlog.app.coach.SavedCheckIn
import com.hyperlog.app.coach.ScriptedWorld
import com.hyperlog.app.coach.Tile
import com.hyperlog.app.coach.WorkoutOptions
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.history.History
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.session.PastSessionEditor
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.week.WeekdayPlans
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The Coach tab's check-in with Claude (SPEC, Coaching → Check-in): through the Claude key, or the
 * scripted coach in a debug build with it switched on. Without either, Coach keeps the Ask Claude
 * screen. The check-in in progress is kept in `no_backup`, so it survives leaving the app.
 */
class CheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val db = HyperlogDatabase.get(application)
    private val programs = ProgramStore.get(application)
    private val keys = ClaudeKeys.get(application)
    private val cost = CoachCost(db)

    /**
     * Settings → Scripted coach (debug builds only) is on: a row in `setting`, which a release build
     * never reads.
     */
    val scripted: StateFlow<Boolean> = db.settings().observe(SCRIPTED_COACH).map { BuildCoach.SCRIPTED && it == "on" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setScripted(on: Boolean) {
        if (!BuildCoach.SCRIPTED) return
        viewModelScope.launch { db.settings().put(SettingEntity(SCRIPTED_COACH, if (on) "on" else "off")) }
    }

    /** A check-in can ask Claude: there's a key, or the scripted coach is on. */
    val available: StateFlow<Boolean> = combine(keys.ending, scripted) { ending, scripted -> ending != null || scripted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, keys.ending.value != null)

    private var client: Pair<String, CoachModel>? = null

    private suspend fun model(): CoachModel? {
        // Read from the table, not the flow: a check-in restored as the app starts asks before the flow has a value.
        if (BuildCoach.SCRIPTED && db.settings().get(SCRIPTED_COACH) == "on") return BuildCoach.scripted(::scriptedWorld)
        val key = withContext(Dispatchers.IO) { keys.key() } ?: return null
        client?.takeIf { it.first == key }?.let { return it.second }
        return MeteredCoach(ClaudeClient(key), cost).also { client = key to it }
    }

    private suspend fun library() = db.exercises().all().map { it.toExercise() }

    private val merged by lazy { runCatching { LibrarySeed.fromAssets(application).merged }.getOrDefault(emptyMap()) }

    private suspend fun lowest(): List<Pair<String, Double>> =
        WorkoutOptions.lowest(History(db, programs).weeklyGroupSets(AskClaude.SET_WEEKS))

    private suspend fun world(): CheckInWorld {
        val active = programs.active.first()
        val library = library()
        return CheckInWorld(
            active?.program, library.map { it.id }.toSet(), active?.inForce.orEmpty().map { it.id }.toSet(),
            Gyms(db).gyms.first(), library.associate { it.id to it.name }, merged,
        )
    }

    private suspend fun frame(): CheckInFrame {
        val active = programs.active.first()
        val library = library()
        val lastTrained = db.sessions().lastTrained()?.let(programs::dateOf)
        return withContext(Dispatchers.Default) {
            CheckInContext.frame(
                active, PastSessionEditor(db, programs).sessions(), library, Gyms(db).gyms.first(), lowest(),
                lastTrained = lastTrained, weekdays = WeekdayPlans(db).plan.first().days,
                weeklySets = History(db, programs).weeklySets(AskClaude.SET_WEEKS),
            )
        }
    }

    private suspend fun scriptedWorld() = ScriptedWorld(programs.active.first(), library(), Gyms(db).gyms.first(), lowest())

    private val runner = CheckInRunner(
        viewModelScope, CheckInStore(File(application.noBackupFilesDir, "check_in.json")),
        model = ::model, world = ::world, frame = ::frame,
    )

    init {
        runner.restore()
    }

    /** The check-in under way, with the step it shows; null on the tiles. */
    val checkIn: StateFlow<CheckInView?> = runner.state
    val thinking: StateFlow<Boolean> = runner.thinking

    fun start(tile: Tile) {
        _draft.value = null
        runner.start(tile)
    }

    /** Ease back in: a check-in whose first answer is the time off. */
    fun easeBackIn(words: String) {
        _draft.value = null
        runner.start(words, com.hyperlog.app.coach.Opening.EaseBackIn)
    }

    /** How's it going? about a Change in force: it opens with the Change and the check-in that made it. */
    fun howItsGoing(record: com.hyperlog.app.program.ChangeRecord) {
        _draft.value = null
        runner.start(CheckInContext.howItsGoing(record), com.hyperlog.app.coach.Opening.HowsItGoing)
    }

    private val _draft = MutableStateFlow<String?>(null)

    /**
     * Type my own's words while it's open (null: closed). Kept here so a half-typed answer survives
     * a tab switch; cleared when an answer goes or the check-in ends.
     */
    val draft: StateFlow<String?> = _draft.asStateFlow()

    fun draft(text: String?) {
        _draft.value = text
    }

    fun answer(text: String) {
        _draft.value = null
        _attachNote.value = null
        runner.answer(text)
    }

    private val _attachNote = MutableStateFlow<String?>(null)

    /** What became of the file picked for Attach a PDF or photo, under Attach: being read, or why it can't go. */
    val attachNote: StateFlow<String?> = _attachNote.asStateFlow()

    /** Attach a PDF or photo: the file at [uri] is read into memory, never stored, and goes to Claude as the answer. */
    fun attach(uri: android.net.Uri) {
        val app = getApplication<Application>()
        _attachNote.value = app.getString(com.hyperlog.app.R.string.checkin_attach_reading)
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { com.hyperlog.app.coach.Attachments.read(app.contentResolver, uri) }
            _attachNote.value = when (read) {
                is com.hyperlog.app.coach.Attachments.Read.Ready -> null.also { _draft.value = null; runner.attach(read.attachment) }
                com.hyperlog.app.coach.Attachments.Read.TooLarge ->
                    app.getString(com.hyperlog.app.R.string.checkin_attach_too_large, com.hyperlog.app.coach.Attachments.MAX_PDF_BYTES / (1024 * 1024))
                com.hyperlog.app.coach.Attachments.Read.Unreadable -> app.getString(com.hyperlog.app.R.string.checkin_attach_unreadable)
            }
        }
    }

    fun tellMeMore() {
        _draft.value = null
        runner.tellMeMore()
    }

    fun tryAgain() = runner.tryAgain()

    /** Back: a question you answered in your own words opens Type my own with them again. */
    fun back() {
        _attachNote.value = null
        _draft.value = checkIn.value?.checkIn?.let(CheckInCoach::ownAnswer)
        runner.back()
    }

    /** Reject on a review: the step before, with Type my own open (your own words, if you typed them) to say what was wrong. */
    fun reject() {
        _draft.value = checkIn.value?.checkIn?.let(CheckInCoach::ownAnswer).orEmpty()
        runner.back()
    }

    fun close() {
        _draft.value = null
        _attachNote.value = null
        runner.close()
    }

    /** The check-in as an accepted Change keeps it. */
    fun saved(): SavedCheckIn? = checkIn.value?.checkIn?.let(com.hyperlog.app.coach.CheckInCoach::saved)

    private companion object {
        const val SCRIPTED_COACH = "scripted_coach"
    }
}
