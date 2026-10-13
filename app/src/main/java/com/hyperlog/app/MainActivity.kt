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
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.ui.gym.GymScreen
import com.hyperlog.app.ui.gym.GymsScreen
import com.hyperlog.app.ui.gym.GymsViewModel
import com.hyperlog.app.ui.gym.LadderEditor
import com.hyperlog.app.ui.gym.LadderKind
import com.hyperlog.app.ui.library.ExerciseEditor
import com.hyperlog.app.ui.library.LibraryScreen
import com.hyperlog.app.ui.library.LibraryViewModel
import com.hyperlog.app.ui.program.ProgramScreen
import com.hyperlog.app.ui.coach.ChangeReviewScreen
import com.hyperlog.app.ui.coach.CoachScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updates.start(Updates.source(this))
        setContent {
            HyperlogTheme { App(sessions, library, gyms, programs, startingWeights, past, history, backups, updates) }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }
}

/**
 * Asks once for notifications (Android 13+), so rest shows on the lock screen. Without them rest
 * still buzzes when it runs out.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!asked && !granted) {
            asked = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Screen { Today, Settings, Library, Gyms, Program, History, Coach }

internal const val NavigationBarTag = "navigation-bar"

/** The four destinations, in the bar's order, with their symbols (outlined, filled when selected). */
private val Tabs = listOf(
    Triple(Screen.Today, Symbol.Today, Symbol.TodayFill),
    Triple(Screen.Program, Symbol.ViewTimeline, Symbol.ViewTimelineFill),
    Triple(Screen.Coach, Symbol.Sports, Symbol.SportsFill),
    Triple(Screen.History, Symbol.History, Symbol.History),
)

/**
 * Today, Program, Coach and History as a Material 3 Expressive short navigation bar, a tone above
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
private const val PlateLadder = -2L

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
) {
    val loaded by sessions.loaded.collectAsStateWithLifecycle()
    val session by sessions.session.collectAsStateWithLifecycle()
    val summary by sessions.summary.collectAsStateWithLifecycle()
    val exercises by sessions.exercises.collectAsStateWithLifecycle()
    val goals by sessions.goals.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    /** The destination Settings was opened from, which back returns to. */
    var settingsFrom by rememberSaveable { mutableStateOf(Screen.Today) }
    val openSettings = { settingsFrom = screen; screen = Screen.Settings }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    val gyms by gymsModel.gyms.collectAsStateWithLifecycle()
    val dumbbells by gymsModel.dumbbells.collectAsStateWithLifecycle()
    val plates by gymsModel.plates.collectAsStateWithLifecycle()
    val bars by gymsModel.bars.collectAsStateWithLifecycle()
    val usualGym by sessions.usualGym.collectAsStateWithLifecycle()
    val program by programModel.active.collectAsStateWithLifecycle()
    val importErrors by programModel.errors.collectAsStateWithLifecycle()
    val pickProgram = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(programModel::import)
    }
    val startingWeights by startingWeightsModel.status.collectAsStateWithLifecycle()
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(startingWeightsModel::import)
    }
    val backup by backupModel.state.collectAsStateWithLifecycle()
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(backupModel::export)
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backupModel::pick)
    }
    var gymId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** The ladder being edited: an item id (0 = new item), or [DumbbellLadder] / [PlateLadder]. */
    var ladder by rememberSaveable { mutableStateOf<Long?>(null) }
    val pastSession by pastModel.session.collectAsStateWithLifecycle()
    val pastSessions by pastModel.sessions.collectAsStateWithLifecycle()
    val weeks by historyModel.weeks.collectAsStateWithLifecycle()
    val trained by historyModel.exercises.collectAsStateWithLifecycle()
    val trend by historyModel.trend.collectAsStateWithLifecycle()
    val bodyweights by historyModel.bodyweights.collectAsStateWithLifecycle()
    var historyTab by rememberSaveable { mutableStateOf(0) }
    var bodyweightOpen by rememberSaveable { mutableStateOf(false) }
    val lastTrained by sessions.lastTrained.collectAsStateWithLifecycle()
    val pasteErrors by programModel.pasteErrors.collectAsStateWithLifecycle()
    val update by updateModel.banner.collectAsStateWithLifecycle()
    val checkForUpdates by updateModel.checkForUpdates.collectAsStateWithLifecycle()
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
    val review by programModel.review.collectAsStateWithLifecycle()
    if (running != null) AskForNotifications()
    // A Change under review covers whatever proposed it: the Program screen or the session.
    review?.let { r ->
        ChangeReviewScreen(
            r, exercises.associate { it.id to it.name },
            // A coach's Change, once accepted, is the plan Today shows.
            onAccept = { programModel.accept(); if (screen == Screen.Coach) screen = Screen.Today },
            onReject = programModel::reject,
        )
        return
    }
    // Session done reads the finished session back, so a correction made from it shows at once.
    LaunchedEffect(finished?.sessionId) { finished?.let { pastModel.open(it.sessionId) } }
    val done = pastSession?.takeIf { it.id == finished?.sessionId }
    val sessionShown = finished == null && running != null && !left
    // The navigation bar is for the four destinations themselves; a session, a sub-screen or Settings hides it.
    val tabShown = finished == null && !sessionShown && editing == null && when (screen) {
        Screen.Today, Screen.Program, Screen.Coach -> true
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
            sharedPlates = plates,
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
        screen == Screen.Gyms && ladder == DumbbellLadder -> LadderEditor(
            title = stringResource(R.string.gym_dumbbells),
            name = null,
            spec = dumbbells,
            kinds = listOf(LadderKind.List, LadderKind.Stack),
            onSave = { _, spec -> gymsModel.setDumbbells(spec); ladder = null },
            onDelete = null,
            onClose = { ladder = null },
        )
        screen == Screen.Gyms && ladder == PlateLadder -> LadderEditor(
            title = stringResource(R.string.gym_plates),
            name = null,
            spec = plates,
            kinds = listOf(LadderKind.Plates),
            onSave = { _, spec -> (spec as? LadderSpec.Plates)?.let(gymsModel::setPlates); ladder = null },
            onDelete = null,
            onClose = { ladder = null },
        )
        screen == Screen.Gyms && gymId != null && gyms.any { it.id == gymId } -> {
            val gym = gyms.first { it.id == gymId }
            val itemId = ladder
            if (itemId != null) {
                val item = gym.items.firstOrNull { it.id == itemId }
                LadderEditor(
                    title = item?.name ?: stringResource(R.string.ladder_new_item),
                    name = item?.name.orEmpty(),
                    spec = item?.ladder,
                    kinds = LadderKind.Items,
                    pinPlates = gym.pinPlates,
                    onSave = { name, spec -> gymsModel.saveItem(EquipmentItem(item?.id ?: 0, gym.id, name, spec)); ladder = null },
                    onDelete = item?.let { { gymsModel.deleteItem(it.id); ladder = null } },
                    onClose = { ladder = null },
                )
            } else {
                GymScreen(
                    gym,
                    onRename = { gymsModel.renameGym(gym.id, it) },
                    onWeekdays = { gymsModel.setWeekdays(gym.id, it) },
                    onDelete = { gymsModel.deleteGym(gym.id); gymId = null },
                    onOpenItem = { ladder = it },
                    onAddItem = { ladder = NewItem },
                    onBack = { gymId = null },
                    exerciseNames = exercises.associate { it.id to it.name },
                    onRestore = { gymsModel.unmarkMissing(gym.id, it) },
                    onPinPlates = { gymsModel.setPinPlates(gym.id, it) },
                )
            }
        }
        screen == Screen.Gyms -> GymsScreen(
            gyms, dumbbells, plates, bars,
            onAddGym = { name -> gymsModel.addGym(name) { gymId = it } },
            onOpenGym = { gymId = it },
            onDumbbells = { ladder = DumbbellLadder },
            onPlates = { ladder = PlateLadder },
            onBar = gymsModel::setBar,
            onBack = { screen = Screen.Settings },
        )
        screen == Screen.Library -> LibraryScreen(
            exercises,
            query = libraryQuery,
            onQuery = { libraryQuery = it },
            onOpen = { editing = it },
            onNew = { editing = NewExercise },
            onBack = { screen = Screen.Settings; libraryQuery = "" },
        )
        screen == Screen.Program -> ProgramScreen(
            program,
            importErrors,
            onImport = { programModel.closeErrors(); pickProgram.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onCloseErrors = programModel::closeErrors,
            onBack = { screen = Screen.Today },
            starter = programModel.starter,
            onRunStarter = programModel::runStarter,
            onPropose = programModel::propose,
            onRevert = programModel::revert,
            onSettings = openSettings,
        )
        screen == Screen.Coach -> CoachScreen(
            program,
            exercises,
            lastTrained,
            onPropose = programModel::propose,
            onBack = { programModel.closePasteErrors(); screen = Screen.Today },
            onAskClaude = { words ->
                scope.launch { programModel.askClaudePrompt(words)?.let { context.startActivity(AskClaudeShare.intent(context, it)) } }
            },
            onPaste = { programModel.paste(AskClaudeShare.clipboard(context)) },
            pasteErrors = pasteErrors,
            onClosePasteErrors = programModel::closePasteErrors,
            onCopyProblems = { AskClaudeShare.copy(context, "Hyperlog", it.joinToString("\n")) },
            weekdays = gyms.flatMap { it.weekdays }.toSet(),
            onSettings = openSettings,
        )
        screen == Screen.History && pastSession?.shown != null -> PastExerciseScreen(pastSession!!, pastModel, onBack = { pastModel.show(null) })
        // A session opened from History or a trend goes back there, read again with any correction.
        screen == Screen.History && pastSession != null -> PastSessionScreen(
            pastSession!!,
            onOpen = pastModel::show,
            onBack = { pastModel.close(); pastModel.refresh(); historyModel.refresh() },
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
            onSettings = openSettings,
        )
        screen == Screen.Settings -> SettingsScreen(
            gymCount = gyms.size,
            exerciseCount = exercises.count { !it.hidden },
            onGyms = { screen = Screen.Gyms; gymId = null; ladder = null },
            onLibrary = { screen = Screen.Library },
            onBack = { screen = settingsFrom },
            startingWeights = startingWeights,
            onImportStartingWeights = { pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            backup = backup,
            onExport = { exportBackup.launch(backupModel.fileName()) },
            onRestore = { pickRestore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onConfirmRestore = backupModel::restore,
            onDismissBackup = backupModel::dismiss,
            checkForUpdates = checkForUpdates,
            onCheckForUpdates = updateModel::setCheckForUpdates,
        )
        else -> TodayScreen(
            onSettings = openSettings,
            sessionRunning = running != null,
            onResume = { left = false },
            onStart = { gym, adHoc, swaps ->
                left = false
                sessions.start(gym, adHoc, swaps)
            },
            gyms = gyms,
            usualGym = usualGym,
            program = program,
            exerciseNames = exercises.associate { it.id to it.name },
            onProgram = { screen = Screen.Program },
            starter = programModel.starter,
            onRunStarter = programModel::runStarter,
            library = exercises,
            lastTrained = lastTrained,
            onPropose = programModel::propose,
            weekdays = gyms.flatMap { it.weekdays }.toSet(),
            update = update,
            onUpdate = { updateModel.install(context) },
            onDismissUpdate = updateModel::dismiss,
            runningDay = running?.day,
        )
    }
    }
    }
}
