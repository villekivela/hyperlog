package com.hyperlog.app.session

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** On a rotation a program week is a calendar week (ADR 0006), and so is the week a card counts (round 4). */
@RunWith(RobolectricTestRunner::class)
class WeekDoneRotationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var plans: WeekdayPlans
    private val monday = LocalDate.of(2026, 10, 5)
    private var today = monday
    private var minutes = 0L

    private fun noon(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        programs = Programs(db, clock = { noon(today) })
        plans = WeekdayPlans(db, clock = { noon(today) })
        plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, SATURDAY to null)))
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 8, "days": [
              ${day("a", "Day A", "db_bench_press")}, ${day("b", "Day B", "db_lateral_raise")}, ${day("c", "Day C", "db_bicep_curl")} ] } ] }
            """,
        )
    }

    private fun day(id: String, name: String, exercise: String) = """{ "id": "$id", "name": "$name", "groups": [
        { "slots": [ { "id": "s$id", "exercise": "$exercise", "sets": [ { "count": 2, "reps": "8-10", "rir": 1, "rest": 90 } ] } ] } ] }"""

    @After
    fun close() = db.close()

    /** Today's turn, trained live, two sets. */
    private suspend fun train(date: LocalDate): SessionSummary {
        today = date
        val logger = SessionLogger(db, clock = { noon(today) + minutes * 60_000 }, programs = programs).also {
            it.load()
            it.startDay(programs.active.first()!!, gymId = null)
        }
        repeat(2) {
            minutes += 3
            if (logger.state.value?.entry?.field == Field.Weight) { logger.press(PadKey.Digit(2)); logger.press(PadKey.Digit(0)); logger.done() }
            logger.press(PadKey.Digit(8)); logger.done()
        }
        return logger.finish()!!
    }

    /** A session already in the books on [date], of [sets] sets, on an exercise today's days don't use. */
    private suspend fun trained(date: LocalDate, week: Int, day: Int, sets: Int) {
        val id = programs.active.first()!!.id
        val dao = db.sessions()
        val session = dao.insertSession(SessionEntity(startedAt = noon(date), finishedAt = noon(date) + 3_600_000, programId = id, week = week, dayIndex = day))
        val exercise = dao.insertSessionExercise(SessionExerciseEntity(sessionId = session, exerciseId = "leg_press", position = 0))
        repeat(sets) { dao.insertSet(LoggedSetEntity(sessionExerciseId = exercise, position = it, weightKg = 100.0, fullReps = 8, partialReps = 0, rir = 1, loggedAt = noon(date))) }
    }

    @Test
    fun aDayWithAnotherStillToComeThisWeekFinishesNoWeek() = runTest {
        assertNull(train(monday).week)
        assertNull(train(monday.plusDays(3)).week)
    }

    @Test
    fun theLastTrainingDayFinishesTheCalendarWeek() = runTest {
        train(monday)
        train(monday.plusDays(3))
        assertEquals(WeekDone(week = 1, days = 3, workSets = 6, lastWeekSets = null, streak = 1), train(monday.plusDays(5)).week)
    }

    /** Round 4, 14 Nov: what comes next is the same program week again, and the week is still done. */
    @Test
    fun aWeekToBeRepeatedStillFinishes() = runTest {
        trained(monday, week = 1, day = 0, sets = 2)
        trained(monday.plusDays(3), week = 1, day = 1, sets = 2)
        trained(monday.plusDays(5), week = 1, day = 2, sets = 2)
        // Two weeks with nothing, so the next is an easy week, and the program waits.
        val easy = monday.plusWeeks(3)
        trained(easy, week = 2, day = 0, sets = 2)
        trained(easy.plusDays(3), week = 2, day = 1, sets = 2)
        val card = train(easy.plusDays(5)).week
        assertEquals(3, card?.days)
    }

    /** Round 4, 21 Nov: one session that week read `5 days, 23 sets, 7 weeks in a row`. */
    @Test
    fun aRepeatedProgramWeekCountsOnlyThisCalendarWeek() = runTest {
        trained(monday, week = 1, day = 0, sets = 9)
        trained(monday.plusDays(3), week = 1, day = 1, sets = 9)
        val later = monday.plusWeeks(2)
        val card = train(later.plusDays(5)).week
        assertEquals(1, card?.days)
        assertEquals(2, card?.workSets)
    }

    @Test
    fun aRunIsCalendarWeeksWithNoEmptyOneBetween() = runTest {
        trained(monday, week = 1, day = 0, sets = 3)
        trained(monday.plusWeeks(2), week = 2, day = 0, sets = 4)
        trained(monday.plusWeeks(3), week = 3, day = 0, sets = 5)
        val card = train(monday.plusWeeks(4).plusDays(5)).week
        assertEquals(3, card?.streak)
        assertEquals(5, card?.lastWeekSets)
    }

    @Test
    fun anEmptyWeekBeforeHasNothingToCompare() = runTest {
        trained(monday, week = 1, day = 0, sets = 3)
        val card = train(monday.plusWeeks(2).plusDays(5)).week
        assertNull(card?.lastWeekSets)
        assertEquals(1, card?.streak)
    }
}
