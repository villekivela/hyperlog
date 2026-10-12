package com.hyperlog.app

import android.os.Bundle
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
import com.hyperlog.app.ui.session.SessionDoneScreen
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SessionViewModel
import com.hyperlog.app.ui.settings.SettingsScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    private val sessions: SessionViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    private val gyms: GymsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperlogTheme { App(sessions, library, gyms) }
        }
    }
}

private enum class Screen { Today, Settings, Library, Gyms }

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
private fun App(sessions: SessionViewModel, library: LibraryViewModel, gymsModel: GymsViewModel) {
    val loaded by sessions.loaded.collectAsStateWithLifecycle()
    val session by sessions.session.collectAsStateWithLifecycle()
    val summary by sessions.summary.collectAsStateWithLifecycle()
    val exercises by sessions.exercises.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    val gyms by gymsModel.gyms.collectAsStateWithLifecycle()
    val dumbbells by gymsModel.dumbbells.collectAsStateWithLifecycle()
    val plates by gymsModel.plates.collectAsStateWithLifecycle()
    val usualGym by sessions.usualGym.collectAsStateWithLifecycle()
    var gymId by rememberSaveable { mutableStateOf<Long?>(null) }
    /** The ladder being edited: an item id (0 = new item), or [DumbbellLadder] / [PlateLadder]. */
    var ladder by rememberSaveable { mutableStateOf<Long?>(null) }
    if (!loaded) return

    val running = session
    val finished = summary
    when {
        finished != null -> SessionDoneScreen(
            finished,
            onResume = { sessions.resumeFinished(); left = false },
            onClose = { sessions.dismissSummary(); left = false },
        )
        running != null && !left -> SessionScreen(
            running,
            exercises,
            actions = sessions,
            onLeave = { left = true },
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
                    kinds = LadderKind.entries,
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
                )
            }
        }
        screen == Screen.Gyms -> GymsScreen(
            gyms, dumbbells, plates,
            onAddGym = { name -> gymsModel.addGym(name) { gymId = it } },
            onOpenGym = { gymId = it },
            onDumbbells = { ladder = DumbbellLadder },
            onPlates = { ladder = PlateLadder },
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
        screen == Screen.Settings -> SettingsScreen(
            gymCount = gyms.size,
            exerciseCount = exercises.count { !it.hidden },
            onGyms = { screen = Screen.Gyms; gymId = null; ladder = null },
            onLibrary = { screen = Screen.Library },
            onBack = { screen = Screen.Today },
        )
        else -> TodayScreen(
            onSettings = { screen = Screen.Settings },
            sessionRunning = running != null,
            onResume = { left = false },
            onStart = { gym ->
                left = false
                sessions.start(gym)
            },
            gyms = gyms,
            usualGymId = usualGym,
        )
    }
}
