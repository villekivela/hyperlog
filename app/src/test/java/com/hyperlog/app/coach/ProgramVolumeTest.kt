package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.MuscleGroup
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Group
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetSpec
import com.hyperlog.app.program.Slot
import com.hyperlog.app.program.WeekOverride
import com.hyperlog.app.program.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A program's hard sets a week per muscle against the range (SPEC, Coaching → New program → Sets
 * per muscle), counted as History counts them.
 */
class ProgramVolumeTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText()).exercises.associateBy { it.id }
    private val standing = NumberRange(8, 14)

    private fun slot(exercise: String, sets: Int) =
        Slot(exercise, exercise, emptyList(), null, listOf(SetSpec(count = sets, reps = RepRange(8, 10), rir = 1)))

    private fun program(
        vararg slots: Slot,
        focus: List<MuscleGroup> = emptyList(),
        weeks: List<WeekOverride> = emptyList(),
        targets: ProgramTrainingTargets = ProgramTrainingTargets(),
    ) = Program(
        "Full body", null,
        listOf(Block("b", "Build", 1, 4, listOf(Day("a", "A", slots.map { Group(false, listOf(it)) })), weeks)),
        focus = focus, targets = targets,
    )

    private fun volume(program: Program, extra: Map<String, com.hyperlog.app.library.Exercise> = emptyMap()) =
        ProgramVolume.of(program, library + extra, standing)

    @Test
    fun aMuscleCountsTheSetsOfEveryExerciseThatWorksIt() {
        // History's worked example: bench 4 sets and laterals 1 give Chest 4, Side Delts 1, Front Delts 2.5.
        val rows = volume(program(slot("barbell_bench_press", 4), slot("db_lateral_raise", 1), slot("leg_extension", 6)))
        // Only a muscle a program exercise has as a primary is listed, in body order; Front Delts and the triceps are not.
        assertEquals(listOf("Chest", "Side Delts", "Quads"), rows.map { it.muscle })
        assertEquals(listOf(4.0, 1.0, 6.0), rows.map { it.sets })
    }

    @Test
    fun aSecondaryMuscleAnotherExerciseTrainsAddsItsHalfSets() {
        // Front Delts is primary in the press (2 sets) and a secondary of the bench (4 sets at half).
        val rows = volume(program(slot("barbell_bench_press", 4), slot("machine_shoulder_press", 2)))
        val frontDelts = rows.single { it.muscle == "Front Delts" }
        assertEquals(4.0, frontDelts.sets, 0.0)
    }

    @Test
    fun withoutAFocusEveryMuscleIsMeasuredAgainstTheRange() {
        val rows = volume(program(slot("barbell_bench_press", 4), slot("leg_extension", 10), slot("seated_leg_curl", 15)))
        assertEquals(listOf(VolumeStatus.Under, VolumeStatus.Within, VolumeStatus.Over), rows.map { it.status })
        assertEquals(List(3) { NumberRange(8, 14) }, rows.map { it.range })
        assertEquals(listOf(false, false, false), rows.map { it.focused })
    }

    @Test
    fun aFocusedGroupsMusclesAreMeasuredAgainstTheRaisedRangeAndTheRestAgainstMaintenance() {
        val rows = volume(program(slot("barbell_bench_press", 10), slot("leg_extension", 6), focus = listOf(MuscleGroup.Chest)))
        val chest = rows.single { it.muscle == "Chest" }
        assertEquals(NumberRange(14, 21), chest.range)
        assertEquals(true, chest.focused)
        assertEquals(VolumeStatus.Under, chest.status)
        val quads = rows.single { it.muscle == "Quads" }
        // Maintenance for 8–14 is 4–8: six sets are within it, though under the range.
        assertEquals(NumberRange(4, 8), quads.range)
        assertEquals(false, quads.focused)
        assertEquals(VolumeStatus.Within, quads.status)
    }

    @Test
    fun theProgramsOwnVolumeIsTheRangeInPlaceOfTheStandingOne() {
        val rows = volume(program(slot("leg_extension", 6), targets = ProgramTrainingTargets(volume = NumberRange(4, 6))))
        assertEquals(NumberRange(4, 6), rows.single().range)
        assertEquals(VolumeStatus.Within, rows.single().status)
    }

    @Test
    fun theWeekCountedIsTheFirstThatIsNotEasedOrADeload() {
        // Week 1 eases in with a set fewer; week 2 is the program at full volume.
        val rows = volume(program(slot("leg_extension", 6), weeks = listOf(WeekOverride(WeekRange(1, 1), rirAdd = 1, fewerSets = 1))))
        assertEquals(6.0, rows.single().sets, 0.0)
    }

    @Test
    fun aChangesReviewReadsTheWeekOfTheNextDayBeforeAndAfterTheChangeApplies() {
        val active = ActiveProgram(1, program(slot("barbell_bench_press", 4), slot("leg_extension", 6)), Position(1, 0))
        val change = Change("Quads drop to 4 sets", Scope.RestOfBlock, listOf(Operation.AdjustSets("leg_extension", 4)))

        val volume = ProgramVolume.change(active, change, library, standing)!!

        assertEquals(mapOf("Chest" to 4.0, "Quads" to 6.0), volume.before)
        assertEquals(listOf("Chest" to 4.0, "Quads" to 4.0), volume.rows.map { it.muscle to it.sets })
        assertEquals(VolumeStatus.Under, volume.rows.last().status)
    }

    @Test
    fun aChangeThatLeavesEveryMusclesSetsAloneHasNoVolumeToShow() {
        val active = ActiveProgram(1, program(slot("leg_extension", 6)), Position(1, 0))
        val change = Change("Lighter", Scope.RestOfBlock, listOf(Operation.AdjustLoad("leg_extension", -10)))

        assertEquals(null, ProgramVolume.change(active, change, library, standing))
    }
}
