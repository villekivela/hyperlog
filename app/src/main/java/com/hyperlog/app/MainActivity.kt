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
import com.hyperlog.app.ui.session.SessionDoneScreen
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.session.SessionViewModel
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    private val sessions: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperlogTheme { App(sessions) }
        }
    }
}

/**
 * Today, the running session, or the summary of the one just finished. The app opens on a
 * running session; back leaves it running and goes to Today.
 */
@Composable
private fun App(sessions: SessionViewModel) {
    val loaded by sessions.loaded.collectAsStateWithLifecycle()
    val session by sessions.session.collectAsStateWithLifecycle()
    val summary by sessions.summary.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    if (!loaded) return

    val running = session
    val finished = summary
    when {
        finished != null -> SessionDoneScreen(finished, onClose = { sessions.dismissSummary(); left = false })
        running != null && !left -> SessionScreen(
            running,
            sessions.starterExercises,
            actions = sessions,
            onLeave = { left = true },
        )
        // Settings arrives with the gyms and equipment ticket.
        else -> TodayScreen(
            onSettings = {},
            sessionRunning = running != null,
            onSession = {
                left = false
                if (running == null) sessions.start()
            },
        )
    }
}
