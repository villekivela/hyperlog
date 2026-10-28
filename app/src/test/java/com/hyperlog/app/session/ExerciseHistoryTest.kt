package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.data.SetExtensionEntity
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.history.History
import com.hyperlog.app.progression.Record
import java.time.LocalDate
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exercise history (GLOSSARY): an exercise's past sessions as suggestions, records and trends read them. */
@RunWith(RobolectricTestRunner::class)
class ExerciseHistoryTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    /** Bench once a week; week 4 is a deload. */
    private val program = """
        { "format": 1, "title": "History", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4,
            "weeks": { "4": { "rir": 3, "deload": true } },
            "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] } ] } ] } ] } ] }
    """

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        programs.import(program)
    }

    @After
    fun close() = db.close()

    private fun history() = ExerciseHistory(db, programs)

    private suspend fun exercise(id: String): Exercise = db.exercises().get(id)!!.toExercise()

    /** A set of [kg] × [reps] @ [rir]. */
    private data class Sample(val kg: Double, val reps: Int, val rir: Int)

    /** A finished session of the program's week [week] on day [at] (days since the epoch) with [exerciseId] done as [sets]. */
    private suspend fun trained(
        week: Int,
        at: Long,
        vararg sets: Sample = arrayOf(Sample(60.0, 8, 2)),
        exerciseId: String = "barbell_bench_press",
        itemId: Long? = null,
    ): Long {
        val dao = db.sessions()
        val start = at * 86_400_000L + 12 * 3_600_000L
        val id = dao.insertSession(
            SessionEntity(startedAt = start, finishedAt = start + 3_600_000, programId = programs.active.first()!!.id, week = week, dayIndex = 0),
        )
        val se = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = exerciseId, position = 0, slotId = "bench", equipmentItemId = itemId))
        sets.forEachIndexed { p, set ->
            dao.insertSet(LoggedSetEntity(sessionExerciseId = se, position = p, weightKg = set.kg, fullReps = set.reps, partialReps = 0, rir = set.rir, loggedAt = start))
        }
        return id
    }

    @Test
    fun aPastSessionIsADeloadWhenItsWeekWas() = runTest {
        val normal = trained(week = 1, at = 20_000)
        val deload = trained(week = 4, at = 20_021)

        val past = history().of(exercise("barbell_bench_press"), itemId = null)

        assertEquals(listOf(normal, deload), past.map { it.sessionId })
        assertEquals(listOf(false, true), past.map { it.performance.deload })
    }

    @Test
    fun aChangeAcceptedAfterASessionDoesNotMakeItADeload() = runTest {
        val session = trained(week = 1, at = 20_000)
        val day = programs.dateOf(db.sessions().session(session)!!.startedAt)
        now = 30_000L * 86_400_000L
        programs.accept(
            ChangeJson.decode("""{"summary":"Easy week","scope":{"from":"$day","to":"$day"},"operations":[{"op":"deload_week","week":1}]}"""),
        )

        val past = history().of(exercise("barbell_bench_press"), itemId = null)

        assertEquals(listOf(false), past.map { it.performance.deload })
    }

    @Test
    fun machinesAndCablesReadOnlyTheirOwnMachineAndFreeWeightsReadAny() = runTest {
        val gyms = Gyms(db)
        val gym = gyms.addGym("Central")
        val left = gyms.addItem(EquipmentItem(gymId = gym, name = "Left", ladder = LadderSpec.lbStack()))
        val right = gyms.addItem(EquipmentItem(gymId = gym, name = "Right", ladder = LadderSpec.lbStack()))
        val onLeft = trained(week = 1, at = 20_000, exerciseId = "cable_lateral_raise", itemId = left)
        trained(week = 1, at = 20_001, exerciseId = "cable_lateral_raise", itemId = right)
        val onLeftAgain = trained(week = 1, at = 20_002, exerciseId = "cable_lateral_raise", itemId = left)
        val benchOnLeft = trained(week = 1, at = 20_003, itemId = left)
        val benchOnRight = trained(week = 1, at = 20_004, itemId = right)

        assertEquals(listOf(onLeft, onLeftAgain), history().of(exercise("cable_lateral_raise"), itemId = left).map { it.sessionId })
        assertEquals(listOf(benchOnLeft, benchOnRight), history().of(exercise("barbell_bench_press"), itemId = left).map { it.sessionId })
    }

    @Test
    fun aWeightedBodyweightSessionCarriesTheBodyweightOfItsDay() = runTest {
        val bodyweights = Bodyweights(db)
        bodyweights.log(LocalDate.ofEpochDay(20_000), 80.0)
        bodyweights.log(LocalDate.ofEpochDay(20_003), 79.0)
        trained(week = 1, at = 19_990, Sample(10.0, 8, 1), exerciseId = "weighted_pullup")
        trained(week = 1, at = 20_005, Sample(12.0, 8, 1), exerciseId = "weighted_pullup")

        val past = history().of(exercise("weighted_pullup"), itemId = null)

        // The first value logged stands in before it; the sets stay as logged, the load on the belt.
        assertEquals(listOf(80.0, 79.0), past.map { it.performance.bodyweightKg })
        assertEquals(listOf(90.0, 91.0), past.map { it.performance.load(it.logged.single().weightKg) })
    }

    @Test
    fun aSessionReadsTheMainSetOnly() = runTest {
        val session = trained(week = 1, at = 20_000, Sample(60.0, 8, 2))
        val set = db.sessions().sets(db.sessions().sessionExercises(session).single().id).single()
        db.sessions().insertExtension(SetExtensionEntity(loggedSetId = set.id, position = 0, kind = "Drop", weightKg = 40.0, reps = 6))

        val past = history().of(exercise("barbell_bench_press"), itemId = null).single()

        assertEquals(listOf(emptyList<Extension>()), past.performance.sets.map { it.set.extensions })
        assertEquals(1, past.logged.single().extensions.size)
    }

    @Test
    fun aSessionReadsTheSessionsBeforeAnotherOnly() = runTest {
        val first = trained(week = 1, at = 20_000)
        val second = trained(week = 2, at = 20_007)
        trained(week = 3, at = 20_014)

        val past = history().of(exercise("barbell_bench_press"), itemId = null, before = db.sessions().session(second))

        assertEquals(listOf(first), past.map { it.sessionId })
    }

    @Test
    fun aRecordBadgeAndTheTrendsRecordAgreeAfterADeloadWeek() = runTest {
        // Two normal weeks teach a constant of 30 (one pair of sets); the deload week would teach 40.
        trained(week = 1, at = 20_000, Sample(100.0, 5, 1), Sample(80.0, 10, 1))
        trained(week = 4, at = 20_007, Sample(50.0, 5, 1), Sample(47.5, 9, 1), Sample(48.75, 7, 1))
        // 85 × 12 @ 1 beats 100 × 5 @ 1 at 30 (121.8 against 120) and not at 40 (112.6 against 115).
        val last = trained(week = 1, at = 20_014, Sample(85.0, 12, 1))
        val row = db.sessions().session(last)!!
        val se = db.sessions().sessionExercises(last).single()
        val sets = stored().sets(se.id)

        val badge = SessionRecords(db, programs).of(row, se, exercise("barbell_bench_press"), sets, deload = false)
        val trend = History(db, programs).trend("barbell_bench_press").lines.single().points.last().record

        assertEquals(listOf(Record.Estimate), badge)
        assertEquals(Record.Estimate, trend)
    }

    @Test
    fun aChangeAcceptedAfterwardsLeavesATrendsRecordsAsTheyWere() = runTest {
        trained(week = 1, at = 20_000, Sample(100.0, 5, 1), Sample(80.0, 10, 1))
        val middle = trained(week = 2, at = 20_007, Sample(50.0, 5, 1), Sample(47.5, 9, 1), Sample(48.75, 7, 1))
        trained(week = 3, at = 20_014, Sample(85.0, 12, 1))
        val before = History(db, programs).trend("barbell_bench_press").lines.single().points.map { it.record }
        val day = programs.dateOf(db.sessions().session(middle)!!.startedAt)
        now = 30_000L * 86_400_000L
        programs.accept(
            ChangeJson.decode("""{"summary":"Easy week","scope":{"from":"$day","to":"$day"},"operations":[{"op":"deload_week","week":2}]}"""),
        )

        val after = History(db, programs).trend("barbell_bench_press").lines.single().points.map { it.record }

        assertEquals(before, after)
    }

    private fun stored() = StoredSessions(db, Gyms(db), programs)
}
