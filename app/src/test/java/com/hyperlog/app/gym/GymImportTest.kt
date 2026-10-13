package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
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
import java.time.DayOfWeek

/** Settings → Gyms and equipment → Import gym adds the gym in a file, never touching one already there. */
@RunWith(RobolectricTestRunner::class)
class GymImportTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var gyms: Gyms

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.parse(File("src/main/assets/exercises.json").readText()))
        gyms = Gyms(db)
    }

    @After
    fun close() = db.close()

    private val file = """
        { "format": "hyperlog-gym", "version": 1, "name": "Harbour", "usualDays": ["monday"], "pinPlates": [2.5],
          "dumbbells": "1 2 … 10 12.5 15 … 45",
          "bars": [ { "name": "EZ bar", "kind": "ez_bar", "kg": 8 }, { "name": "Swiss bar", "kind": "swiss_bar", "kg": 15 } ],
          "equipment": [
            { "name": "Leg extension", "kind": "stack", "serves": ["leg_extension"] },
            { "name": "Crossover", "kind": "stack", "cableStation": true, "serves": ["cable_lateral_raise"],
              "ladder": { "kind": "pin", "unit": "kg", "plates": "5 10 … 100" } },
            { "name": "Leg press", "kind": "plate_loaded", "serves": ["leg_press"] },
            { "name": "Squat rack", "kind": "rack_bench" }
          ] }
    """.trimIndent()

    private suspend fun import(text: String = file): Long = (gyms.import(text) as Gyms.Import.Added).gymId

    @Test
    fun importingAddsTheGymBesideTheOnesThere() = runTest {
        val own = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = own, name = "Cable", ladder = LadderSpec.lbStack()))
        val id = import()
        assertTrue(id != own)
        assertEquals(listOf("Cable"), gyms.gym(own)!!.items.map { it.name })
        assertEquals(2, gyms.gyms.first().size)
    }

    @Test
    fun theGymComesWithItsDaysPlatesRackAndBars() = runTest {
        val gym = gyms.gym(import())!!
        assertEquals(setOf(DayOfWeek.MONDAY), gym.weekdays)
        assertEquals(listOf(2.5), gym.pinPlates)
        assertEquals(24, gym.dumbbells.ladder().rungs.size)
        assertEquals(listOf("EZ bar", "Squat rack", "Swiss bar"), gym.kit.map { it.name })
    }

    @Test
    fun itsMachinesAreRememberedAndAGuessAsksOnFirstUse() = runTest {
        val id = import()
        val gym = gyms.gym(id)!!
        val extension = gym.items.single { it.name == "Leg extension" }
        assertEquals(LadderState.Unconfirmed, extension.state)
        assertEquals(LadderState.Confirmed, gym.items.single { it.name == "Crossover" }.state)
        assertEquals(extension.id, gyms.remembered(id, "leg_extension"))
        // The leg press loads the shared plates.
        assertEquals(LadderSpec.DefaultPlates.pairs, (gym.items.single { it.name == "Leg press" }.ladder as LadderSpec.Plates).pairs)

        val logger = SessionLogger(db).also { it.load() }
        logger.start(id)
        logger.addExercise("leg_extension")
        assertTrue(logger.state.value!!.current!!.needsLadder)
        // Any cable exercise goes to the station.
        logger.addExercise("triceps_pressdown")
        assertEquals("Crossover", logger.state.value!!.current!!.machine!!.name)
    }

    @Test
    fun aGymsOwnBarWeighsWhatItsFileSays() = runTest {
        val id = import()
        val logger = SessionLogger(db).also { it.load() }
        logger.start(id)
        logger.addExercise(db.exercises().all().first { it.equipment == Equipment.EzBar.name }.id)
        assertEquals(8.0, logger.state.value!!.current!!.plates!!.baseKg, 0.0)
    }

    @Test
    fun anInvalidFileAddsNothing() = runTest {
        val result = gyms.import("""{ "format": "hyperlog-gym", "version": 1, "name": "X", "equipment": [ { "name": "A", "kind": "stack", "serves": ["nope"] } ] }""")
        assertEquals(Gyms.Import.Invalid(listOf("equipment[0].serves[0]: unknown exercise \"nope\"")), result)
        assertEquals(emptyList<Gym>(), gyms.gyms.first())
    }
}
