package com.hyperlog.app.coach

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What a check-in's call is doing, while one runs: the ongoing notification's words. Null: no call. */
object CheckInWork {
    val text = MutableStateFlow<String?>(null)
}

/**
 * The foreground service a check-in's call runs under (`dataSync`), so leaving the app doesn't cut
 * its stream. It shows [CheckInWork]'s words in an ongoing notification and stops itself when they
 * end; nothing else stops it, so it is always foreground before it goes.
 */
class CheckInService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notices by lazy { CheckInNotices(this) }
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, CheckInNotices.WORKING_ID, notices.working(CheckInWork.text.value), type)
        if (!watching) {
            watching = true
            scope.launch {
                CheckInWork.text.collect { text ->
                    if (text == null) {
                        ServiceCompat.stopForeground(this@CheckInService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notices.updateWorking(text)
                    }
                }
            }
        }
        // A call can't come back by itself after the process is gone: the check-in asks again when the app opens.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
