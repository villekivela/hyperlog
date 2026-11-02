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
        assertEquals("mannequins/barbell_squat.glb", clip.asset)
        assertEquals(StageCamera(target = listOf(0f, 0.92f, 0f), half = 1.02f, azimuth = 62f, elevation = 8f), clip.camera)
    }

    @Test
    fun anExerciseWithoutAPoseHasNone() {
        assertNull(mannequins.clipFor(library.getValue("barbell_bench_press")))
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
        assertEquals(listOf("barbell_squat"), mannequins.clips.map { it.exerciseId })
    }
}
