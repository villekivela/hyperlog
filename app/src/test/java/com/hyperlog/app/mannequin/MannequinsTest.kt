package com.hyperlog.app.mannequin

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.LibrarySeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Which exercise gets which mannequin clip, from the shipped index (SPEC, Technique videos → The mannequin). */
class MannequinsTest {
    private val mannequins = Mannequins.parse(File("src/main/assets/mannequins/mannequins.json").readText())
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText()).exercises.associateBy { it.id }
    private val squat = library.getValue("barbell_squat")

    @Test
    fun theSquatPlaysItsClip() {
        val clip = mannequins.clipFor(squat)!!
        assertEquals("mannequins/barbell_squat.glb", clip.file)
        // The mannequin's mesh and skin are shared by every clip, which reads them by name.
        assertEquals(mapOf("mannequin.bin" to "mannequins/mannequin.bin"), clip.resources)
        assertEquals(StageCamera(target = listOf(0f, 0.87f, 0f), halfHeight = 1.02f, azimuth = 62f, elevation = 8f), clip.camera)
    }

    @Test
    fun eachClipPlaysAtItsExercisesTempo() {
        assertEquals(RepTimeline(lowering = 2f, bottom = 0.25f, lifting = 1f, top = 0.7f), mannequins.clipFor(squat)!!.tempo)
        // The pulldown's clip starts with the pull, the concentric, so its lowering is the quick half.
        assertEquals(RepTimeline(lowering = 1f, bottom = 0.3f, lifting = 2f, top = 0.5f), mannequins.clipFor(library.getValue("lat_pulldown"))!!.tempo)
    }

    @Test
    fun anExerciseWithoutAPoseHasNone() {
        assertNull(mannequins.clipFor(library.getValue("deadlift")))
    }

    @Test
    fun aShippedExerciseWhoseEquipmentIsEditedLosesIt() {
        assertNull(mannequins.clipFor(squat.copy(equipment = Equipment.SmithMachine)))
    }

    @Test
    fun anExerciseAddedOnThePhoneHasNone() {
        // Added from the library editor under the name, its id is made from the name, never a shipped one.
        assertNull(mannequins.clipFor(squat.copy(id = "barbell_back_squat")))
    }

    @Test
    fun everyClipIsForAShippedExerciseWithItsEquipment() {
        mannequins.clips.forEach { clip ->
            assertEquals(clip.exerciseId, library[clip.exerciseId]?.equipment, clip.equipment)
        }
        assertEquals(
            listOf(
                "barbell_bench_press", "barbell_rdl", "barbell_squat", "close_grip_lat_pulldown", "lat_pulldown",
                "leg_press", "machine_chest_press", "triceps_pressdown",
            ),
            mannequins.clips.map { it.exerciseId },
        )
    }
}
