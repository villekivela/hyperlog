package com.hyperlog.app.program

import com.hyperlog.app.library.LibrarySeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled starter program, Hyperlog Full Body, against its design document
 * (docs/programs/full-body-3-day.md).
 */
class StarterProgramTest {
    private val source = File("src/main/assets/${StarterProgram.ASSET}").readText()
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program by lazy {
        val result = ProgramParser.parse(source, ids, library.merged)
        (result as? ProgramParser.Result.Valid)?.program ?: error("starter rejected: $result")
    }

    private fun day(week: Int, day: Int) = program.plannedDay(Position(week, day))!!
    private fun PlannedDay.sets(slot: String) = slot(slot)!!.sets

    @Test
    fun itPassesTheValidator() {
        assertEquals("Hyperlog Full Body", program.title)
        assertEquals("Hyperlog", program.author)
        assertTrue(program.description!!.isNotBlank())
    }

    @Test
    fun everyExerciseIdResolvesInTheLibrary() {
        val used = program.blocks.flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots } } }
            .flatMap { listOf(it.exercise) + it.alternatives.map { a -> a.exercise } }
            .toSet()
        assertEquals(emptySet<String>(), used - ids)
        // Not a merged-away legacy id either: the cleaned library's survivors only.
        assertEquals(emptySet<String>(), used intersect library.merged.keys)
        // The ids the document lists for the program file.
        val documented = setOf(
            "barbell_bench_press", "close_grip_bench_press", "smith_machine_bench_press", "hack_squat", "leg_press",
            "2_grip_lat_pulldown", "machine_pulldown", "seated_leg_curl", "lying_leg_curl", "cable_lateral_raise",
            "overhead_cable_triceps_extension", "barbell_rdl", "45_hyperextension", "chest_supported_machine_row",
            "leg_extension", "bayesian_cable_curl", "machine_preacher_curl",
            "reverse_cable_crossover", "db_incline_press", "incline_machine_press", "triceps_pressdown",
            "seated_calf_raise", "cable_shoulder_press", "cable_flye", "chest_supported_db_row",
            "seated_cable_row",
        )
        assertEquals(emptySet<String>(), documented - used)
    }

    @Test
    fun itRunsTwelveWeeksInTheFourBlocksOfThreeDays() {
        assertEquals(12, program.weeks)
        assertEquals(
            listOf("Ramp-in 1–2", "Build 3–6", "Intensify 7–11", "Deload + test 12–12"),
            program.blocks.map { "${it.name} ${it.weekStart}–${it.weekEnd}" },
        )
        program.blocks.forEach { block ->
            assertEquals(
                listOf("Day A — Heavy bench, squat, pulldown", "Day B — Hinge, close-grip bench, row", "Day C — Bench volume, leg press, pulldown"),
                block.days.map { it.name },
            )
        }
    }

    @Test
    fun buildIsAsWrittenWithTheSupersetsBackOffsAndTechniquesOnTheLastSet() {
        val a = day(3, 0)
        assertEquals(listOf("1", "2", "3", "4", "5a", "5b", "6"), a.slots.map { it.label })
        assertEquals(
            listOf("barbell_bench_press", "hack_squat", "2_grip_lat_pulldown", "seated_leg_curl", "cable_lateral_raise", "overhead_cable_triceps_extension", "seated_calf_raise"),
            a.slots.map { it.exercise },
        )
        // Bench: top set 1 × 4–6 @ 1, then back-off 2 × 8–10 @ 1 at 10% off the top set.
        assertEquals(
            listOf(
                SetPrescription(RepRange(4, 6), 1, 180),
                SetPrescription(RepRange(8, 10), 1, 180, backoff = Backoff(0, 10)),
                SetPrescription(RepRange(8, 10), 1, 180, backoff = Backoff(0, 10)),
            ),
            a.sets("bench"),
        )
        assertEquals(listOf("smith_machine_bench_press"), a.slot("bench")!!.alternatives)
        assertEquals(listOf(null, Technique.LengthenedPartials()), a.sets("lat_pulldown").map { it.technique })
        assertEquals(listOf(0, 0), a.sets("lat_pulldown").map { it.rir })
        assertEquals(listOf(null, Technique.MyoReps()), a.sets("lateral_raise").map { it.technique })
        assertEquals(listOf<Technique?>(Technique.Dropset(drops = 2)), a.sets("overhead_triceps").map { it.technique })

        // The calf raise closes Day A: 2 × 12–15 @ 0, lengthened partials on the last set.
        assertEquals(
            listOf(SetPrescription(RepRange(12, 15), 0, 90), SetPrescription(RepRange(12, 15), 0, 90, technique = Technique.LengthenedPartials())),
            a.sets("calf_raise"),
        )
        assertEquals(listOf("standing_calf_raise", "leg_press_calf_press"), a.slot("calf_raise")!!.alternatives)

        val b = day(3, 1)
        assertEquals(listOf("1", "2", "3", "4", "5a", "5b", "6a", "6b", "7"), b.slots.map { it.label })
        assertEquals(
            listOf("barbell_rdl", "close_grip_bench_press", "chest_supported_machine_row", "leg_extension", "cable_shoulder_press", "cable_lateral_raise", "cable_flye", "reverse_cable_crossover", "bayesian_cable_curl"),
            b.slots.map { it.exercise },
        )
        // The row's first alternative is the seated cable row.
        assertEquals(listOf("seated_cable_row", "chest_supported_db_row"), b.slot("row")!!.alternatives)
        // Cable shoulder press with a cable lateral raise: 1 set each.
        assertEquals(listOf(SetPrescription(RepRange(8, 10), 0, 120)), b.sets("shoulder_press"))
        assertEquals(listOf("machine_shoulder_press", "seated_db_shoulder_press"), b.slot("shoulder_press")!!.alternatives)
        assertEquals(listOf(SetPrescription(RepRange(10, 12), 0, 90, technique = Technique.MyoReps())), b.sets("side_delt_raise"))
        assertEquals(listOf("machine_lateral_raise", "db_lateral_raise"), b.slot("side_delt_raise")!!.alternatives)
        assertEquals(listOf(SetPrescription(RepRange(12, 15), 0, 90, technique = Technique.LengthenedPartials())), b.sets("flye"))
        assertEquals(listOf("pec_deck"), b.slot("flye")!!.alternatives)
        // Pairs (labels 5a/5b, 6a/6b): shoulder press + lateral raise, flye + reverse crossover. The curl is alone, last.
        assertEquals(listOf("shoulder_press", "side_delt_raise", "flye", "rear_delt", "curl"), b.slots.drop(4).map { it.id })
        assertEquals(listOf<Technique?>(Technique.Dropset(drops = 2)), b.sets("curl").map { it.technique })
        assertEquals(listOf(null, Technique.MyoReps()), b.sets("leg_extension").map { it.technique })
        assertEquals(listOf(null, null), b.sets("rdl").map { it.technique })

        val c = day(3, 2)
        // Pec deck and calf raise moved to B and A; pulldown and incline press are a superset.
        assertEquals(listOf("1", "2", "3a", "3b", "4", "5a", "5b"), c.slots.map { it.label })
        assertEquals(listOf("bench_volume", "leg_press", "pulldown", "incline_press", "lying_leg_curl", "preacher_curl", "pressdown"), c.slots.map { it.id })
        assertEquals(listOf<Technique?>(null, null), c.sets("pulldown").map { it.technique })
        assertEquals(listOf<Technique?>(Technique.Dropset(drops = 2)), c.sets("incline_press").map { it.technique })
        // Bench volume: 1 × 6–8 @ 1, then back-off 1 × 8–10 @ 1.
        assertEquals(
            listOf(SetPrescription(RepRange(6, 8), 1, 180), SetPrescription(RepRange(8, 10), 1, 180, backoff = Backoff(0, 10))),
            c.sets("bench_volume"),
        )

        // A technique is only ever on a slot's last set.
        (0..2).map { day(3, it) }.flatMap { it.slots }.forEach { slot ->
            assertTrue(slot.id, slot.sets.dropLast(1).all { it.technique == null })
        }
    }

    @Test
    fun theRampInIsOneRirEasierWithNoFailureAndNoTechniques() {
        for (week in 1..2) for (d in 0..2) {
            val ramp = day(week, d)
            val build = day(3, d)
            assertEquals(build.slots.map { it.id }, ramp.slots.map { it.id })
            ramp.slots.zip(build.slots).forEach { (r, b) ->
                assertEquals(r.id, b.sets.map { it.copy(rir = it.rir!! + 1, technique = null) }, r.sets)
            }
        }
        assertEquals(listOf(2, 2, 2), day(1, 0).sets("bench").map { it.rir })
    }

    @Test
    fun intensifyDropsTheMainLiftsRepsAndKeepsTheTechniques() {
        val a = day(7, 0)
        assertEquals(listOf(RepRange(3, 5), RepRange(8, 10), RepRange(8, 10)), a.sets("bench").map { it.reps })
        assertEquals(listOf(RepRange(5, 7), RepRange(8, 10)), a.sets("squat").map { it.reps })
        assertEquals(listOf(1, 0), a.sets("squat").map { it.rir })
        assertEquals(day(3, 0).sets("lateral_raise"), a.sets("lateral_raise"))
        val b = day(11, 1)
        assertEquals(listOf(RepRange(5, 7), RepRange(5, 7)), b.sets("rdl").map { it.reps })
        assertEquals(listOf(RepRange(5, 7), RepRange(5, 7)), b.sets("close_grip_bench").map { it.reps })
        assertEquals(day(3, 1).sets("curl"), b.sets("curl"))
        val c = day(9, 2)
        assertEquals(listOf(RepRange(5, 7), RepRange(8, 10)), c.sets("bench_volume").map { it.reps })
        assertEquals(Backoff(0, 10), c.sets("bench_volume")[1].backoff)
    }

    @Test
    fun weekTwelveTestsTheBenchWithATopSingleAndDeloadsTheRest() {
        val a = day(12, 0)
        assertEquals(listOf(SetPrescription(RepRange(1, 1), 1, 180)), a.sets("bench"))
        val note = a.slot("bench")!!.note!!
        assertTrue(note, "bench goal" in note && "3%" in note)
        // Days B and C: one set fewer, @ 3 RIR, no techniques.
        for (d in 1..2) {
            val deload = day(12, d)
            val build = day(3, d)
            deload.slots.zip(build.slots).forEach { (dl, b) ->
                assertEquals(dl.id, (b.sets.size - 1).coerceAtLeast(1), dl.sets.size)
                assertTrue(dl.id, dl.sets.all { it.rir == 3 && it.technique == null })
            }
        }
        // The rest of Day A deloads the same way after the single.
        day(12, 0).slots.drop(1).zip(day(3, 0).slots.drop(1)).forEach { (dl, b) ->
            assertEquals(dl.id, (b.sets.size - 1).coerceAtLeast(1), dl.sets.size)
            assertTrue(dl.id, dl.sets.all { it.rir == 3 && it.technique == null })
        }
    }

    @Test
    fun weekTwelveIsMarkedAsADeloadSoItsSetsNeverLowerTheEstimate() {
        assertEquals(listOf(true, true, true), (0..2).map { day(12, it).deload })
        assertTrue((1..11).all { w -> (0..2).none { day(w, it).deload } })
    }

    @Test
    fun theSlotNotesCarryTheProgramsCues() {
        val a = day(3, 0)
        val b = day(3, 1)
        val c = day(3, 2)
        assertEquals("Same grip, setup and pause every rep.", a.slot("bench")!!.note)
        assertEquals("Same grip, setup and pause every rep.", c.slot("bench_volume")!!.note)
        assertEquals("Use straps.", b.slot("rdl")!!.note)
        assertEquals("Use straps.", a.slot("lat_pulldown")!!.note)
        assertEquals("Use straps.", c.slot("pulldown")!!.note)
        assertEquals("Chest on the pad, pull with the elbows.", b.slot("row")!!.note)
        val stretch = "Control the stretch, 1 s pause at the bottom."
        listOf(b.slot("shoulder_press"), c.slot("incline_press"), b.slot("curl"), c.slot("preacher_curl")).forEach {
            assertEquals(it!!.id, stretch, it.note)
        }
    }

    @Test
    fun mainLiftsRestThreeMinutesAndEverythingElseLess() {
        val main = setOf("bench", "squat", "rdl", "close_grip_bench", "row", "bench_volume", "leg_press")
        (0..2).map { day(3, it) }.flatMap { it.slots }.forEach { slot ->
            val rests = slot.sets.map { it.rest }.toSet()
            if (slot.id in main) assertEquals(slot.id, setOf(180), rests)
            else assertTrue(slot.id, rests.all { it in 90..120 })
        }
    }

    @Test
    fun noThirdPartyProgramIsBundled() {
        val bundled = File("src/main/assets/programs").listFiles()!!.map { it.name }
        assertEquals(listOf("full-body-3-day.json"), bundled)
        assertNull(Regex("(?i)nippard").find(source))
    }
}
