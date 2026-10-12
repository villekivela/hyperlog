package com.hyperlog.app.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseLibraryTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var library: ExerciseLibrary

    private val bench = Exercise(
        "barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush,
        GripDemand.Low, listOf("Chest"), listOf("Front Delts"), "https://youtu.be/abcdefgh", "Pause on the chest.",
    )
    private val curl = Exercise(
        "db_bicep_curl", "DB Curl", Equipment.Dumbbell, MovementPattern.ElbowFlexion, GripDemand.High, listOf("Biceps"),
    )
    private val seed = LibrarySeed(listOf(bench, curl), merged = emptyMap())

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = ExerciseLibrary(db)
    }

    @After
    fun close() = db.close()

    private suspend fun all() = library.exercises.first()

    @Test
    fun syncFillsTheLibraryFromTheSeed() = runTest {
        library.sync(seed)
        assertEquals(listOf(bench, curl), all())
    }

    @Test
    fun addsAnExerciseWithAnIdMadeFromItsName() = runTest {
        library.sync(seed)
        val id = library.add(curl.copy(id = "", name = "Cable Curl (rope)", equipment = Equipment.Cable))
        assertEquals("cable_curl_rope", id)
        // A second one with the same name gets its own id.
        assertEquals("cable_curl_rope_2", library.add(curl.copy(id = "", name = "Cable curl rope")))
        assertEquals(listOf("Barbell Bench Press", "Cable Curl (rope)", "Cable curl rope", "DB Curl"), all().map { it.name })
    }

    @Test
    fun editsAreKeptWhenTheSeedSyncsAgain() = runTest {
        library.sync(seed)
        library.update(bench.copy(note = "My own cue.", muscles = listOf("Chest", "Triceps Long Head")))
        library.sync(LibrarySeed(listOf(bench.copy(note = "A newer cue."), curl.copy(note = "Newer curl cue.")), emptyMap()))

        val (b, c) = all()
        assertEquals("My own cue.", b.note)
        assertEquals(listOf("Chest", "Triceps Long Head"), b.muscles)
        // Exercises the user never edited follow the seed.
        assertEquals("Newer curl cue.", c.note)
    }

    @Test
    fun hiddenExercisesStayHiddenAndCanComeBack() = runTest {
        library.sync(seed)
        library.setHidden("db_bicep_curl", true)
        library.sync(seed)
        assertTrue(all().single { it.id == "db_bicep_curl" }.hidden)

        library.setHidden("db_bicep_curl", false)
        assertEquals(listOf(false, false), all().map { it.hidden })
    }

    @Test
    fun searchMatchesEveryWordInTheNameEquipmentOrMuscles() {
        val list = listOf(bench, curl, curl.copy(id = "c", name = "Cable Curl", equipment = Equipment.Cable))
        assertEquals(listOf("DB Curl", "Cable Curl"), list.search("curl").map { it.name })
        assertEquals(listOf("Cable Curl"), list.search("cable biceps").map { it.name })
        assertEquals(listOf("Barbell Bench Press"), list.search("chest").map { it.name })
        // Names starting with the query come first.
        val curls = listOf(curl.copy(name = "DB Spider Curl"), curl.copy(name = "Curl Machine"))
        assertEquals(listOf("Curl Machine", "DB Spider Curl"), curls.search("curl").map { it.name })
        assertEquals(list, list.search("  "))
        // Exercises named for the query come before ones that only match a muscle.
        val raises = listOf(
            curl.copy(id = "dip", name = "Weighted Dip", muscles = listOf("Triceps Lateral Head")),
            curl.copy(id = "raise", name = "Cable Lateral Raise", muscles = listOf("Side Delts")),
        )
        assertEquals(listOf("Cable Lateral Raise", "Weighted Dip"), raises.search("lateral").map { it.name })
    }
}
