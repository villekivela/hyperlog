package com.hyperlog.app.rest

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowVibrator

/**
 * Rest on the lock screen (SPEC): the ongoing notification, its +15 s and End rest, and the
 * alarm-class buzz when rest runs out with the phone in a pocket.
 */
@RunWith(RobolectricTestRunner::class)
class RestTimerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L
    private var visible = false
    private val timer = RestTimer(context, clock = { now }, visible = { visible })

    @Before
    fun open() = runTest {
        ShadowVibrator.reset()
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        programs = Programs(db, clock = { now })
    }

    @After
    fun close() = db.close()

    private fun sets(count: Int, rest: Int, technique: String? = null) =
        """[ { "count": $count, "reps": "8-10", "rir": 1, "rest": $rest${technique?.let { ", \"technique\": $it" } ?: ""} } ]"""

    private suspend fun logger(benchTechnique: String? = null): SessionLogger {
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "db_bench_press", "sets": ${sets(3, 180, benchTechnique)} } ] } ] } ] } ] }
            """,
        )
        return SessionLogger(db, clock = { now }, programs = programs).also {
            it.load()
            it.startDay(programs.active.first()!!, gymId = null)
        }
    }

    private suspend fun SessionLogger.log(kg: String, reps: String = "8") {
        now += 5_000
        kg.forEach { press(PadKey.Digit(it.digitToInt())) }; done()
        reps.forEach { press(PadKey.Digit(it.digitToInt())) }; done()
        timer.show(state.value?.restNotice())
    }

    private val notification: Notification? get() = shadowOf(notifications).allNotifications.singleOrNull()
    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private val Notification.sub get() = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
    private val Notification.actionTitles get() = actions.orEmpty().map { it.title.toString() }
    private val alarm get() = shadowOf(alarms).peekNextScheduledAlarm()
    private val vibrator get() = shadowOf(context.getSystemService(Vibrator::class.java))

    /** What the notification's action [title] sends, handled as the receiver would. */
    private suspend fun tap(title: String, logger: SessionLogger) {
        val action = notification!!.actions.first { it.title.toString() == title }
        RestReceiver.handle(shadowOf(action.actionIntent).savedIntent, logger, timer)
    }

    /** The rest alarm going off, handled as the receiver would. */
    private suspend fun ring(logger: SessionLogger) {
        val alarm = alarm!!
        now = alarm.triggerAtTime
        shadowOf(alarms).fireAlarm(alarm)
        RestReceiver.handle(shadowOf(alarm.operation).savedIntent, logger, timer)
    }

    @Test
    fun restShowsAnOngoingCountdownToTheNextSetOnTheLockScreen() = runTest {
        val logger = logger()
        logger.log("20")

        val n = notification!!
        assertEquals("DB Bench Press · Set 2", n.title)
        assertEquals("20 kg × 8–10 @ 1", n.text)
        assertEquals("Rest", n.sub)
        assertEquals(listOf("+15", "End rest"), n.actionTitles)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(now + 180_000, n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        // A Live Update whose status-bar chip is the countdown: a short critical text would replace
        // it (found on the phone: the chip read "Rest").
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertNull(n.shortCriticalText)
        // The alarm that buzzes when rest runs out, exact and through Doze.
        assertEquals(now + 180_000, alarm!!.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm!!.type)
        assertTrue(alarm!!.allowWhileIdle)
    }

    @Test
    fun plus15AndEndRestWorkFromTheNotification() = runTest {
        val logger = logger()
        logger.log("20")
        val ends = now + 180_000

        tap("+15", logger)
        assertEquals(ends + 15_000, logger.state.value!!.restEndsAt)
        assertEquals(ends + 15_000, notification!!.`when`)
        assertEquals(ends + 15_000, alarm!!.triggerAtTime)

        tap("End rest", logger)
        assertNull(logger.state.value!!.restEndsAt)
        assertNull(notification)
        assertNull(alarm)
    }

    @Test
    fun whenRestRunsOutThePhoneBuzzesAsAnAlarmAndTheNotificationCountsUp() = runTest {
        val logger = logger()
        logger.log("20")
        ring(logger)

        assertTrue(vibrator.isVibrating)
        assertEquals(VibrationAttributes.USAGE_ALARM, (vibrator.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
        // Two pulses.
        assertEquals(listOf(0L, 400L, 200L, 400L), vibrator.pattern.toList())
        val n = notification!!
        assertEquals("Rest over", n.sub)
        assertEquals("DB Bench Press · Set 2", n.title)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("End rest"), n.actionTitles)
        // Out of the app it alerts (heads-up) on its own channel.
        assertEquals(RestTimer.OVER_CHANNEL, n.channelId)
    }

    @Test
    fun restRunningOutOutOfSightVibratesWithNoSoundByDefault() = runTest {
        // An install from before vibrate-only kept its channel with the sound on.
        notifications.createNotificationChannel(
            android.app.NotificationChannel("rest_over", "Rest over", NotificationManager.IMPORTANCE_HIGH),
        )
        val logger = logger()
        logger.log("20")
        ring(logger)

        val channel = notifications.getNotificationChannel(notification!!.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        // No sound until it is turned on for the channel in the system's settings.
        assertNull(channel.sound)
        assertTrue(vibrator.isVibrating)
        // The old channel, which Android won't let the app change, is gone.
        assertNull(notifications.getNotificationChannel("rest_over"))
    }

    @Test
    fun withTheAppOpenRestRunningOutOnlyBuzzes() = runTest {
        val logger = logger()
        logger.log("20")
        visible = true
        ring(logger)

        // One buzz, not the pocket's two (found on the phone: it buzzed twice in the app).
        assertTrue(vibrator.isVibrating)
        assertEquals(400L, vibrator.milliseconds)
        assertEquals(RestTimer.REST_CHANNEL, notification!!.channelId)
        assertEquals("Rest over", notification!!.sub)
    }

    @Test
    fun anAlarmForRestThatChangedSinceDoesNothing() = runTest {
        val logger = logger()
        logger.log("20")
        val stale = alarm!!.triggerAtTime
        logger.adjustRest(15_000)
        timer.show(logger.state.value!!.restNotice())

        // An alarm already on its way when +15 s moved it.
        now = stale
        val late = android.content.Intent(RestReceiver.ACTION_ENDED).putExtra(RestReceiver.EXTRA_ENDS_AT, stale)
        RestReceiver.handle(late, logger, timer)
        assertFalse(vibrator.isVibrating)
        assertEquals("Rest", notification!!.sub)
    }

    @Test
    fun loggingTheNextSetStartsItsOwnRest() = runTest {
        val logger = logger()
        logger.log("20")
        ring(logger)
        logger.log("20")

        assertEquals("DB Bench Press · Set 3", notification!!.title)
        assertEquals("Rest", notification!!.sub)
        assertEquals(now + 180_000, alarm!!.triggerAtTime)
    }

    @Test
    fun pullingRestPastZeroWithMinus15BuzzesOnce() = runTest {
        val logger = logger()
        logger.log("20")
        now += 170_000
        logger.adjustRest(-15_000)
        timer.show(logger.state.value!!.restNotice())

        assertTrue(vibrator.isVibrating)
        assertEquals(400L, vibrator.milliseconds)
        assertEquals("Rest over", notification!!.sub)
        assertNull(alarm)
    }

    @Test
    fun noRestBeforeADropMeansNoNotification() = runTest {
        val logger = logger(benchTechnique = """{ "kind": "dropset", "drops": 1, "percent": 25 }""")
        logger.log("20")
        assertNull(notification)
        assertNull(alarm)
    }

    @Test
    fun aMyoBreathingPauseCountsDownToTheMiniSetAndBuzzesShortWhenItEnds() = runTest {
        val logger = logger(benchTechnique = """{ "kind": "myo_reps", "miniSetReps": 4, "maxMiniSets": 3 }""")
        logger.log("20", "12")

        val n = notification!!
        assertEquals("DB Bench Press · Mini-set 1", n.title)
        assertEquals("20 kg × 4", n.text)
        assertEquals("Breathe", n.sub)
        assertEquals(emptyList<String>(), n.actionTitles)
        assertEquals(now + 15_000, alarm!!.triggerAtTime)

        ring(logger)
        assertTrue(vibrator.isVibrating)
        // The mini-set is in hand: nothing is left to count.
        assertNull(notification)
    }

    @Test
    fun afterARebootTheRunningRestComesBack() = runTest {
        val logger = logger()
        logger.log("20")
        notifications.cancelAll()
        alarms.cancel(alarm!!.operation!!)
        // A fresh process after a reboot: no notification, no alarm, the session in the database.
        val booted = RestTimer(context, clock = { now }, visible = { false })
        val again = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        RestReceiver.handle(android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED), again, booted)

        assertNotNull(notification)
        assertEquals(now + 180_000, alarm!!.triggerAtTime)
        assertFalse(vibrator.isVibrating)
    }
}
