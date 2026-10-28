package com.hyperlog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.hyperlog.app.rest.AppVisibility
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.gym.GymsScreen
import com.hyperlog.app.ui.gym.GymsViewModel
import com.hyperlog.app.ui.gym.LadderEditor
import com.hyperlog.app.ui.gym.ItemEditor
import com.hyperlog.app.ui.gym.LadderKind
import com.hyperlog.app.ui.library.ExerciseEditor
import com.hyperlog.app.ui.library.LibraryScreen
import com.hyperlog.app.ui.library.LibraryViewModel
import com.hyperlog.app.ui.program.ProgramScreen
import com.hyperlog.app.ui.program.ProgramView
import com.hyperlog.app.ui.coach.ChangeReviewScreen
import com.hyperlog.app.ui.coach.WorkoutReviewScreen
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.hyperlog.app.ui.coach.AskClaudeShare
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hyperlog.app.ui.program.ProgramViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hyperlog.app.ui.session.SessionDoneScreen
import com.hyperlog.app.ui.session.PastExerciseScreen
import com.hyperlog.app.ui.session.PastSessionScreen
import com.hyperlog.app.ui.session.PastSessionsViewModel
import com.hyperlog.app.ui.history.BodyweightScreen
import com.hyperlog.app.ui.history.ExerciseTrendScreen
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.history.HistoryViewModel
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SessionViewModel
import com.hyperlog.app.ui.settings.SettingsScreen
import com.hyperlog.app.ui.settings.StartingWeightsViewModel
import com.hyperlog.app.ui.settings.BackupState
import com.hyperlog.app.ui.settings.BackupViewModel
import com.hyperlog.app.ui.settings.ClaudeKeyViewModel
import android.app.Activity
import android.content.Intent
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hyperlog.app.ui.today.TodayScreen
import com.hyperlog.app.update.UpdateViewModel
import com.hyperlog.app.update.Updates

class MainActivity : ComponentActivity() {
    private val sessions: SessionViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    private val gyms: GymsViewModel by viewModels()
    private val programs: ProgramViewModel by viewModels()
    private val startingWeights: StartingWeightsViewModel by viewModels()
    private val past: PastSessionsViewModel by viewModels()
    private val history: HistoryViewModel by viewModels()
    private val backups: BackupViewModel by viewModels()
    private val updates: UpdateViewModel by viewModels()
    private val claudeKey: ClaudeKeyViewModel by viewModels()
    private val checkIns: com.hyperlog.app.ui.coach.CheckInViewModel by viewModels()

    /** Each tap on a check-in's notification, counted: Coach opens on it. */
    private val openCheckIn = kotlinx.coroutines.flow.MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) opened(intent)
        setContent {
            HyperlogTheme { App(sessions, library, gyms, programs, startingWeights, past, history, backups, updates, claudeKey, checkIns, openCheckIn) }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opened(intent)
    }

    /** A check-in's notification opens Coach, at the step the check-in is on. */
    private fun opened(intent: Intent?) {
        if (intent?.getBooleanExtra(com.hyperlog.app.coach.CheckInNotices.EXTRA_OPEN_CHECK_IN, false) == true) openCheckIn.value++
    }

    override fun onStart() {
        super.onStart()
        // Every return to the foreground checks for an update; the check reads GitHub at most every 15 minutes.
        updates.start(Updates.source(this))
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }
}

/**
 * Asks once for notifications (Android 13+), so rest shows on the lock screen. Without them rest
 * still buzzes when it runs out. With [once], it asks only the first time ever for that reason
 * (a check-in's: its reply ready while you're away); everything works without them.
 */
@Composable
private fun AskForNotifications(once: String? = null) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val prefs = context.getSharedPreferences("notifications", android.content.Context.MODE_PRIVATE)
        if (once != null && prefs.getBoolean(once, false)) return@LaunchedEffect
        if (!asked && !granted) {
            asked = true
            once?.let { prefs.edit().putBoolean(it, true).apply() }
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Screen { Today, Settings, Library, Gyms, Program, History, Coach }

internal const val NavigationBarTag = "navigation-bar"

/** The five destinations, in the bar's order, with their symbols (outlined, filled when selected). */
private val Tabs = listOf(
    Triple(Screen.Today, Symbol.Today, Symbol.TodayFill),
    Triple(Screen.Program, Symbol.ViewTimeline, Symbol.ViewTimelineFill),
    Triple(Screen.Coach, Symbol.Sports, Symbol.SportsFill),
    Triple(Screen.History, Symbol.History, Symbol.History),
    Triple(Screen.Settings, Symbol.Settings, Symbol.SettingsFill),
)

/**
 * Today, Program, Coach, History and Settings as a Material 3 Expressive short navigation bar, a tone above
 * the screens, with the selected item in a primary-container pill (docs/design/designer-brief.md).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavigationBar(selected: Screen, onSelect: (Screen) -> Unit) {
    val cs = MaterialTheme.colorScheme
    ShortNavigationBar(Modifier.testTag(NavigationBarTag), containerColor = Tones.bar) {
        Tabs.forEach { (tab, symbol, filled) ->
            val label = stringResource(
                when (tab) {
                    Screen.Program -> R.string.program_title
                    Screen.Coach -> R.string.coach_title
                    Screen.History -> R.string.history_title
                    Screen.Settings -> R.string.settings
                    else -> R.string.today_title
                },
            )
            ShortNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { SymbolIcon(if (tab == selected) filled else symbol, null) },
                label = { Text(label) },
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIndicatorColor = cs.primaryContainer,
                    selectedIconColor = cs.onPrimaryContainer,
                    selectedTextColorTopIconPosition = cs.onSurface,
                ),
            )
        }
    }
}

/** Marks a new exercise in the editor, which otherwise holds the id of the one being edited. */
private const val NewExercise = ""

/** Ladder editor targets besides an equipment item's id. Room ids start at 1. */
private const val NewItem = 0L
private const val DumbbellLadder = -1L

/**
 * Today, the running session, or the summary of the one just finished; settings and the
 * exercise library behind Today. The app opens on a running session; back leaves it running
 * and goes to Today.
 */
@Composable
private fun App(
    sessions: SessionViewModel,
    library: LibraryViewModel,
    gymsModel: GymsViewModel,
    programModel: ProgramViewModel,
    startingWeightsModel: StartingWeightsViewModel,
    pastModel: PastSessionsViewModel,
    historyModel: HistoryViewModel,
    backupModel: BackupViewModel,
    updateModel: UpdateViewModel,
    claudeKeyModel: ClaudeKeyViewModel,
    checkInModel: com.hyperlog.app.ui.coach.CheckInViewModel,
    openCheckIn: kotlinx.coroutines.flow.StateFlow<Int> = kotlinx.coroutines.flow.MutableStateFlow(0),
) {
    val loaded by sessions.loaded.collectAsStateWithLifecycle()
    val session by sessions.session.collectAsStateWithLifecycle()
    val summary by sessions.summary.collectAsStateWithLifecycle()
    val exercises by sessions.exercises.collectAsStateWithLifecycle()
    val goals by sessions.goals.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    val gyms by gymsModel.gyms.collectAsStateWithLifecycle()
    val bars by gymsModel.bars.collectAsStateWithLifecycle()
    val programSetups by gymsModel.programSetups.collectAsStateWithLifecycle()
    val usualGym by sessions.usualGym.collectAsStateWithLifecycle()
    val program by programModel.active.collectAsStateWithLifecycle()
    val weekdayPlan by programModel.weekdayPlan.collectAsStateWithLifecycle()
    val importErrors by programModel.errors.collectAsStateWithLifecycle()
    val pickProgram = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(programModel::import)
    }
    val startingWeights by startingWeightsModel.status.collectAsStateWithLifecycle()
    // Attach a spreadsheet, PDF or photo, on a check-in's step that offers it.
    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(checkInModel::attach)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(startingWeightsModel::import)
    }
    val backup by backupModel.state.collectAsStateWithLifecycle()
    val lastExport by backupModel.lastExport.collectAsStateWithLifecycle()
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(backupModel::export)
    }
    val listedPrograms by programModel.listed.collectAsStateWithLifecycle()
    /** The saved program whose page is up, opened from Programs or Today. */
    var savedOpen by rememberSaveable { mutableStateOf<Long?>(null) }
    /** The program whose file Export is saving, until the save dialog answers. */
    var exportingProgram by rememberSaveable { mutableStateOf<Long?>(null) }
    val exportProgram = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportingProgram
        exportingProgram = null
        if (uri != null && id != null) programModel.export(id, uri)
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backupModel::pick)
    }
    var gymId by rememberSaveable { mutableStateOf<Long?>(null) }
    val gymImportErrors by gymsModel.importErrors.collectAsStateWithLifecycle()
    val pickGym = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { gymsModel.importGym(it) { id -> gymId = id } }
    }
    /** The ladder being edited: an item id (0 = new item), or [DumbbellLadder]. */
    var ladder by rememberSaveable { mutableStateOf<Long?>(null) }
    val pastSession by pastModel.session.collectAsStateWithLifecycle()
    val pastSessions by pastModel.sessions.collectAsStateWithLifecycle()
    val weeks by historyModel.weeks.collectAsStateWithLifecycle()
    val trained by historyModel.exercises.collectAsStateWithLifecycle()
    val trend by historyModel.trend.collectAsStateWithLifecycle()
    val bodyweights by historyModel.bodyweights.collectAsStateWithLifecycle()
    var historyTab by rememberSaveable { mutableStateOf(0) }
    var bodyweightOpen by rememberSaveable { mutableStateOf(false) }
    // What Program shows, kept while a session opened from it is looked at in History or a review is up.
    var programView by rememberSaveable(stateSaver = ProgramView.Saver) { mutableStateOf(ProgramView()) }
    // The day picked on Today's calendar (epoch day; null: today), kept while a session opened from it is looked at.
    var todayDay by rememberSaveable { mutableStateOf<Long?>(null) }
    // Today's own saved state (each day's scroll), kept while another screen is up.
    val todayState = rememberSaveableStateHolder()
    // A session opened from Program or Today goes back there.
    var sessionFrom by rememberSaveable { mutableStateOf<Screen?>(null) }
    val lastTrained by sessions.lastTrained.collectAsStateWithLifecycle()
    val pasteErrors by programModel.pasteErrors.collectAsStateWithLifecycle()
    val reply by programModel.reply.collectAsStateWithLifecycle()
    /** What opened Ask Claude, the check-in without a key (null: closed), and its tile, if one did. */
    var askOpening by rememberSaveable { mutableStateOf<com.hyperlog.app.coach.Opening?>(null) }
    var askTile by rememberSaveable { mutableStateOf<com.hyperlog.app.coach.Tile?>(null) }
    /** The words for Claude: kept across tabs. */
    var askWords by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    /** Ask Claude was pressed for the words; an accept, a revert or cleared words end it. */
    var coachAsked by rememberSaveable { mutableStateOf(false) }
    /** A Change was just accepted from Coach: Today brings the program day it changed into view. */
    var showProgramDay by rememberSaveable { mutableStateOf(false) }
    val waitingWorkouts by programModel.waiting.collectAsStateWithLifecycle()
    val lowest by programModel.lowest.collectAsStateWithLifecycle()
    val workoutReview by programModel.workout.collectAsStateWithLifecycle()
    val programReview by programModel.program.collectAsStateWithLifecycle()
    val attachNote by checkInModel.attachNote.collectAsStateWithLifecycle()
    val endAsk = { coachAsked = false; programModel.clearReply() }
    /** Opens Ask Claude with [words] to edit, for the [opening] and its [tile]. */
    val openAsk = { opening: com.hyperlog.app.coach.Opening, tile: com.hyperlog.app.coach.Tile?, words: String ->
        askOpening = opening
        askTile = tile
        askWords = TextFieldValue(words, TextRange(words.length))
        endAsk()
        programModel.closePasteErrors()
    }
    /** Back, or done: the tiles again. */
    val closeAsk = { askOpening = null; askTile = null; endAsk(); programModel.closePasteErrors() }
    val inForce by programModel.inForce.collectAsStateWithLifecycle()
    val coachLine by programModel.coachLine.collectAsStateWithLifecycle()
    val dueReview by programModel.dueReview.collectAsStateWithLifecycle()
    val reviewPage by programModel.blockReview.collectAsStateWithLifecycle()
    // A session finished or corrected reads the list again, and the coach line with it.
    LaunchedEffect(pastSessions) { programModel.readCoachLine() }
    val update by updateModel.banner.collectAsStateWithLifecycle()
    val checkForUpdates by updateModel.checkForUpdates.collectAsStateWithLifecycle()
    val checkedNow by updateModel.checkedNow.collectAsStateWithLifecycle()
    val claudeKey by claudeKeyModel.status.collectAsStateWithLifecycle()
    // With a Claude key, or the scripted coach, a tile starts a check-in with Claude; without, it opens Ask Claude.
    val checkInAvailable by checkInModel.available.collectAsStateWithLifecycle()
    // A check-in whose first answer is given words (a day over its training day's minutes, the coach line): with Claude, the wizard; without, the words to share.
    val checkInWords = { words: String ->
        if (checkInAvailable) checkInModel.fit(words)
        else openAsk(com.hyperlog.app.coach.Opening.Tile, null, words)
        programModel.closePasteErrors()
        screen = Screen.Coach
    }
    val asking = askOpening != null && !checkInAvailable
    // A key saved (or the scripted coach switched on) while Ask Claude is open: the wizard takes over, and its words go.
    LaunchedEffect(checkInAvailable) { if (checkInAvailable && askOpening != null) { askOpening = null; askTile = null } }
    val checkIn by checkInModel.checkIn.collectAsStateWithLifecycle()
    val checkInDraft by checkInModel.draft.collectAsStateWithLifecycle()
    val scriptedCoach by checkInModel.scripted.collectAsStateWithLifecycle()
    /** The Changes in force page on Coach is open. */
    var coachInForce by rememberSaveable { mutableStateOf(false) }
    /** A new program was just saved for later: Coach says so once. */
    var programSaved by rememberSaveable { mutableStateOf(false) }
    /** A workout was added while a session runs: Today says so once. */
    var workoutAddedMidSession by rememberSaveable { mutableStateOf(false) }
    // Reverting the last one closes the page, so the tiles (and the bar) come back.
    val noneInForce = program?.inForce.isNullOrEmpty()
    LaunchedEffect(noneInForce) { if (noneInForce) coachInForce = false }
    val checkInStep = checkIn?.step
    val checkInProgress by checkInModel.progress.collectAsStateWithLifecycle()
    // A check-in's notification opens Coach, where the check-in is; a running session goes on, left as Leave leaves it.
    val openedCheckIn by openCheckIn.collectAsStateWithLifecycle()
    LaunchedEffect(openedCheckIn) {
        if (openedCheckIn > 0) {
            left = true
            screen = Screen.Coach
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A restored backup replaces everything the screens hold: start them again on its data.
    LaunchedEffect(backup) {
        if (backup == BackupState.Restored) (context as? Activity)?.let {
            it.startActivity(Intent(it, MainActivity::class.java))
            it.finish()
        }
    }
    if (!loaded) return

    val running = session
    val finished = summary
    // Time off counts from the last session finished: read again whenever Today or the coach shows,
    // and on start, when a session may already be running (a restart or a restore).
    LaunchedEffect(screen, running == null) { sessions.refreshLastTrained() }
    // Today's calendar marks the finished sessions: read again whenever Today shows or a session starts or ends.
    LaunchedEffect(screen, running == null, finished == null) { if (screen == Screen.Today) pastModel.refresh() }
    // The focus's marks count the sets trained so far this week.
    LaunchedEffect(screen == Screen.Coach) { if (screen == Screen.Coach) programModel.refreshLowest() }
    val review by programModel.review.collectAsStateWithLifecycle()
    // A check-in's Change, workout or program opens its review, the one a paste opens, on Coach:
    // one read back as the app starts waits there, rather than covering Today.
    LaunchedEffect(checkIn?.checkIn?.turns?.size, checkInStep?.let { it::class }, screen == Screen.Coach) {
        if (screen != Screen.Coach) return@LaunchedEffect
        when (val step = checkInStep) {
            is com.hyperlog.app.coach.CheckInStep.ChangeReview -> programModel.propose(step.change)
            is com.hyperlog.app.coach.CheckInStep.WorkoutReview -> programModel.reviewWorkout(step.workout)
            is com.hyperlog.app.coach.CheckInStep.ProgramReview -> programModel.reviewProgram(step.proposal)
            else -> Unit
        }
    }
    val reviewingCheckIn = checkInStep is com.hyperlog.app.coach.CheckInStep.ChangeReview || checkInStep is com.hyperlog.app.coach.CheckInStep.WorkoutReview ||
        checkInStep is com.hyperlog.app.coach.CheckInStep.ProgramReview
    if (running != null) AskForNotifications()
    // Its notifications say when Claude's reply is ready while you're away; asked at the first check-in only.
    if (checkIn != null) AskForNotifications(once = "check_in")
    // A Change under review covers whatever proposed it: the Program screen or the session.
    review?.let { r ->
        ChangeReviewScreen(
            r, exercises.associate { it.id to it.name },
            // A coach's Change, once accepted, is the plan Today shows.
            // An accepted Change keeps the check-in that made it, and the check-in ends.
            onAccept = {
                // A Change from the check-in, or pasted back after it handed off; not one a session proposes.
                val fromCheckIn = reviewingCheckIn || (checkInStep is com.hyperlog.app.coach.CheckInStep.Failed && screen == Screen.Coach && (running == null || left))
                // Ask Claude's words and tile are the check-in a Change from there keeps.
                val fromAsk = asking && screen == Screen.Coach
                val saved = when {
                    fromCheckIn -> checkInModel.saved()
                    fromAsk -> {
                        val words = askWords.text.trim()
                        val tile = askTile
                        com.hyperlog.app.coach.SavedCheckIn(tile?.words ?: words, if (tile != null && words.isNotEmpty()) listOf(com.hyperlog.app.coach.SavedCheckIn.Step(context.getString(R.string.checkin_ask_step), words)) else emptyList())
                    }
                    else -> null
                }
                programModel.accept(saved); endAsk()
                if (fromAsk) closeAsk()
                if (fromCheckIn) checkInModel.close()
                if (screen == Screen.Coach) { screen = Screen.Today; showProgramDay = true }
            },
            // Reject is Back for a check-in: the step before shows again, to change its answer.
            onReject = { programModel.reject(); if (reviewingCheckIn) checkInModel.reject() },
            // × drops the check-in, as on every one of its steps.
            onClose = if (reviewingCheckIn) ({ programModel.reject(); checkInModel.close() }) else null,
        )
        return
    }
    // A workout's date is the program's today, or the phone's with no program.
    val todayWorkout = waitingWorkouts.firstOrNull { it.addedOn == (program?.today ?: java.time.LocalDate.now()) }
    // A check-in's workout starts at the gym answered in it (none: Elsewhere); a pasted one at the usual gym.
    val workoutGym = checkIn?.checkIn?.let { it.gym ?: if (it.elsewhere) null else usualGym?.id } ?: usualGym?.id
    // A workout from Claude, before it goes on Today.
    workoutReview?.let { w ->
        WorkoutReviewScreen(
            w, exercises.associate { it.id to it.name },
            onAdd = {
                programModel.addWorkout(workoutGym); closeAsk(); screen = Screen.Today
                if (checkIn != null) checkInModel.close()
                // Today shows the running session, not the workout: say where it went.
                if (running != null) workoutAddedMidSession = true
            },
            onReject = { programModel.rejectWorkout(); if (reviewingCheckIn) checkInModel.reject() },
            onClose = if (reviewingCheckIn) ({ programModel.rejectWorkout(); checkInModel.close() }) else null,
            replaces = todayWorkout?.workout?.name,
            gym = if (gyms.isEmpty()) null else gyms.firstOrNull { it.id == workoutGym }?.name?.let { context.getString(R.string.today_workout_at, it) }
                ?: context.getString(R.string.coach_elsewhere),
        )
        return
    }
    // A new program from Claude, before it runs.
    programReview?.let { p ->
        com.hyperlog.app.ui.coach.ProgramReviewScreen(
            p, exercises.associate { it.id to it.name }, program,
            // Start program goes to Today on the new program's first day, and the check-in ends;
            // one the library no longer takes goes to Program, which shows its problems.
            onStart = {
                programModel.startProgram(
                    started = {
                        closeAsk(); screen = Screen.Today
                        if (checkIn != null) checkInModel.close()
                    },
                    failed = { closeAsk(); if (checkIn != null) checkInModel.close(); programView = ProgramView(); screen = Screen.Program },
                )
            },
            onReject = { programModel.rejectProgram(); if (reviewingCheckIn) checkInModel.reject() },
            onClose = if (reviewingCheckIn) ({ programModel.rejectProgram(); checkInModel.close() }) else null,
            file = checkIn?.checkIn?.file.takeIf { reviewingCheckIn },
            fit = com.hyperlog.app.ui.coach.ReviewFit(weekdayPlan, gyms, exercises, java.time.LocalDate.now()),
            // Save for later ends the check-in on Coach, which says where it went; the program running goes on.
            onSave = {
                programModel.saveProgram(
                    saved = { closeAsk(); if (checkIn != null) checkInModel.close(); programSaved = true; screen = Screen.Coach },
                    failed = { closeAsk(); if (checkIn != null) checkInModel.close(); programView = ProgramView(); screen = Screen.Program },
                )
            },
        )
        return
    }
    // A saved program's page, read-only with Start program; once started it's no longer saved, and the page goes.
    listedPrograms.firstOrNull { it.id == savedOpen && it.status is com.hyperlog.app.program.ListedProgram.Status.Saved }?.let { p ->
        com.hyperlog.app.ui.program.SavedProgramScreen(
            p, exercises.associate { it.id to it.name }, program,
            // Started, it runs where it was opened from (Today, or Programs); one the library no longer takes goes to Program.
            onStart = {
                programModel.startSaved(
                    p.id,
                    started = { savedOpen = null },
                    failed = { savedOpen = null; programView = ProgramView(); screen = Screen.Program },
                )
            },
            onExport = { exportingProgram = p.id; exportProgram.launch(com.hyperlog.app.ui.program.programFileName(p.title)) },
            onDelete = { savedOpen = null; programModel.deleteProgram(p.id) },
            onBack = { savedOpen = null },
        )
        return
    }
    // A finished block's review, from its card on Program, Today's line or a past program on Programs.
    reviewPage?.let { page ->
        com.hyperlog.app.ui.program.BlockReviewScreen(
            page, exercises.associate { it.id to it.name },
            onBack = programModel::closeReview,
            onCheckIn = { programModel.closeReview(); checkInWords(it) },
            onNewProgram = { programModel.closeReview(); checkInWords(it) },
        )
        return
    }
    // A Change in force opened from its card on Coach or its row on Program: what it does, read-only, with Revert.
    inForce?.let { (id, r) ->
        ChangeReviewScreen(
            r, exercises.associate { it.id to it.name },
            onAccept = {},
            onReject = programModel::closeInForce,
            onRevert = { programModel.revert(id); programModel.closeInForce(); endAsk() },
        )
        return
    }
    // Session done reads the finished session back, so a correction made from it shows at once.
    LaunchedEffect(finished?.sessionId) { finished?.let { pastModel.open(it.sessionId) } }
    val done = pastSession?.takeIf { it.id == finished?.sessionId }
    val sessionShown = finished == null && running != null && !left
    // The navigation bar is for the five destinations themselves; a session or a sub-screen hides it.
    val tabShown = finished == null && !sessionShown && editing == null && when (screen) {
        Screen.Today, Screen.Settings -> true
        // A check-in hides the bar, as a session does (owner, 2026-10-24); Changes in force is a sub-screen.
        Screen.Coach -> !(asking || (checkInAvailable && (checkIn != null)) || (coachInForce && !noneInForce))
        Screen.Program -> !programView.changesOpen && !programView.programsOpen
        Screen.History -> pastSession == null && !bodyweightOpen && trend == null
        else -> false
    }
    Scaffold(
        bottomBar = {
            if (tabShown) AppNavigationBar(screen) { tab ->
                when (tab) {
                    Screen.History -> { pastModel.refresh(); historyModel.refresh() }
                    Screen.Coach -> programModel.closePasteErrors()
                    else -> Unit
                }
                // Chosen from the bar, Program opens on where you are.
                if (tab == Screen.Program && screen != Screen.Program) programView = ProgramView()
                // And Today on today.
                if (tab == Screen.Today && screen != Screen.Today) todayDay = null
                screen = tab
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { bar ->
    Box(Modifier.fillMaxSize().padding(bar).consumeWindowInsets(bar)) {
    when {
        finished != null && done?.shown != null -> PastExerciseScreen(done, pastModel, onBack = { pastModel.show(null) })
        finished != null -> SessionDoneScreen(
            done?.summary ?: finished,
            onResume = { pastModel.close(); sessions.resumeFinished(); left = false },
            onClose = { pastModel.close(); sessions.dismissSummary(); left = false },
            onOpen = done?.let { d -> { i: Int -> pastModel.show(d.exercises[i].id) } },
        )
        running != null && !left -> SessionScreen(
            running,
            exercises,
            actions = sessions,
            onLeave = { left = true },
            onKeepEdits = programModel::propose,
            goals = goals,
        )
        editing != null -> {
            val id = editing
            val exercise = exercises.firstOrNull { it.id == id }
            ExerciseEditor(
                exercise,
                onSave = { library.save(it, isNew = exercise == null); editing = null },
                onSetHidden = { hidden -> exercise?.let { library.setHidden(it.id, hidden) } },
                onClose = { editing = null },
            )
        }
        screen == Screen.Gyms && gymId != null && gyms.any { it.id == gymId } -> {
            val gym = gyms.first { it.id == gymId }
            val itemId = ladder
            if (itemId == DumbbellLadder) {
                LadderEditor(
                    title = stringResource(R.string.gym_dumbbells),
                    name = null,
                    spec = gym.dumbbells,
                    kinds = listOf(LadderKind.List, LadderKind.Stack),
                    readable = true,
                    onSave = { _, spec -> gymsModel.setDumbbells(gym.id, spec); ladder = null },
                    onDelete = null,
                    onClose = { ladder = null },
                )
            } else if (itemId != null) {
                // A machine, or a bar from an imported gym file.
                val item = (gym.items + gym.kit).firstOrNull { it.id == itemId }
                ItemEditor(
                    gym, item,
                    onSave = { gymsModel.saveItem(it); ladder = null },
                    onDelete = item?.let { { gymsModel.deleteItem(it.id); ladder = null } },
                    onClose = { ladder = null },
                )
            } else {
                GymScreen(
                    gym,
                    onRename = { gymsModel.renameGym(gym.id, it) },
                    onDelete = { gymsModel.deleteGym(gym.id); gymId = null },
                    onOpenItem = { ladder = it },
                    onAddItem = { ladder = NewItem },
                    onBack = { gymId = null },
                    exerciseNames = exercises.associate { it.id to it.name },
                    onRestore = { gymsModel.unmarkMissing(gym.id, it) },
                    onPinPlates = { gymsModel.setPinPlates(gym.id, it) },
                    onDumbbells = { ladder = DumbbellLadder },
                    onPlates = { gymsModel.setPlates(gym.id, it) },
                    onRenameItem = gymsModel::renameItem,
                    onDeleteItem = { gymsModel.deleteItem(it.id) },
                    programSetup = programSetups?.second?.get(gym.id),
                    programTitle = programSetups?.first,
                    onAddProgramEquipment = { gymsModel.addProgramEquipment(gym.id, it) },
                )
            }
        }
        screen == Screen.Gyms -> GymsScreen(
            gyms, bars,
            onAddGym = { name -> gymsModel.addGym(name) { gymId = it } },
            onOpenGym = { gymId = it },
            onBar = gymsModel::setBar,
            onBack = { screen = Screen.Settings },
            onImport = { gymsModel.closeImportErrors(); pickGym.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            importErrors = gymImportErrors,
            onCloseImportErrors = gymsModel::closeImportErrors,
        )
        screen == Screen.Library -> LibraryScreen(
            exercises,
            query = libraryQuery,
            onQuery = { libraryQuery = it },
            onOpen = { editing = it },
            onNew = { editing = NewExercise },
            onBack = { screen = Screen.Settings; libraryQuery = "" },
        )
        screen == Screen.Program -> {
            // Done days show what was logged, so the list is read as Program opens.
            LaunchedEffect(Unit) { pastModel.refresh() }
            ProgramScreen(
            program,
            importErrors,
            onImport = { programModel.closeErrors(); pickProgram.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onCloseErrors = programModel::closeErrors,
            onBack = { screen = Screen.Today },
            starter = programModel.starter,
            onRunStarter = programModel::runStarter,
            onPropose = programModel::propose,
            onRevert = programModel::revert,
            onOpenChange = programModel::openInForce,
            exerciseNames = exercises.associate { it.id to it.name },
            sessions = pastSessions.orEmpty(),
            onOpenSession = { id -> pastModel.open(id); sessionFrom = Screen.Program; screen = Screen.History },
            view = programView,
            onView = { programView = it },
            weekdayPlan = weekdayPlan,
            gyms = gyms,
            library = exercises,
            onCheckInFit = checkInWords,
            onWeekdayPlan = programModel::setWeekdayPlan,
            listedPrograms = listedPrograms,
            onRunAgain = programModel::runAgain,
            onResume = programModel::resume,
            onOpenSaved = { savedOpen = it },
            onExportProgram = { exportingProgram = it.id; exportProgram.launch(com.hyperlog.app.ui.program.programFileName(it.title)) },
            onReview = { block -> program?.let { programModel.openReview(it.id, block) } },
            onProgramReview = { id -> programModel.openReview(id, null) },
            )
        }
        screen == Screen.Coach && asking -> com.hyperlog.app.ui.coach.AskClaudeScreen(
            title = askTile?.let { stringResource(com.hyperlog.app.ui.coach.TileLooks.getValue(it).label) } ?: when (askOpening) {
                com.hyperlog.app.coach.Opening.HowsItGoing -> stringResource(R.string.checkin_title_hows_it_going)
                com.hyperlog.app.coach.Opening.EaseBackIn -> stringResource(R.string.checkin_title_ease_back)
                else -> stringResource(R.string.checkin_title)
            },
            words = askWords,
            onWords = { askWords = it },
            onBack = closeAsk,
            tileAsked = askTile != null,
            asked = coachAsked,
            onAskedChange = { if (it) coachAsked = true else endAsk() },
            onAskClaude = { words ->
                scope.launch {
                    val prompt = programModel.askClaudePrompt(words, null, false, lastTrained, weekdayPlan.days, tile = askTile)
                    context.startActivity(AskClaudeShare.intent(context, prompt))
                }
            },
            onPaste = { programModel.paste(AskClaudeShare.clipboard(context)) },
            pasteErrors = pasteErrors,
            onClosePasteErrors = programModel::closePasteErrors,
            onCopyProblems = { AskClaudeShare.copy(context, "Hyperlog", it.joinToString("\n")) },
            replyReady = reply != null,
            onCheckReply = { programModel.checkReply(AskClaudeShare.clipboard(context)) },
            onReview = programModel::reviewReply,
        )
        screen == Screen.Coach -> com.hyperlog.app.ui.coach.CheckInScreen(
            checkIn, program,
            onTile = { tile ->
                if (checkInAvailable) checkInModel.start(tile)
                else openAsk(com.hyperlog.app.coach.Opening.Tile, tile, context.getString(com.hyperlog.app.ui.coach.TileAskWords.getValue(tile)))
            },
            onAnswer = checkInModel::answer,
            draft = checkInDraft,
            onDraft = checkInModel::draft,
            onLeave = { screen = Screen.Today },
            onBack = checkInModel::back,
            onClose = { checkInModel.close(); programModel.closePasteErrors() },
            onTellMeMore = checkInModel::tellMeMore,
            onTryAgain = checkInModel::tryAgain,
            progress = checkInProgress,
            lowest = lowest,
            inForceOpen = coachInForce,
            onInForceOpen = { coachInForce = it },
            onOpenChange = programModel::openInForce,
            onRevert = { programModel.revert(it); endAsk() },
            savedNotice = programSaved,
            onSavedNoticeShown = { programSaved = false },
            onOpenPrograms = { programView = ProgramView(programsOpen = true); screen = Screen.Program },
            onHowItsGoing = {
                if (checkInAvailable) checkInModel.howItsGoing(it)
                else openAsk(com.hyperlog.app.coach.Opening.HowsItGoing, null, com.hyperlog.app.coach.CheckInContext.howItsGoing(it))
            },
            // The key refused or the limit hit: this check-in goes to the Claude app, and its reply is pasted back.
            onAskClaude = {
                scope.launch {
                    val words = checkInModel.saved()?.text().orEmpty()
                    val gym = gyms.firstOrNull { it.id == checkIn?.checkIn?.gym }
                    val prompt = programModel.askClaudePrompt(words, gym, checkIn?.checkIn?.elsewhere == true, lastTrained, weekdayPlan.days)
                    context.startActivity(AskClaudeShare.intent(context, prompt))
                }
            },
            onPaste = { programModel.paste(AskClaudeShare.clipboard(context)) },
            pasteErrors = pasteErrors,
            onCopyProblems = { AskClaudeShare.copy(context, "Hyperlog", it.joinToString("\n")) },
            onAttach = { pickAttachment.launch(com.hyperlog.app.coach.Attachments.PickerTypes) },
            // A check-in read back without its file asks for it again.
            attachNote = attachNote ?: checkIn?.checkIn?.lostFile?.let { context.getString(R.string.checkin_attach_again, it) },
        )
        screen == Screen.History && pastSession?.shown != null -> PastExerciseScreen(pastSession!!, pastModel, onBack = { pastModel.show(null) })
        // A session opened from History or a trend goes back there, read again with any correction.
        screen == Screen.History && pastSession != null -> PastSessionScreen(
            pastSession!!,
            onOpen = pastModel::show,
            onBack = {
                pastModel.close(); pastModel.refresh(); historyModel.refresh()
                sessionFrom?.let { screen = it; sessionFrom = null }
            },
        )
        screen == Screen.History && bodyweightOpen -> BodyweightScreen(
            bodyweights,
            onLog = historyModel::logBodyweight,
            onDelete = historyModel::deleteBodyweight,
            onBack = { bodyweightOpen = false },
        )
        screen == Screen.History && trend != null -> ExerciseTrendScreen(
            trend!!,
            hasBodyweight = bodyweights.isNotEmpty(),
            onOpenSession = pastModel::open,
            onBack = historyModel::closeTrend,
            onSetGoal = historyModel::setGoal,
            onClearGoal = historyModel::clearGoal,
        )
        screen == Screen.History -> HistoryScreen(
            pastSessions, weeks, trained, bodyweights,
            tab = historyTab,
            onTab = { historyTab = it },
            onOpenSession = pastModel::open,
            onOpenExercise = historyModel::openTrend,
            onBodyweight = { bodyweightOpen = true },
            onLogBodyweight = historyModel::logBodyweight,
            onBack = { screen = Screen.Today },
        )
        screen == Screen.Settings -> SettingsScreen(
            gymCount = gyms.size,
            exerciseCount = exercises.count { !it.hidden },
            onGyms = { screen = Screen.Gyms; gymId = null; ladder = null },
            onLibrary = { screen = Screen.Library },
            onBack = { screen = Screen.Today },
            startingWeights = startingWeights,
            onImportStartingWeights = { pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            backup = backup,
            lastExport = lastExport,
            onExport = { exportBackup.launch(backupModel.fileName()) },
            onRestore = { pickRestore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onConfirmRestore = backupModel::restore,
            onDismissBackup = backupModel::dismiss,
            checkForUpdates = checkForUpdates,
            onCheckForUpdates = updateModel::setCheckForUpdates,
            onCheckNow = updateModel::checkNow,
            checkedNow = checkedNow,
            version = BuildConfig.VERSION_NAME,
            claudeKey = claudeKey,
            scriptedCoach = scriptedCoach.takeIf { com.hyperlog.app.coach.BuildCoach.SCRIPTED },
            onScriptedCoach = checkInModel::setScripted,
            onSaveKey = claudeKeyModel::save,
            onRemoveKey = claudeKeyModel::remove,
            onDismissKey = claudeKeyModel::dismiss,
        )
        else -> todayState.SaveableStateProvider("today") { TodayScreen(
            sessionRunning = running != null,
            workoutAddedNotice = workoutAddedMidSession,
            onWorkoutAddedNoticeShown = { workoutAddedMidSession = false },
            onResume = { left = false },
            onStart = { gym, adHoc, swaps ->
                left = false
                sessions.start(gym, adHoc, swaps)
            },
            onTrainMissed = { gym, swaps ->
                left = false
                sessions.startMissed(gym, swaps)
            },
            gyms = gyms,
            usualGym = usualGym,
            program = program,
            exerciseNames = exercises.associate { it.id to it.name },
            onProgram = { screen = Screen.Program },
            starter = programModel.starter,
            onRunStarter = programModel::runStarter,
            savedPrograms = listedPrograms.filter { it.status is com.hyperlog.app.program.ListedProgram.Status.Saved },
            onOpenSaved = { savedOpen = it },
            library = exercises,
            lastTrained = lastTrained,
            onEaseBack = { days ->
                val words = context.getString(R.string.coach_ease_back_words, days)
                // With Claude, a check-in whose first answer is the time off; without, the words to share.
                if (checkInAvailable) checkInModel.easeBackIn(words)
                else openAsk(com.hyperlog.app.coach.Opening.EaseBackIn, null, words)
                programModel.closePasteErrors()
                screen = Screen.Coach
            },
            onCheckInFit = checkInWords,
            coachLine = coachLine,
            onCheckInLine = checkInWords,
            onHideCoachLine = programModel::hideCoachLine,
            dueReview = dueReview,
            onOpenReview = { programModel.openReview(it.programId, it.blockId) },
            weekdayPlan = weekdayPlan,
            onWeekdayPlan = programModel::setWeekdayPlan,
            sessions = pastSessions,
            onOpenSession = { id -> pastModel.open(id); sessionFrom = Screen.Today; screen = Screen.History },
            update = update,
            onUpdate = { updateModel.install(context) },
            onDismissUpdate = updateModel::dismiss,
            runningDay = running?.day,
            runningWorkout = running?.workoutId != null,
            workout = todayWorkout,
            onStartWorkout = { id, gym, swaps ->
                left = false
                sessions.startWorkout(id, gym, swaps)
            },
            onRemoveWorkout = programModel::removeWorkout,
            onUndoRemoveWorkout = programModel::undoRemoveWorkout,
            showProgramDay = showProgramDay,
            onProgramDayShown = { showProgramDay = false },
            selectedDay = todayDay,
            onSelectDay = { todayDay = it },
        ) }
    }
    }
    }
}
