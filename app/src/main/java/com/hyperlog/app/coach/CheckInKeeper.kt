package com.hyperlog.app.coach

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.hyperlog.app.rest.AppVisibility
import com.hyperlog.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps a check-in's call going when you leave the app (SPEC, Coaching → Check-in): Android cuts a
 * backgrounded app's network, so while Claude replies the call runs in [CheckInService], a
 * foreground service with an ongoing notification. A reply that lands while the app is away is
 * saved by the runner as always, and a notification says what came, opening the check-in.
 */
class CheckInKeeper(
    private val context: Context,
    scope: CoroutineScope,
    thinking: StateFlow<Boolean>,
    private val view: StateFlow<CheckInView?>,
    private val visible: () -> Boolean = { AppVisibility.foreground },
) {
    private val notices = CheckInNotices(context)

    init {
        scope.launch {
            var was = false
            thinking.collect { now ->
                if (now && !was) started()
                if (!now && was) ended()
                was = now
            }
        }
    }

    private fun started() {
        notices.clearReady()
        val newProgram = view.value?.checkIn?.tile == Tile.NewProgram
        CheckInWork.text.value = context.getString(if (newProgram) R.string.checkin_working_program else R.string.checkin_working)
        // Started only while the app shows, as Android requires; a call that can't have it still runs while the app is open.
        runCatching { ContextCompat.startForegroundService(context, Intent(context, CheckInService::class.java)) }
            .onFailure { Log.w(TAG, "Couldn't start the check-in's service: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun ended() {
        // The service sees the work end, and stops itself.
        CheckInWork.text.value = null
        val step = view.value?.step ?: return
        if (!visible()) notices.ready(step)
    }

    private companion object {
        const val TAG = "CheckInKeeper"
    }
}
