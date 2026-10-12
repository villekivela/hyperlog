package com.hyperlog.app.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.session.SessionRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The rest notification's +15 s and End rest, the rest alarm going off, and the phone starting
 * up (or the app updating) with a session running, all in the app's own process. The process may
 * have been killed since, so each starts by loading the session.
 */
class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val runtime = SessionRuntime.get(context)
                runtime.ready()
                handle(intent, runtime.logger, runtime.timer)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.hyperlog.app.rest.ADD"
        const val ACTION_END = "com.hyperlog.app.rest.END"
        const val ACTION_ENDED = "com.hyperlog.app.rest.ENDED"
        const val EXTRA_ENDS_AT = "endsAt"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Acts on [intent] for [logger]'s session and brings [timer] up to date. */
        suspend fun handle(intent: Intent, logger: SessionLogger, timer: RestTimer) {
            when (intent.action) {
                ACTION_ADD -> logger.adjustRest(15_000)
                ACTION_END -> logger.endRest()
                ACTION_ENDED -> {
                    timer.ended(logger.state.value?.restNotice(), intent.getLongExtra(EXTRA_ENDS_AT, 0))
                    return
                }
            }
            // Boot and app updates come here too: the countdown and its alarm come back.
            timer.show(logger.state.value?.restNotice())
        }
    }
}
