package com.hyperlog.app.program

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
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

/** The block review read from the log as stored (SPEC, Program → Block review). */
@RunWith(RobolectricTestRunner::class)
class BlockReviewReaderTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var reader: BlockReviewReader
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
        check(programs.import(PROGRAM) is ProgramParser.Result.Valid)
        reader = BlockReviewReader(db, programs, zone)
    }

    @After
    fun close() = db.close()

    private suspend fun active() = programs.active.first()!!

    private fun millis(date: LocalDate) = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    /** A finished session of Day A in [week] on [date]: bench at [kg] × 8, and the row on [machine] if given. */
    private suspend fun dayA(date: LocalDate, week: Int, kg: Double, machine: Long? = null, finished: Boolean = true): Long {
        val dao = db.sessions()
        val id = dao.insertSession(SessionEntity(startedAt = millis(date), finishedAt = if (finished) millis(date) + 3_600_000 else null, programId = active().id, week = week, dayIndex = 0))
        val bench = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = "barbell_bench_press", position = 0, slotId = "bench"))
        dao.insertSet(LoggedSetEntity(sessionExerciseId = bench, position = 0, weightKg = kg, fullReps = 8, partialReps = 0, rir = 2, loggedAt = millis(date)))
        if (machine != null) {
            val row = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = "seated_cable_row", position = 1, slotId = "row", equipmentItemId = machine))
            dao.insertSet(LoggedSetEntity(sessionExerciseId = row, position = 0, weightKg = kg - 30, fullReps = 10, partialReps = 0, rir = 2, loggedAt = millis(date)))
        }
        return id
    }

    private suspend fun moveTo(week: Int) = db.programs().setPosition(active().id, week, 0)

    private suspend fun review() = reader.page(active().id, "one", whole = false)!!.review

    @Test
    fun aSessionInAWeekThePlanEasesIsLeftOutOfTheLifts() = runTest {
        // Week 1 is a Ramp-in (+1 RIR): only the week 2 session counts, so bench was trained once.
        dayA(today.minusDays(14), week = 1, kg = 80.0)
        dayA(today.minusDays(7), week = 2, kg = 82.5)
        assertEquals(emptyList<String>(), review().lifts.map { it.exercise })
        dayA(today.minusDays(4), week = 2, kg = 85.0)
        assertEquals(listOf("barbell_bench_press"), review().lifts.map { it.exercise })
    }

    @Test
    fun aMachineExerciseIsNamedForTheMachineUsedMost() = runTest {
        val gyms = Gyms(db)
        val gym = gyms.addGym("Harbour")
        val left = gyms.addItem(EquipmentItem(gymId = gym, name = "Cable left", ladder = LadderSpec.DefaultPinStack))
        val right = gyms.addItem(EquipmentItem(gymId = gym, name = "Cable right", ladder = LadderSpec.DefaultPinStack))
        dayA(today.minusDays(10), week = 2, kg = 80.0, machine = left)
        dayA(today.minusDays(8), week = 2, kg = 80.0, machine = right)
        dayA(today.minusDays(6), week = 2, kg = 82.5, machine = left)
        dayA(today.minusDays(4), week = 2, kg = 85.0, machine = left)
        assertEquals("Cable left · Harbour", review().lifts.single { it.exercise == "seated_cable_row" }.machine)
    }

    @Test
    fun theVolumeChangesAndTurningUpComeFromTheRun() = runTest {
        dayA(today.minusDays(14), week = 1, kg = 80.0)
        dayA(today.minusDays(7), week = 2, kg = 82.5)
        programs.accept(com.hyperlog.app.coach.Change("No grip work for a week", com.hyperlog.app.coach.Scope.RestOfBlock, emptyList()))
        val review = review()
        // One set of bench a week, as written: two days of the block, the second not trained.
        assertEquals(MuscleVolume("Chest", done = 1.0, written = 1.0), review.volume.first())
        assertEquals(listOf("No grip work for a week"), review.changes)
        assertEquals(TurningUp.InSequence(2, today.minusDays(14), today.minusDays(7)), review.turningUp)
    }

    @Test
    fun todayShowsAFinishedBlocksReviewUntilItIsOpened() = runTest {
        assertNull(reader.due(active(), reader.opened.first()))
        moveTo(3)
        assertEquals(DueReview(active().id, "one", "One", programDone = false), reader.due(active(), reader.opened.first()))
        reader.open(active().id, "one")
        assertNull(reader.due(active(), reader.opened.first()))
    }

    @Test
    fun todayShowsAFinishedBlocksReviewUntilTheNextBlocksFirstSessionStarts() = runTest {
        moveTo(3)
        dayA(today, week = 3, kg = 85.0, finished = false)
        assertNull(reader.due(active(), reader.opened.first()))
    }

    @Test
    fun aFinishedProgramsReviewIsTheProgramReview() = runTest {
        moveTo(5)
        assertEquals(DueReview(active().id, "two", "Two", programDone = true), reader.due(active(), reader.opened.first()))
        val page = reader.page(active().id, "two", whole = true)!!
        assertEquals("two", page.block.id)
        assertEquals(true, page.whole != null)
    }

    private companion object {
        val PROGRAM = """
            { "format": 1, "title": "Reviews", "blocks": [
              { "id": "one", "name": "One", "weekStart": 1, "weekEnd": 2, "weeks": {"1": {"rirAdd": 1}}, "days": [
                { "id": "a", "name": "Day A — Heavy bench", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 1, "reps": 8, "rir": 2 } ] } ] } ] },
                { "id": "b", "name": "Day B", "groups": [
                  { "slots": [ { "id": "row", "exercise": "seated_cable_row", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] } ] } ] } ] },
              { "id": "two", "name": "Two", "weekStart": 3, "weekEnd": 4, "days": [
                { "id": "a", "name": "Day A", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 1, "reps": 8, "rir": 2 } ] } ] } ] } ] } ] }
        """
    }

    @Test
    fun aProgramReplacedMidWayReviewsTheLastBlockItReached() = runTest {
        moveTo(3)
        val id = active().id
        check(programs.import(PROGRAM) is ProgramParser.Result.Valid)
        val page = reader.page(id, blockId = null, whole = true)!!
        assertEquals("two", page.block.id)
    }
}
