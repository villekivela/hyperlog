package com.hyperlog.app.rest

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hyperlog.app.MainActivity
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg

/**
 * Rest with the phone in a pocket (SPEC, Rest on the lock screen): an ongoing notification that
 * counts down to the next set, with +15 s and End rest, and an exact alarm that buzzes like an
 * alarm clock when rest runs out.
 *
 * No foreground service: the notification's chronometer counts on its own and the alarm wakes
 * the app when it is due, so nothing has to run in between. Both outlive the app's process; a
 * reboot clears them and [RestReceiver] puts them back.
 */
class RestTimer(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Whether the app is on screen, where rest running out only buzzes. */
    private val visible: () -> Boolean = { AppVisibility.foreground },
) {
    private val context = context.applicationContext
    private val notifications = NotificationManagerCompat.from(this.context)
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    /** What was shown last, so the same notice isn't posted twice and −15 past zero is noticed. */
    private var shown: RestNotice? = null
    private var hasShown = false

    /** Shows [notice] (null takes the notification and its alarm away). */
    fun show(notice: RestNotice?) {
        if (hasShown && notice == shown) return
        val before = shown
        shown = notice
        hasShown = true
        if (notice == null) {
            alarms.cancel(alarmIntent(0))
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        val now = clock()
        if (notice.endsAt > now) {
            schedule(notice.endsAt)
            post(notice, over = false, alert = false)
        } else {
            alarms.cancel(alarmIntent(0))
            // −15 pulled a running countdown past zero: that is rest running out.
            val pulled = before != null && before.kind == notice.kind && before.endsAt > now
            // −15 is on the rest sheet, so the app is in hand: one buzz.
            if (pulled) buzz(notice.kind, inHand = true)
            if (notice.kind == RestNotice.Kind.Breathing) notifications.cancel(NOTIFICATION_ID)
            else post(notice, over = true, alert = false)
        }
    }

    /**
     * The alarm set for [endsAt] went off. If [notice] is still that countdown, the phone buzzes
     * and rest counts up; an alarm for a countdown that has changed since does nothing.
     */
    fun ended(notice: RestNotice?, endsAt: Long) {
        if (notice == null || notice.endsAt != endsAt) return
        shown = notice
        hasShown = true
        val onScreen = visible()
        buzz(notice.kind, inHand = onScreen)
        // A breathing pause leads straight into the mini-set, which is in hand.
        if (notice.kind == RestNotice.Kind.Breathing) notifications.cancel(NOTIFICATION_ID)
        else post(notice, over = true, alert = !onScreen)
    }

    private fun schedule(at: Long) {
        val operation = alarmIntent(at)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }

    private fun post(notice: RestNotice, over: Boolean, alert: Boolean) {
        if (!notifications.areNotificationsEnabled()) return
        channels()
        val res = context.resources
        val breathing = notice.kind == RestNotice.Kind.Breathing
        val label = when {
            breathing -> res.getString(R.string.breathing)
            over -> res.getString(R.string.rest_over)
            else -> res.getString(R.string.rest_title)
        }
        val next = notice.next
        val title = when {
            next == null -> res.getString(R.string.session_day_done)
            next.miniSet != null -> res.getString(R.string.rest_next_mini_set, next.exercise, next.miniSet)
            else -> res.getString(R.string.rest_next_set, next.exercise, next.set)
        }
        val builder = NotificationCompat.Builder(context, if (alert) OVER_CHANNEL else REST_CHANNEL)
            .setSmallIcon(if (breathing) R.drawable.symbol_air else R.drawable.symbol_timer)
            .setContentTitle(title)
            .setContentText(next?.let(::target))
            .setSubText(label)
            .setWhen(notice.endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(!over)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setCategory(if (alert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            // A Live Update (Android 16): the countdown also shows as a chip in the status bar. A
            // short critical text would take the chronometer's place there, so there is none.
            .setRequestPromotedOngoing(true)
        if (!breathing) {
            if (!over) builder.addAction(0, res.getString(R.string.rest_add), actionIntent(RestReceiver.ACTION_ADD))
            builder.addAction(0, res.getString(R.string.rest_end), actionIntent(RestReceiver.ACTION_END))
        }
        try {
            notifications.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications were turned off in between; the buzz still comes.
        }
    }

    /** `25 kg × 8–10 @ 1`, `8–10 reps @ 1` with no weight yet, `25 kg × 4` for a mini-set. */
    private fun target(next: NextSet): String {
        val res = context.resources
        val reps = next.weightKg?.let { res.getString(R.string.rest_target_kg, formatKg(it), next.reps) }
            ?: res.getString(R.string.rest_target_reps, next.reps)
        return next.rir?.let { res.getString(R.string.rest_target_rir, reps, it) } ?: reps
    }

    /**
     * Alarm-class, so it comes through on vibrate and silent and in a pocket. Rest ending buzzes
     * twice, or once with the app [inHand] (on screen).
     */
    private fun buzz(kind: RestNotice.Kind, inHand: Boolean) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        val effect = when (kind) {
            RestNotice.Kind.Rest if inHand -> VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
            RestNotice.Kind.Rest -> VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
            RestNotice.Kind.Breathing -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun channels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val res = context.resources
        // Silent but not "silent": a low-importance channel hides from many lock screens.
        val rest = NotificationChannel(REST_CHANNEL, res.getString(R.string.rest_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = res.getString(R.string.rest_channel_about)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        // The buzz is the app's own alarm-class one. The channel pops up with no sound; the sound
        // can be turned on for it in the system's settings.
        val over = NotificationChannel(OVER_CHANNEL, res.getString(R.string.rest_over_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            description = res.getString(R.string.rest_over_channel_about)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(rest, over))
        // A channel's sound is fixed once it exists, so vibrate-only came with a new id.
        manager.deleteNotificationChannel(OLD_OVER_CHANNEL)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, RestReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The one rest alarm; its extra says which countdown it was set for. */
    private fun alarmIntent(endsAt: Long): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, RestReceiver::class.java).setAction(RestReceiver.ACTION_ENDED).putExtra(RestReceiver.EXTRA_ENDS_AT, endsAt),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val REST_CHANNEL = "rest"
        /** Vibrate only by default; the old [OLD_OVER_CHANNEL] played the sound. */
        const val OVER_CHANNEL = "rest_over_vibrate"
        private const val OLD_OVER_CHANNEL = "rest_over"
        const val NOTIFICATION_ID = 1
        private const val ALARM_REQUEST = 1
    }
}

/** Whether the app is on screen; [MainActivity] keeps it. */
object AppVisibility {
    @Volatile var foreground: Boolean = false
}
