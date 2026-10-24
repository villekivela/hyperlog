package com.hyperlog.app.coach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hyperlog.app.MainActivity
import com.hyperlog.app.R

/**
 * A check-in's notifications: the ongoing one while Claude replies, and the one that says a reply
 * came while the app was away. Both open the check-in. Without the permission to post, nothing
 * shows and the check-in works the same.
 */
class CheckInNotices(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val res = context.resources
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(WORKING_CHANNEL, res.getString(R.string.checkin_working_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(READY_CHANNEL, res.getString(R.string.checkin_ready_channel), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    /** The ongoing notification: what Claude is doing. */
    fun working(text: String?): Notification = NotificationCompat.Builder(context, WORKING_CHANNEL)
        .setSmallIcon(R.drawable.symbol_sports)
        .setContentTitle(text ?: context.getString(R.string.checkin_working))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setProgress(0, 0, true)
        .setContentIntent(open())
        .build()

    fun updateWorking(text: String) = post(WORKING_ID, working(text))

    /** A reply came while the app was away: what it is. */
    fun ready(step: CheckInStep) {
        val title = readyTitle(step) ?: return
        post(
            READY_ID,
            NotificationCompat.Builder(context, READY_CHANNEL)
                .setSmallIcon(R.drawable.symbol_sports)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.checkin_ready_open))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(open())
                .build(),
        )
    }

    fun clearReady() = manager.cancel(READY_ID)

    /** The ready notification's title for [step]; null for one that isn't a reply. */
    fun readyTitle(step: CheckInStep): String? = when (step) {
        is CheckInStep.ProgramReview -> context.getString(R.string.checkin_ready_program)
        is CheckInStep.ChangeReview, is CheckInStep.WorkoutReview -> context.getString(R.string.checkin_ready_reply)
        is CheckInStep.Question -> context.getString(R.string.checkin_ready_question)
        is CheckInStep.Words -> context.getString(R.string.checkin_ready_words)
        is CheckInStep.Problems, is CheckInStep.Failed -> context.getString(R.string.checkin_ready_failed)
        CheckInStep.Thinking -> null
    }

    private fun post(id: Int, notification: Notification) {
        // Without the permission (Android 13+), nothing shows: the check-in goes on the same.
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(
        context, OPEN_REQUEST,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CHECK_IN, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /** On MainActivity's intent: open Coach, where the check-in is. */
        const val EXTRA_OPEN_CHECK_IN = "open_check_in"
        const val WORKING_ID = 2001
        const val READY_ID = 2002
        private const val OPEN_REQUEST = 2001
        private const val WORKING_CHANNEL = "check_in"
        private const val READY_CHANNEL = "check_in_ready"
    }
}
