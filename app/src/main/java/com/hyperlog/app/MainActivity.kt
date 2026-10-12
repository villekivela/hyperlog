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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperlogTheme { App(sessions, library) }
        }
    }
}

private enum class Screen { Today, Settings, Library }

/** Marks a new exercise in the editor, which otherwise holds the id of the one being edited. */
private const val NewExercise = ""

/**
 * Today, the running session, or the summary of the one just finished; settings and the
 * exercise library behind Today. The app opens on a running session; back leaves it running
 * and goes to Today.
 */
@Composable
private fun App(sessions: SessionViewModel, library: LibraryViewModel) {
    val loaded by sessions.loaded.collectAsStateWithLifecycle()
    val session by sessions.session.collectAsStateWithLifecycle()
    val summary by sessions.summary.collectAsStateWithLifecycle()
    val exercises by sessions.exercises.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    if (!loaded) return

    val running = session
    val finished = summary
    when {
        finished != null -> SessionDoneScreen(finished, onClose = { sessions.dismissSummary(); left = false })
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
        screen == Screen.Library -> LibraryScreen(
            exercises,
            query = libraryQuery,
            onQuery = { libraryQuery = it },
            onOpen = { editing = it },
            onNew = { editing = NewExercise },
            onBack = { screen = Screen.Settings; libraryQuery = "" },
        )
        screen == Screen.Settings -> SettingsScreen(
            exerciseCount = exercises.count { !it.hidden },
            onLibrary = { screen = Screen.Library },
            onBack = { screen = Screen.Today },
        )
        else -> TodayScreen(
            onSettings = { screen = Screen.Settings },
            sessionRunning = running != null,
            onSession = {
                left = false
                if (running == null) sessions.start()
            },
        )
    }
}
