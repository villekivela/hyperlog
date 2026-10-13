package com.hyperlog.app.library

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibrarySeedTest {
    private val seed = LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext())
    private val byId = seed.exercises.associateBy { it.id }

    @Test
    fun holdsTheLegacyLibraryWithDuplicatesMerged() {
        // 189 legacy exercises, 16 of them merged into others.
        assertEquals(173, seed.exercises.size)
        assertEquals(16, seed.merged.size)
        assertEquals("db_incline_press", seed.merged["incline_db_press"])
        assertEquals("triceps_pressdown", seed.merged["tricep_pushdown"])
        seed.merged.forEach { (old, kept) ->
            assertTrue("$old is merged, so it isn't in the library", old !in byId)
            assertTrue("$old merges into $kept, which is in the library", kept in byId)
        }
        assertEquals(seed.exercises.size, byId.size)
        assertEquals("names tell exercises apart", seed.exercises.size, seed.exercises.map { it.name }.toSet().size)
    }

    @Test
    fun everyExerciseHasEquipmentPatternMusclesAndGripDemand() {
        seed.exercises.forEach { assertTrue("${it.id} has muscles", it.muscles.isNotEmpty()) }
        val bench = byId.getValue("barbell_bench_press")
        assertEquals("Barbell Bench Press", bench.name)
        assertEquals(Equipment.Barbell, bench.equipment)
        assertEquals(MovementPattern.HorizontalPush, bench.pattern)
        // Free-weight presses hold and balance the whole load in the hands (#17), so they are high.
        assertEquals(GripDemand.High, bench.grip)
        assertEquals(GripDemand.Low, byId.getValue("smith_machine_bench_press").grip)
        assertEquals(listOf("Chest"), bench.muscles)
        // Fixed from the legacy data: curls were "vertical pulls", the wide-grip pulldown a machine.
        assertEquals(MovementPattern.ElbowFlexion, byId.getValue("db_bicep_curl").pattern)
        assertEquals(Equipment.Cable, byId.getValue("lat_pulldown_wide_grip").equipment)
        assertEquals(GripDemand.None, byId.getValue("leg_extension").grip)
        assertEquals(GripDemand.High, byId.getValue("barbell_rdl").grip)
    }

    @Test
    fun dumbbellExercisesSayWhetherTheyUseTwo() {
        val two = seed.exercises.filter { it.twoDumbbells }.map { it.id }.toSet()
        assertTrue("db_bench_press" in two)
        assertTrue("db_lateral_raise" in two)
        assertTrue("seated_db_shoulder_press" in two)
        // One dumbbell: held in both hands, or one arm at a time.
        listOf("goblet_squat", "single_arm_db_row", "overhead_db_triceps_extension").forEach { assertTrue(it, it !in two) }
        // Only dumbbell exercises use two.
        seed.exercises.filter { it.twoDumbbells }.forEach { assertEquals(it.id, com.hyperlog.app.library.Equipment.Dumbbell, it.equipment) }
    }

    @Test
    fun holdsEveryExerciseTheStarterProgramNames() {
        val program = listOf(
            "barbell_bench_press", "close_grip_bench_press", "smith_machine_bench_press", "hack_squat", "leg_press",
            "2_grip_lat_pulldown", "machine_pulldown", "seated_leg_curl", "lying_leg_curl", "cable_lateral_raise",
            "overhead_cable_triceps_extension", "barbell_rdl", "45_hyperextension", "chest_supported_machine_row",
            "leg_extension", "machine_shoulder_press", "bayesian_cable_curl", "machine_preacher_curl",
            "reverse_cable_crossover", "db_incline_press", "incline_machine_press", "pec_deck", "triceps_pressdown",
            "seated_calf_raise", "smith_machine_squat", "1_arm_cable_pulldown", "machine_lateral_raise",
            "db_lateral_raise", "overhead_db_triceps_extension", "db_romanian_deadlift", "machine_chest_press",
            "db_bench_press", "chest_supported_db_row", "seated_cable_row", "reverse_nordic", "cable_shoulder_press",
            "seated_db_shoulder_press", "db_incline_curl", "1_arm_reverse_pec_deck", "lying_reverse_db_flye",
            "neutral_grip_pullup", "smith_machine_incline_press", "cable_flye", "cable_triceps_kickback",
            "standing_calf_raise", "leg_press_calf_press",
        )
        program.forEach { assertNotNull("$it is in the library", byId[it]) }
    }

    @Test
    fun noNoteCarriesProgramAdvice() {
        val advice = Regex("(?i)drop ?set|myo|partial|each week|last set|first set|second set|amrap|sbs|\\brir\\b|instead do")
        seed.exercises.forEach { e ->
            e.note?.let { assertTrue("${e.id}: $it", !advice.containsMatchIn(it)) }
        }
        // Cleaned, not dropped: the cue after the legacy "Dropset: …" prefix stays.
        assertEquals("Round your back as you crunch.", byId.getValue("cable_crunch").note)
    }

    @Test
    fun everyVideoIsAYouTubeLink() {
        val videos = seed.exercises.mapNotNull { it.video }
        assertEquals(138, videos.size)
        videos.forEach { assertNotNull(it, TechniqueVideo.parse(it)) }
        assertEquals(653, TechniqueVideo.parse(byId.getValue("cable_lateral_raise").video!!)!!.startSeconds)
    }
}
