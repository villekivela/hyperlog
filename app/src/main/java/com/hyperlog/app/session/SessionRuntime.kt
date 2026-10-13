package com.hyperlog.app.session

import android.app.Application
import android.content.Context
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.rest.RestTimer
import com.hyperlog.app.rest.restNotice
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one running session of the app's process: the screen and the rest notification's actions
 * act on the same [logger], and [timer] follows its rest wherever the change came from.
 */
class SessionRuntime private constructor(application: Application, private val db: HyperlogDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val logger = SessionLogger(db, programs = ProgramStore.get(application))
    val timer = RestTimer(application)

    /** The shipped library comes first (sessions point at its exercises), then the session. */
    private val loaded = scope.async {
        ExerciseLibrary(db).sync(withContext(Dispatchers.IO) { LibrarySeed.fromAssets(application) })
        logger.load()
    }

    init {
        scope.launch {
            logger.state.map { it?.restNotice() }.distinctUntilChanged().collect { timer.show(it) }
        }
        // An accepted or reverted Change edits the running session's day too.
        scope.launch {
            ProgramStore.get(application).active.map { it?.history }.distinctUntilChanged().drop(1).collect {
                loaded.await()
                logger.readAgain()
            }
        }
    }

    /** Waits until the session is loaded. */
    suspend fun ready() = loaded.await()

    companion object {
        @Volatile private var instance: SessionRuntime? = null

        fun get(context: Context): SessionRuntime {
            val application = context.applicationContext as Application
            val db = HyperlogDatabase.get(application)
            instance?.takeIf { it.db === db }?.let { return it }
            return synchronized(this) {
                instance?.takeIf { it.db === db } ?: SessionRuntime(application, db).also { instance = it }
            }
        }
    }
}
