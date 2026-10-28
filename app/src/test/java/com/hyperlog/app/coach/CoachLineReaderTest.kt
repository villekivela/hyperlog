package com.hyperlog.app.coach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.week.Rotation
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.RotationTurn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** The coach line read from the log as stored (SPEC, Coaching → The coach line). */
@RunWith(RobolectricTestRunner::class)
class CoachLineReaderTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var reader: CoachLineReader
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 28)
    private val now = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        check(programs.import(PROGRAM) is com.hyperlog.app.program.ProgramParser.Result.Valid)
        reader = CoachLineReader(db, programs, zone)
    }

    @After
    fun close() = db.close()

    private suspend fun active() = programs.active.first()!!

    private fun millis(date: LocalDate) = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * A finished session of Day A on [date]: bench at [kg] × 8 at [rir], and the row slot done as
     * [row], 30 kg lighter.
     */
    private suspend fun dayA(date: LocalDate, kg: Double, rir: Int = 2, row: String = "seated_cable_row", easy: Boolean = false, gymId: Long? = null, week: Int = 2) {
        val dao = db.sessions()
        val id = dao.insertSession(SessionEntity(startedAt = millis(date), finishedAt = millis(date) + 3_600_000, programId = active().id, week = week, dayIndex = 0, easy = easy, gymId = gymId))
        val bench = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = "barbell_bench_press", position = 0, slotId = "bench"))
        dao.insertSet(LoggedSetEntity(sessionExerciseId = bench, position = 0, weightKg = kg, fullReps = 8, partialReps = 0, rir = rir, loggedAt = millis(date)))
        val rowId = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = row, position = 1, slotId = "row"))
        dao.insertSet(LoggedSetEntity(sessionExerciseId = rowId, position = 0, weightKg = kg - 30, fullReps = 10, partialReps = 0, rir = 2, loggedAt = millis(date)))
    }

    @Test
    fun zeroRirWhereThePlanAskedForTwoIsGrinding() = runTest {
        (0..2).forEach { dayA(today.minusDays(9L - 3 * it), kg = 80.0 + 2.5 * it, rir = 0) }
        val line = reader.shown(active(), hidden = emptySet())
        assertEquals("barbell_bench_press", (line as CoachLine.Grinding).exercise)
        assertEquals(3, line.sessions)
    }

    @Test
    fun anEasyWeekSessionIsNotCountedInAStall() = runTest {
        dayA(today.minusDays(12), kg = 80.0)
        dayA(today.minusDays(9), kg = 80.0)
        dayA(today.minusDays(6), kg = 80.0, easy = true)
        dayA(today.minusDays(3), kg = 80.0)
        assertNull(reader.shown(active(), hidden = emptySet()))
        dayA(today.minusDays(1), kg = 80.0)
        assertEquals(CoachLine.Stall::class, reader.shown(active(), hidden = emptySet())!!::class)
    }

    @Test
    fun aSessionInAWeekThePlanEasesIsNotCountedInAStall() = runTest {
        // Week 1 is a Ramp-in (+1 RIR).
        dayA(today.minusDays(12), kg = 80.0)
        dayA(today.minusDays(9), kg = 80.0)
        dayA(today.minusDays(6), kg = 80.0, week = 1)
        dayA(today.minusDays(3), kg = 80.0)
        assertNull(reader.shown(active(), hidden = emptySet()))
        dayA(today.minusDays(1), kg = 80.0)
        assertEquals(CoachLine.Stall::class, reader.shown(active(), hidden = emptySet())!!::class)
    }

    @Test
    fun aSlotDoneAsAnotherExerciseThanThePlanIsASwap() = runTest {
        val gym = com.hyperlog.app.gym.Gyms(db).addGym("Harbour")
        (0..2).forEach { dayA(today.minusDays(9L - 3 * it), kg = 80.0 + 2.5 * it, row = "chest_supported_db_row", gymId = gym) }
        val line = reader.shown(active(), hidden = emptySet()) as CoachLine.SameSwap
        assertEquals(CoachLine.SameSwap("row", "seated_cable_row", "chest_supported_db_row", 3, line.last), line)
    }

    @Test
    fun aDayMissedComesFromTheRotationAndTheSessionsOfTheDay() = runTest {
        val mondays = (4 downTo 1).map { today.minusWeeks(it.toLong()).with(java.time.DayOfWeek.MONDAY) }
        dayA(mondays[1], kg = 80.0)
        val missed = listOf(mondays[0], mondays[2], mondays[3]).map { RotationTurn(it, Position(1, 0)) }
        val rotation = Rotation(emptyList(), missed, null, again = false, easy = false, emptyWeeks = 0, anchor = RotationAnchor(mondays[0], 1, 0), next = Position(1, 0))
        val line = reader.shown(active().copy(rotation = rotation, today = today), hidden = emptySet())
        // Named by its letter, without its title.
        assertEquals(CoachLine.DayMissed("a", "Day A", missed = 3, of = 4, last = mondays[3]), line)
    }

    @Test
    fun aPatternOverFourWeeksAgoIsNoLine() = runTest {
        (0..2).forEach { dayA(today.minusDays(40L - 3 * it), kg = 80.0 + 2.5 * it, rir = 0) }
        assertNull(reader.shown(active(), hidden = emptySet()))
    }

    @Test
    fun hidingALineKeepsItHiddenUntilItGrows() = runTest {
        (0..2).forEach { dayA(today.minusDays(9L - 3 * it), kg = 80.0 + 2.5 * it, rir = 0) }
        reader.hide(reader.shown(active(), reader.hidden.first())!!, active())
        assertNull(reader.shown(active(), reader.hidden.first()))
        dayA(today.minusDays(1), kg = 90.0, rir = 0)
        assertEquals(4, (reader.shown(active(), reader.hidden.first()) as CoachLine.Grinding).sessions)
    }

    private companion object {
        val PROGRAM = """
            { "format": 1, "title": "Lines", "blocks": [
              { "id": "one", "name": "One", "weekStart": 1, "weekEnd": 8, "weeks": {"1": {"rirAdd": 1}}, "days": [
                { "id": "a", "name": "Day A — Heavy bench", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 1, "reps": 8, "rir": 2 } ] } ] },
                  { "slots": [ { "id": "row", "exercise": "seated_cable_row", "sets": [ { "count": 1, "reps": "8-10", "rir": 2 } ] } ] } ] },
                { "id": "b", "name": "Day B", "groups": [
                  { "slots": [ { "id": "squat", "exercise": "hack_squat", "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] } ] } ] } ] }
        """
    }
}
