package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.MissingSwaps
import com.hyperlog.app.gym.SessionSwap
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** A gym without a leg extension: picking it swaps the exercise for that session only (SPEC, Missing equipment). */
@RunWith(RobolectricTestRunner::class)
class MissingEquipmentTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var gyms: Gyms
    private val seed = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private var now = 1_760_000_000_000L
    private var gymId = 0L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(seed)
        programs = Programs(db, clock = { now })
        programs.import(File("src/main/assets/${StarterProgram.ASSET}").readText())
        db.programs().setPosition(active().id, 1, 1) // Day B, with the leg extension
        gyms = Gyms(db)
        gymId = gyms.addGym("Harbour")
    }

    @After
    fun close() = db.close()

    private suspend fun active() = programs.active.first()!!
    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    @Test
    fun pickingAGymWithoutALegExtensionSwapsItForThatSessionOnly() = runTest {
        gyms.markMissing(gymId, "leg_extension")
        val gym = gyms.gym(gymId)!!
        val swaps = MissingSwaps.forDay(active().next!!.day, seed.exercises, gym.missing)
        assertEquals(listOf(SessionSwap("leg_extension", "leg_extension", "reverse_nordic")), swaps)

        val logger = logger()
        logger.startDay(active(), gymId, swaps.associate { it.slot to it.replacement!! })
        val legs = logger.state.value!!.exercises.single { it.slot?.id == "leg_extension" }
        assertEquals("reverse_nordic", legs.exercise.id)
        assertEquals("leg_extension", legs.inPlaceOf)

        // The plan is as written: no Change, and next week's Day B has the leg extension again.
        assertTrue(active().history.isEmpty())
        assertEquals("leg_extension", programs.plannedDay(active().id, Position(2, 1), programs.dateOf(now))!!.slot("leg_extension")!!.exercise)
    }

    @Test
    fun notAtThisGymMarksItMissingAndSwapsTheExercise() = runTest {
        val logger = logger()
        logger.startDay(active(), gymId)
        val legs = logger.state.value!!.exercises.single { it.slot?.id == "leg_extension" }
        logger.switchTo(legs.id)
        assertTrue(logger.state.value!!.current!!.needsMachine)

        logger.notAtGym()

        assertEquals(setOf("leg_extension"), gyms.gym(gymId)!!.missing)
        val swapped = logger.state.value!!.exercises.single { it.slot?.id == "leg_extension" }
        assertEquals("reverse_nordic", swapped.exercise.id)
        assertEquals(swapped.id, logger.state.value!!.current!!.id)
        gyms.unmarkMissing(gymId, "leg_extension")
        assertEquals(emptySet<String>(), gyms.gym(gymId)!!.missing)
    }

    @Test
    fun anAdHocExerciseNotAtTheGymIsSwappedByItsMovementPattern() = runTest {
        val logger = logger()
        logger.start(gymId)
        logger.addExercise("leg_extension")
        logger.notAtGym()
        val swapped = logger.state.value!!.current!!
        assertEquals(com.hyperlog.app.library.MovementPattern.KneeExtension, swapped.exercise.pattern)
        assertTrue(swapped.exercise.id != "leg_extension")
    }
}
