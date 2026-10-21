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
        // 189 legacy exercises, 16 of them merged into others, 3 added since and 37 from Nippard's tier lists.
        assertEquals(213, seed.exercises.size)
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
    fun holdsTheCablePressesAndRopeHammerCurlAddedAfterTheLegacyLibrary() {
        val press = byId.getValue("cable_chest_press")
        assertEquals("Cable Chest Press", press.name)
        assertEquals(Equipment.Cable, press.equipment)
        assertEquals(MovementPattern.HorizontalPush, press.pattern)
        assertEquals(GripDemand.Low, press.grip)
        assertEquals(listOf("Chest"), press.muscles)
        assertEquals(listOf("Front Delts", "Triceps Long Head", "Triceps Lateral Head"), press.secondary)

        val lowToHigh = byId.getValue("low_to_high_cable_press")
        assertEquals("Low-to-High Cable Press", lowToHigh.name)
        assertEquals(Equipment.Cable, lowToHigh.equipment)
        assertEquals(MovementPattern.HorizontalPush, lowToHigh.pattern)
        assertEquals(GripDemand.Low, lowToHigh.grip)
        assertEquals(listOf("Chest", "Front Delts"), lowToHigh.muscles)

        val curl = byId.getValue("cable_rope_hammer_curl")
        assertEquals("Cable Rope Hammer Curl", curl.name)
        assertEquals(Equipment.Cable, curl.equipment)
        assertEquals(MovementPattern.ElbowFlexion, curl.pattern)
        assertEquals(GripDemand.Low, curl.grip)
        assertEquals(listOf("Brachialis", "Biceps"), curl.muscles)
        assertEquals(listOf("Forearm Flexors", "Forearm Extensors"), curl.secondary)

        listOf(press, lowToHigh, curl).forEach { assertNotNull("${it.id} has a technique note", it.note) }
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
        assertEquals(177, videos.size)
        videos.forEach { assertNotNull(it, TechniqueVideo.parse(it)) }
        assertEquals(653, TechniqueVideo.parse(byId.getValue("cable_lateral_raise").video!!)!!.startSeconds)
    }

    @Test
    fun holdsNippardsTierListExercises() {
        listOf(
            // Chest, back, shoulders
            "machine_chest_press", "seated_cable_flye", "cable_crossover", "1_arm_cable_press_around", "dip",
            "deficit_push_up", "db_guillotine_press", "lat_pulldown_wide_grip", "neutral_grip_lat_pulldown",
            "1_arm_cable_pulldown", "meadows_row", "wide_grip_seated_cable_row", "1_arm_cable_lat_pull_around",
            "deficit_pendlay_row", "1_arm_db_kroc_row", "db_pullover", "cable_lateral_raise", "cable_y_raise",
            "behind_the_back_cable_lateral_raise", "reverse_pec_deck", "reverse_cable_crossover",
            "standing_machine_lateral_raise", "1_arm_lean_in_db_lateral_raise", "1_arm_side_lying_db_lateral_raise",
            // Arms, legs
            "bayesian_cable_curl", "db_preacher_curl", "machine_preacher_curl", "preacher_hammer_curl", "lying_db_curl",
            "overhead_cable_triceps_extension", "skull_crusher", "1_arm_katana_cable_triceps_extension",
            "1_arm_overhead_db_triceps_extension", "db_skull_crusher", "smith_machine_jm_press", "hack_squat",
            "pendulum_squat", "db_bulgarian_split_squat", "db_walking_lunge", "machine_hip_abduction",
            "ffe_smith_machine_lunge", "1_leg_db_hip_thrust", "1_leg_cable_glute_kickback",
            // Seven "no one does", and the top 25
            "incline_db_y_raise", "dead_hang", "dragon_flag", "seated_cable_deadlift", "machine_lat_pullover", "db_shrug",
            "pullup",
        ).forEach { assertNotNull("$it is in the library", byId[it]) }
        assertEquals("Incline DB Y-Raise", byId.getValue("incline_db_y_raise").name)
        // One-sided ones say so in the name, so their sets split into left and right.
        listOf("1_arm_db_kroc_row", "1_leg_db_hip_thrust", "1_leg_cable_glute_kickback", "ffe_smith_machine_lunge")
            .forEach { assertTrue(it, byId.getValue(it).unilateral) }
    }

    @Test
    fun namesPutModifiersThenTheImplementThenTheMovement() {
        // Named machine stations and named lifts keep their short names (the owner's call, 2026-10-21).
        val shortNames = setOf(
            "Pec Deck", "Reverse Pec Deck", "1-Arm Reverse Pec Deck", "Leg Press", "1-Leg Leg Press",
            "Leg Press Calf Press", "Leg Extension", "Lying Leg Curl", "Seated Leg Curl", "Hack Squat",
            "Pendulum Squat", "Seated Calf Raise", "Standing Calf Raise", "Donkey Calf Raise", "Glute-Ham Raise",
            "T-Bar Row", "Chest-Supported T-Bar Row", "Lat Pulldown", "Wide-Grip Lat Pulldown",
            "Close-Grip Lat Pulldown", "Neutral-Grip Lat Pulldown", "2-Grip Lat Pulldown", "Triceps Pressdown",
            "Deadlift", "Deficit Deadlift", "Sumo Deadlift", "Rack Pull", "Front Squat", "High-Bar Back Squat",
            "Low-Bar Back Squat", "Pause Squat", "Pin Squat", "Zercher Squat", "Pendlay Row", "Deficit Pendlay Row",
            "Meadows Row", "Floor Press", "Push Press", "Spoto Press", "Larsen Press", "Z-Press", "JM Press",
            "Goblet Squat", "Inverse Zottman Curl", "Modified Zottman Curl", "45° Hyperextension",
        )
        val implement = mapOf(
            Equipment.Barbell to "Barbell", Equipment.Dumbbell to "DB", Equipment.Machine to "Machine",
            Equipment.Cable to "Cable", Equipment.SmithMachine to "Smith Machine", Equipment.EzBar to "EZ-Bar",
            Equipment.TrapBar to "Trap Bar", Equipment.SafetyBar to "Safety Bar", Equipment.Plate to "Plate",
            Equipment.WeightedBodyweight to "Weighted",
        )
        val modifiers = listOf(
            "1-Arm", "1-Leg", "Incline", "Decline", "Seated", "Standing", "Lying", "Close-Grip", "Wide-Grip",
            "Neutral-Grip", "Chest-Supported", "Bent-Over", "Side-Lying", "Lean-In", "Lean-Away",
        )
        seed.exercises.filter { it.name !in shortNames }.forEach { e ->
            val word = implement[e.equipment] ?: return@forEach
            assertTrue("${e.name} names its implement, $word", word in e.name)
            val after = e.name.substringAfter(word)
            modifiers.forEach { assertTrue("${e.name}: $it goes before $word", it !in after) }
        }
        assertEquals("Incline DB Press", byId.getValue("db_incline_press").name)
        assertEquals("Machine Hip Abduction", byId.getValue("machine_hip_abduction").name)
    }
}
