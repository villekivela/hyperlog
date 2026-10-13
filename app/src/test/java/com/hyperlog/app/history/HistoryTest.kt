package com.hyperlog.app.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.data.SetExtensionEntity
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.summary
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** History (SPEC, Screens → History): weekly sets per muscle and each exercise's trend. */
@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private val zone = ZoneId.of("Europe/Helsinki")

    /** Tuesday 13 Oct 2026. */
    private val today = LocalDate.of(2026, 10, 13)

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { 0L })
    }

    @After
    fun close() = db.close()

    private fun history() = History(db, programs, zone = zone, today = { today })

    private fun at(date: LocalDate, hour: Int = 18) = LocalDateTime.of(date, LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private class Logged(
        val kg: Double,
        val reps: Int,
        val rir: Int,
        val partials: Int = 0,
        val drops: List<Pair<Double, Int>> = emptyList(),
        val miniSets: List<Int> = emptyList(),
    )

    /** A session on [date] with [exercises] (exercise id to its sets), done on equipment [items] where given. */
    private suspend fun session(
        date: LocalDate,
        vararg exercises: Pair<String, List<Logged>>,
        items: Map<String, Long> = emptyMap(),
        finished: Boolean = true,
        hour: Int = 18,
    ): Long {
        val dao = db.sessions()
        val start = at(date, hour)
        val id = dao.insertSession(SessionEntity(startedAt = start, finishedAt = if (finished) start + 3_600_000 else null))
        exercises.forEachIndexed { i, (exerciseId, sets) ->
            val se = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = exerciseId, position = i, equipmentItemId = items[exerciseId]))
            sets.forEachIndexed { p, s ->
                val set = dao.insertSet(
                    LoggedSetEntity(sessionExerciseId = se, position = p, weightKg = s.kg, fullReps = s.reps, partialReps = s.partials, rir = s.rir, loggedAt = start),
                )
                s.drops.forEachIndexed { d, (kg, reps) -> dao.insertExtension(SetExtensionEntity(loggedSetId = set, position = d, kind = "Drop", weightKg = kg, reps = reps)) }
                s.miniSets.forEachIndexed { m, reps -> dao.insertExtension(SetExtensionEntity(loggedSetId = set, position = m, kind = "MiniSet", weightKg = s.kg, reps = reps)) }
            }
        }
        return id
    }

    @Test
    fun weeklySetsCountEachWorkSetOnceForItsPrimaryMuscles() = runTest {
        // This week (Mon 12 Oct): bench 3 sets, one with partials and one a drop set; laterals as myo-reps.
        session(
            LocalDate.of(2026, 10, 12),
            "barbell_bench_press" to listOf(
                Logged(80.0, 6, 1),
                Logged(80.0, 6, 0, partials = 4),
                Logged(70.0, 8, 0, drops = listOf(55.0 to 7, 40.0 to 6)),
            ),
            "db_lateral_raise" to listOf(Logged(10.0, 15, 1, miniSets = listOf(4, 4, 3))),
        )
        // A set done entirely as lengthened partials still counts as one.
        session(today, "db_bench_press" to listOf(Logged(30.0, 0, 0, partials = 12)))
        // Last week, late on Sunday; a session still running doesn't count yet.
        session(LocalDate.of(2026, 10, 11), "leg_extension" to listOf(Logged(50.0, 12, 1), Logged(50.0, 11, 0)), hour = 23)
        session(today, "leg_extension" to listOf(Logged(50.0, 12, 1)), finished = false)

        val weeks = history().weeklySets(weeks = 2)

        assertEquals(listOf(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 5)), weeks.map { it.start })
        assertEquals(mapOf("Chest" to 4, "Side Delts" to 1), weeks[0].sets)
        assertEquals(mapOf("Quads" to 2), weeks[1].sets)
    }

    @Test
    fun aFreeWeightTrendShowsTheTopSetAndTheEstimateAfterEachSession() = runTest {
        val gyms = Gyms(db)
        val harbour = gyms.addGym("Harbour")
        val riverside = gyms.addGym("Riverside")
        session(LocalDate.of(2026, 9, 28), "barbell_bench_press" to listOf(Logged(80.0, 5, 1), Logged(80.0, 6, 1)))
        session(LocalDate.of(2026, 10, 1), "barbell_bench_press" to listOf(Logged(82.5, 5, 1), Logged(75.0, 7, 2)))
        // An easy session: its sets at 4 RIR can't lower the estimate.
        session(LocalDate.of(2026, 10, 5), "barbell_bench_press" to listOf(Logged(60.0, 5, 4)))
        db.sessions().finishedSessions().forEachIndexed { i, s -> db.sessions().updateSession(s.copy(gymId = if (i == 0) harbour else riverside)) }

        val trend = history().trend("barbell_bench_press")

        // Free weights share one history across gyms.
        val line = trend.lines.single()
        assertEquals(null, line.machine)
        assertEquals(listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), line.points.map { it.date })
        assertEquals(listOf("80 × 6 @ 1", "82.5 × 5 @ 1", "60 × 5 @ 4"), line.points.map { it.top.summary() })
        // Epley: 80 × (1 + 7/30) = 98.7, then 82.5 × (1 + 6/30) = 99, which the easy session keeps.
        assertEquals(listOf(98.67, 99.0, 99.0), line.points.map { (it.estimateKg!! * 100).roundToInt() / 100.0 })
    }

    @Test
    fun aTwoDumbbellEstimateCountsBothDumbbells() = runTest {
        // Logged per dumbbell: 30 kg each is 60 kg lifted, so 60 × (1 + 9/30) = 78.
        session(LocalDate.of(2026, 10, 5), "db_bench_press" to listOf(Logged(30.0, 8, 1)), "goblet_squat" to listOf(Logged(30.0, 8, 1)))
        assertEquals(78.0, history().trend("db_bench_press").lines.single().points.single().estimateKg!!, 0.001)
        assertEquals("30 × 8 @ 1", history().trend("db_bench_press").lines.single().points.single().top.summary())
        // A goblet squat holds one.
        assertEquals(39.0, history().trend("goblet_squat").lines.single().points.single().estimateKg!!, 0.001)
    }

    @Test
    fun aWeightedBodyweightEstimateAddsTheBodyweightOfTheDay() = runTest {
        Bodyweights(db).log(LocalDate.of(2026, 9, 30), 80.0)
        Bodyweights(db).log(LocalDate.of(2026, 10, 4), 79.0)
        session(LocalDate.of(2026, 9, 28), "weighted_pullup" to listOf(Logged(10.0, 8, 1)))
        session(LocalDate.of(2026, 10, 5), "weighted_pullup" to listOf(Logged(12.0, 8, 1)))

        val line = history().trend("weighted_pullup").lines.single()

        // The top set stays as logged, the load on the belt.
        assertEquals(listOf("10 × 8 @ 1", "12 × 8 @ 1"), line.points.map { it.top.summary() })
        // Before the first value logged it takes that one: 90 × (1 + 9/30) = 117; then 91 × 1.3 = 118.3 at 79 kg.
        assertEquals(listOf(117.0, 118.3), line.points.map { (it.estimateKg!! * 100).roundToInt() / 100.0 })
    }

    @Test
    fun theExercisesTrainedComeLatestFirstWithTheirTrends() = runTest {
        session(LocalDate.of(2026, 9, 28), "barbell_bench_press" to listOf(Logged(80.0, 6, 1)), "leg_extension" to listOf(Logged(50.0, 12, 1)))
        session(LocalDate.of(2026, 10, 1), "barbell_bench_press" to listOf(Logged(82.5, 5, 1)))
        // Nothing logged: not trained.
        session(LocalDate.of(2026, 10, 2), "db_lateral_raise" to emptyList())

        val trends = history().exercises()

        assertEquals(listOf("Barbell Bench Press", "Leg Extension"), trends.map { it.exercise.name })
        assertEquals(2, trends[0].lines.single().points.size)
    }

    @Test
    fun aTrendCarriesItsExercisesLiftGoal() = runTest {
        session(LocalDate.of(2026, 10, 1), "barbell_bench_press" to listOf(Logged(82.5, 5, 1)))
        session(LocalDate.of(2026, 10, 1), "pendlay_row" to listOf(Logged(70.0, 8, 1)))
        LiftGoals(db).set("barbell_bench_press", 100.0, by = LocalDate.of(2026, 12, 31))

        assertEquals(LiftGoal("barbell_bench_press", 100.0, LocalDate.of(2026, 12, 31)), history().trend("barbell_bench_press").goal)
        assertEquals(null, history().trend("pendlay_row").goal)
        assertEquals(listOf(true, false), history().exercises().sortedBy { it.exercise.id }.map { it.goal != null })
    }

    @Test
    fun eachGoalComesWithTheEstimateAfterEverySession() = runTest {
        session(LocalDate.of(2026, 9, 28), "barbell_bench_press" to listOf(Logged(80.0, 5, 1)))
        session(LocalDate.of(2026, 10, 1), "barbell_bench_press" to listOf(Logged(82.5, 5, 1)))
        LiftGoals(db).set("barbell_bench_press", 100.0, by = null)
        // Not trained yet: the goal stands with no estimate.
        LiftGoals(db).set("barbell_squat", 140.0, by = null)

        val goals = history().goals().associateBy { it.goal.exerciseId }

        // Epley on reps + RIR: 80 × (1 + 6/30) = 96, then 82.5 × 1.2 = 99.
        assertEquals(listOf(96.0, 99.0), goals.getValue("barbell_bench_press").points.map { it.estimateKg })
        assertEquals(99.0, goals.getValue("barbell_bench_press").estimateKg!!, 0.001)
        assertEquals(null, goals.getValue("barbell_squat").estimateKg)
    }

    @Test
    fun machineAndPlainBodyweightExercisesTakeNoGoal() = runTest {
        val library = db.exercises()
        assertEquals(true, library.get("barbell_bench_press")!!.toExercise().takesGoal)
        assertEquals(true, library.get("weighted_pullup")!!.toExercise().takesGoal)
        // A machine's estimate is per machine (ADR 0002), and a plain bodyweight exercise has reps, not an estimate.
        assertEquals(false, library.get("cable_lateral_raise")!!.toExercise().takesGoal)
        assertEquals(false, library.all().map { it.toExercise() }.first { it.equipment == Equipment.Bodyweight }.takesGoal)
    }

    @Test
    fun aMachineTrendHasOneLinePerMachine() = runTest {
        val gyms = Gyms(db)
        val harbour = gyms.addGym("Harbour")
        val riverside = gyms.addGym("Riverside")
        val left = gyms.addItem(EquipmentItem(gymId = harbour, name = "Cable left", ladder = LadderSpec.lbStack()))
        val tall = gyms.addItem(EquipmentItem(gymId = riverside, name = "Tall cable", ladder = LadderSpec.lbStack()))
        session(LocalDate.of(2026, 9, 28), "cable_lateral_raise" to listOf(Logged(5.7, 12, 1)), items = mapOf("cable_lateral_raise" to left))
        session(LocalDate.of(2026, 10, 1), "cable_lateral_raise" to listOf(Logged(10.2, 10, 1)), items = mapOf("cable_lateral_raise" to tall))
        session(LocalDate.of(2026, 10, 5), "cable_lateral_raise" to listOf(Logged(7.9, 10, 1)), items = mapOf("cable_lateral_raise" to left))

        val trend = history().trend("cable_lateral_raise")

        // Latest used first; stacks don't compare, so each machine is its own line (ADR 0002).
        assertEquals(listOf("Cable left · Harbour", "Tall cable · Riverside"), trend.lines.map { it.machine })
        assertEquals(listOf("5.7 × 12 @ 1", "7.9 × 10 @ 1"), trend.lines[0].points.map { it.top.summary() })
        assertEquals(listOf("10.2 × 10 @ 1"), trend.lines[1].points.map { it.top.summary() })
    }

    @Test
    fun deletingAMachineOrItsGymKeepsItsHistoryApart() = runTest {
        val gyms = Gyms(db)
        val harbour = gyms.addGym("Harbour")
        val riverside = gyms.addGym("Riverside")
        val left = gyms.addItem(EquipmentItem(gymId = harbour, name = "Cable left", ladder = LadderSpec.lbStack()))
        val tall = gyms.addItem(EquipmentItem(gymId = riverside, name = "Tall cable", ladder = LadderSpec.lbStack()))
        session(LocalDate.of(2026, 9, 28), "cable_lateral_raise" to listOf(Logged(5.7, 12, 1)), items = mapOf("cable_lateral_raise" to left))
        session(LocalDate.of(2026, 10, 1), "cable_lateral_raise" to listOf(Logged(10.2, 10, 1)), items = mapOf("cable_lateral_raise" to tall))
        // A session with no gym: its machine isn't known.
        session(LocalDate.of(2026, 10, 3), "cable_lateral_raise" to listOf(Logged(20.0, 10, 1)))

        gyms.deleteItem(left)
        gyms.deleteGym(riverside)

        // Gone from setup, but each machine's sessions stay its own line (ADR 0002), never the no-gym pool.
        assertEquals(listOf("Harbour"), gyms.gyms.first().map { it.name })
        assertEquals(emptyList<EquipmentItem>(), gyms.gyms.first().single().items)
        val lines = history().trend("cable_lateral_raise").lines
        assertEquals(listOf(null, "Tall cable · Riverside", "Cable left · Harbour"), lines.map { it.machine })
        assertEquals(listOf(1, 1, 1), lines.map { it.points.size })
    }
}
