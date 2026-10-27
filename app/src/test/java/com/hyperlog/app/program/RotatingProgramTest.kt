package com.hyperlog.app.program

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ProgramActions
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.ZoneId

/** The program running on training days, as stored (SPEC, Domain → Training days and the rotation). */
@RunWith(RobolectricTestRunner::class)
class RotatingProgramTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var plans: WeekdayPlans
    private var today = LocalDate.of(2026, 10, 29)

    private fun noon(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { noon(today) })
        plans = WeekdayPlans(db, clock = { noon(today) })
    }

    @After
    fun close() = db.close()

    private suspend fun trainingDays() = plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, SATURDAY to null)))

    private suspend fun planned() = programs.active.first()!!.rotation!!.planned.map { "${it.position.week}.${it.position.day} ${it.date}" }

    /** A finished session started on [date]: a program day when [position] is given, else ad hoc. */
    private suspend fun trained(date: LocalDate, position: Position? = null) {
        val id = programs.active.first()!!.id
        db.sessions().insertSession(
            SessionEntity(startedAt = noon(date), finishedAt = noon(date) + 3_600_000, programId = position?.let { id }, week = position?.week, dayIndex = position?.day),
        )
    }

    @Test
    fun aProgramStartedMidWeekBeginsAtTheFirstTrainingDayFromThatDay() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        assertEquals(listOf("1.0 2026-10-29", "1.1 2026-10-31", "2.0 2026-11-02"), planned().take(3))
        // Monday, before the start, isn't missed.
        assertEquals(emptyList<Any>(), programs.active.first()!!.rotation!!.missed)
    }

    @Test
    fun aMissedDayIsSkippedAndFinishingASessionDoesntMoveTheRotationTwice() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // Thursday missed; Saturday's Day B trained and finished.
        today = LocalDate.of(2026, 10, 31)
        trained(today, Position(1, 1))
        programs.advance(programs.active.first()!!.id, Position(1, 1))
        val active = programs.active.first()!!
        assertEquals(listOf(LocalDate.of(2026, 10, 29)), active.rotation!!.missed.map { it.date })
        assertEquals(Position(2, 0), active.position)
        assertEquals(listOf("2.0 2026-11-02", "2.1 2026-11-05"), planned().take(2))
    }

    @Test
    fun clearingTheTrainingDaysSwitchesToTheSequenceAtTheRotationsNextDay() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        today = LocalDate.of(2026, 11, 1)
        trained(LocalDate.of(2026, 10, 29), Position(1, 0))
        trained(LocalDate.of(2026, 10, 31), Position(1, 1))
        plans.set(WeekdayPlan.None)
        val active = programs.active.first()!!
        assertNull(active.rotation)
        assertEquals(Position(2, 0), active.position)
        // Setting them again starts the rotation from today, nothing missed before it.
        today = LocalDate.of(2026, 11, 3)
        trainingDays()
        assertEquals(listOf("2.0 2026-11-05", "2.1 2026-11-07"), planned().take(2))
        assertEquals(emptyList<Any>(), programs.active.first()!!.rotation!!.missed)
    }

    @Test
    fun aTrainingDayMovedToAnotherWeekdayKeepsItsTurn() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        trained(today, Position(1, 0))
        // Saturday moves to Friday.
        plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, java.time.DayOfWeek.FRIDAY to null)))
        assertEquals(listOf("1.1 2026-10-30", "2.0 2026-11-02"), planned().take(2))
    }

    @Test
    fun aDaySkippedAheadIsMissedAndARepeatedWeekTrainsAgain() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        trained(today, Position(1, 0))
        programs.accept(ProgramActions.skipDay("Away Saturday"))
        assertEquals(listOf(LocalDate.of(2026, 10, 31)), programs.active.first()!!.rotation!!.missed.map { it.date })
        programs.accept(ProgramActions.repeatWeek("Again"))
        assertEquals(listOf("1.0 2026-11-02", "1.1 2026-11-05", "1.0 2026-11-07"), planned().take(3))
        // Reverted, the skip is gone.
        programs.revert(programs.active.first()!!.history.first().id)
        assertEquals(emptyList<Any>(), programs.active.first()!!.rotation!!.missed)
    }

    @Test
    fun shiftingTheProgramMovesTheWeekTheRotationTrains() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        programs.accept(ProgramActions.shift("A week on", -1))
        assertEquals(listOf("2.0 2026-10-29", "2.1 2026-10-31"), planned().take(2))
    }

    @Test
    fun twoSkippedDaysTakeTheNextTwoTrainingDaysAndADateWithNoneTakesTheOneAfterIt() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        trained(today, Position(1, 0))
        programs.accept(com.hyperlog.app.coach.ChangeJson.decode(
            """{"summary":"Away","scope":"rest_of_program","operations":[{"op":"skip_day"},{"op":"skip_day"}]}""",
        ))
        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2)), programs.active.first()!!.rotation!!.missed.map { it.date })
        // Friday has no training day: Saturday after it is skipped.
        programs.revert(programs.active.first()!!.history.single().id)
        programs.accept(com.hyperlog.app.coach.ChangeJson.decode(
            """{"summary":"Away","scope":"rest_of_program","operations":[{"op":"skip_day","date":"2026-10-30"}]}""",
        ))
        assertEquals(listOf(LocalDate.of(2026, 10, 31)), programs.active.first()!!.rotation!!.missed.map { it.date })
    }

    @Test
    fun missedDaysStayMissedWhenTheRotationIsSetAgain() = runTest {
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // Thursday missed; on Friday Saturday moves to Sunday, then a Change shifts the program.
        today = LocalDate.of(2026, 10, 30)
        plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, java.time.DayOfWeek.SUNDAY to null)))
        programs.accept(ProgramActions.shift("A week on", -1))
        assertEquals(listOf(LocalDate.of(2026, 10, 29)), programs.active.first()!!.rotation!!.missed.map { it.date })
    }

    @Test
    fun theProgramRunningMovesOnToTheNewDayWhileTheAppStaysOpen() = runTest {
        trainingDays()
        val ticking = Programs(db, clock = { noon(today) }, tick = 10)
        ticking.import(TestPrograms.twoDays("First"))
        // Today on screen holds one collection open, as the app does.
        val shown = kotlinx.coroutines.flow.MutableStateFlow<ActiveProgram?>(null)
        val screen = launch(kotlinx.coroutines.Dispatchers.Default) { ticking.active.collect { shown.value = it } }
        suspend fun until(date: LocalDate) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) { shown.first { it?.today == date }!! }
        }
        until(LocalDate.of(2026, 10, 29))
        // Overnight, with nothing written: the next day comes up by itself.
        today = LocalDate.of(2026, 10, 31)
        assertEquals(LocalDate.of(2026, 10, 31), until(today).rotation!!.planned.first().date)
        screen.cancel()
    }

    private val monWedFri = WeekdayPlan(mapOf(MONDAY to null, java.time.DayOfWeek.WEDNESDAY to null, java.time.DayOfWeek.FRIDAY to null))

    @Test
    fun trainingDaysChangedAfterTheWeeksLastTrainingDayKeepTheWeeksResult() = runTest {
        today = LocalDate.of(2026, 10, 26)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // Week 1 trained in full, then on Saturday evening the days move to Mon Wed Fri.
        trained(LocalDate.of(2026, 10, 26), Position(1, 0))
        trained(LocalDate.of(2026, 10, 29), Position(1, 1))
        today = LocalDate.of(2026, 10, 31)
        trained(today, Position(1, 0))
        val next = planned().first()
        plans.set(monWedFri)
        assertEquals("2.1 2026-11-02", next)
        assertEquals(next, planned().first())
    }

    @Test
    fun trainingDaysChangedOnAnEmptyWeeksWeekendStillCountItEmpty() = runTest {
        today = LocalDate.of(2026, 10, 26)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        trained(today, Position(1, 0))
        // Week 2 has no session; on its Saturday the days move to Mon Wed Fri, none left that week.
        today = LocalDate.of(2026, 11, 7)
        plans.set(monWedFri)
        today = LocalDate.of(2026, 11, 9)
        val rotation = programs.active.first()!!.rotation!!
        assertEquals(true, rotation.again)
        assertEquals(1, rotation.emptyWeeks)
        assertEquals("2.1 2026-11-09", planned().first())
    }

    @Test
    fun aMissedDayStaysOnOfferAfterTheTrainingDaysChangeAndTrainingItClearsIt() = runTest {
        today = LocalDate.of(2026, 10, 26)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // Monday missed; on Tuesday Saturday moves to Friday.
        today = LocalDate.of(2026, 10, 27)
        plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, java.time.DayOfWeek.FRIDAY to null)))
        val offered = programs.active.first()!!.rotation!!.trainToday!!
        assertEquals(LocalDate.of(2026, 10, 26), offered.date)
        trained(today, offered.position)
        val rotation = programs.active.first()!!.rotation!!
        assertNull(rotation.trainToday)
        assertEquals(emptyList<Any>(), rotation.missed)
    }

    @Test
    fun aWeekTrainedAgainKeepsSayingSoWhenTheTrainingDaysChangeMidWeek() = runTest {
        today = LocalDate.of(2026, 10, 26)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        trained(today, Position(1, 0))
        // Week 2 has no session, so the week of 9 Nov trains it again; on its Tuesday Saturday moves to Friday.
        today = LocalDate.of(2026, 11, 10)
        assertEquals(true, programs.active.first()!!.rotation!!.again)
        plans.set(WeekdayPlan(mapOf(MONDAY to null, THURSDAY to null, java.time.DayOfWeek.FRIDAY to null)))
        assertEquals(true, programs.active.first()!!.rotation!!.again)
    }

    @Test
    fun aProgramStartedOnASundayStartsItsFirstWeekOnMonday() = runTest {
        today = LocalDate.of(2026, 11, 1)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // The week it started in has no training day left, so it doesn't count, trained or not.
        assertEquals("1.0 2026-11-02", planned().first())
        assertEquals(false, programs.active.first()!!.rotation!!.again)
    }

    @Test
    fun aMissedDayStaysOnOfferAfterAChangeMovesThePosition() = runTest {
        today = LocalDate.of(2026, 10, 26)
        trainingDays()
        programs.import(TestPrograms.twoDays("First"))
        // Monday missed; on Tuesday a Change shifts the program a week on.
        today = LocalDate.of(2026, 10, 27)
        programs.accept(ProgramActions.shift("A week on", -1))
        assertEquals(LocalDate.of(2026, 10, 26), programs.active.first()!!.rotation!!.trainToday!!.date)
    }
}
