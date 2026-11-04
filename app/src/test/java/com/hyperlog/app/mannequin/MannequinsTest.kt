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
        // The mannequin's mesh and skin are shared by every clip, and the barbell by every barbell clip; each reads them by name.
        assertEquals(mapOf("mannequin.bin" to "mannequins/mannequin.bin", "kit_barbell.bin" to "mannequins/kit_barbell.bin"), clip.resources)
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
        assertNull(mannequins.clipFor(library.getValue("barbell_lunge")))
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
                "1_arm_cable_press_around", "1_arm_katana_cable_triceps_extension", "2_grip_lat_pulldown",
                "back_squat_high_bar", "back_squat_low_bar", "barbell_bench_press", "barbell_curl",
                "barbell_glute_bridge", "barbell_hip_thrust", "barbell_rdl", "barbell_row", "barbell_shrug",
                "barbell_squat", "barbell_upright_row", "bayesian_cable_curl", "behind_the_back_cable_lateral_raise",
                "cable_chest_press", "cable_crossover", "cable_flye", "cable_lateral_raise", "cable_shoulder_press",
                "cable_shrug_in", "cable_y_raise", "chest_supported_machine_row", "chest_supported_t_bar_row",
                "close_grip_bench_press", "close_grip_lat_pulldown", "db_incline_press", "deadlift", "deficit_deadlift",
                "deficit_pendlay_row", "floor_press", "front_squat", "hack_squat", "high_cable_lateral_raise",
                "kelso_shrug", "lat_pulldown", "leg_extension", "leg_press", "low_to_high_cable_press", "lying_leg_curl",
                "machine_chest_press", "machine_preacher_curl", "machine_pulldown", "overhead_cable_triceps_extension",
                "reverse_cable_crossover", "seated_calf_raise", "seated_leg_curl", "t_bar_row", "triceps_pressdown",
            ),
            mannequins.clips.map { it.exerciseId },
        )
    }
}
