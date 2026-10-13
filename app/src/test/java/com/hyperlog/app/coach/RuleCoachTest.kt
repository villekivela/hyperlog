package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.program.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The offline coach on the starter program (SPEC, Coaching → Rules). The hand-pain case is the
 * one in docs/programs/full-body-3-day.md, "First coaching case".
 */
class RuleCoachTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)

    private fun plan(position: Position = Position(1, 0), changes: List<AcceptedChange> = emptyList()) =
        ActiveProgram(1, program, position, today = oct13).let { a ->
            if (changes.isEmpty()) a else a.copy(history = changes.map { ChangeRecord(it, 0, null) })
        }

    private fun advise(situation: Situation, constraints: Constraints = situation.constraints(), at: Position = Position(1, 0), lastTrained: LocalDate? = oct13.minusDays(2)) =
        RuleCoach.advise(situation, constraints, plan(at), library.exercises, lastTrained)

    private fun proposed(advice: Advice): Change = (advice as? Advice.Propose)?.change ?: error("no Change: $advice")

    @Test
    fun handPainOnTheStarterProgramIsTheProgramDocumentsChange() {
        val change = proposed(advise(Situation.Pain(Area.Hands)))
        assertEquals(emptyList<String>(), ChangeValidator.validate(change, program, ids))
        // Weeks 1–2 of the program, from today.
        assertEquals(Scope.Dates(oct13, oct13.plusDays(13)), change.scope)

        val swaps = change.operations.filterIsInstance<Operation.Swap>().associate { it.slot to it.exercise }
        assertEquals(
            mapOf(
                "bench" to "smith_machine_bench_press",
                "close_grip_bench" to "machine_chest_press",
                "incline_press" to "incline_machine_press",
                "rdl" to "45_hyperextension",
                // Day C's bench has no Smith alternative: the machine chest press, the document's "or".
                "bench_volume" to "machine_chest_press",
            ),
            swaps,
        )
        // Pulls keep their exercise, with straps; the curls are already the cable and machine ones.
        // Only what kept a grip gets lighter: lower body and machines (the Smith included) are untouched.
        val lighter = change.operations.filterIsInstance<Operation.AdjustLoad>().associate { it.slot to it.percent }
        assertEquals(
            setOf("lat_pulldown", "lateral_raise", "overhead_triceps", "curl", "rear_delt", "pressdown"),
            lighter.keys,
        )
        assertTrue(lighter.values.all { it == -10 })
        // Every target +1 RIR.
        assertEquals(listOf(Operation.AdjustRir(null, 1)), change.operations.filterIsInstance<Operation.AdjustRir>().map { it.copy(reason = null) })
        // No drop sets on grip exercises; the incline machine press keeps its own.
        assertEquals(
            setOf("overhead_triceps", "curl"),
            change.operations.filterIsInstance<Operation.RemoveTechnique>().onEach { assertEquals("dropset", it.technique) }.map { it.slot }.toSet(),
        )
    }

    @Test
    fun theHandChangeAsTrainedInBuild() {
        val change = proposed(advise(Situation.Pain(Area.Hands), at = Position(3, 0)))
        val accepted = Plan.accept(program, Position(3, 0), change, oct13)
        val dayC = Plan.day(program, Position(3, 2), oct13.plusDays(4), listOf(accepted))!!
        // The DB incline press goes to the incline machine press, which as a machine is untouched but for the extra RIR.
        val incline = dayC.slot("incline_press")!!
        assertEquals("incline_machine_press", incline.exercise)
        assertEquals(listOf<Technique?>(Technique.Dropset()), incline.sets.map { it.technique })
        assertEquals(listOf(1), incline.sets.map { it.rir })
        assertEquals(0, incline.loadPercent)
        // A cable exercise keeps a grip: lighter, and no drop set.
        val pressdown = dayC.slot("pressdown")!!
        assertEquals(-10, pressdown.loadPercent)
        // Lower body and machines are untouched but for the extra RIR.
        val legPress = dayC.slot("leg_press")!!
        assertEquals(0, legPress.loadPercent)
        assertEquals(Technique.LengthenedPartials(), legPress.sets.last().technique)
        // A drop set on a no-grip exercise would stay; the myo-reps on the preacher curl do.
        assertEquals(Technique.MyoReps(), dayC.slot("preacher_curl")!!.sets.last().technique)
    }

    private fun trained(change: Change, at: Position = Position(1, 0)) =
        Plan.day(program, at, oct13, listOf(Plan.accept(program, at, change, oct13)))!!

    @Test
    fun elevenDaysOffIsAReducedVolumeReEntrySession() {
        val change = proposed(advise(Situation.TimeOff, lastTrained = oct13.minusDays(11)))
        assertEquals(Scope.Dates(oct13, oct13), change.scope)
        assertEquals("Back after 11 days: an easier first session", change.summary)
        // Day A: half the sets, rounded up, wherever there is more than one.
        assertEquals(
            mapOf("bench" to 2, "squat" to 1, "lat_pulldown" to 1, "seated_leg_curl" to 1, "lateral_raise" to 1),
            change.operations.filterIsInstance<Operation.AdjustSets>().associate { it.slot to it.sets },
        )
        assertEquals(1, change.operations.filterIsInstance<Operation.AdjustRir>().single().by)
        assertEquals(2, trained(change).slot("bench")!!.sets.size)
        // The diff shows the one session it covers, not the rest of the week.
        assertEquals(listOf("a"), Plan.diff(program, Position(1, 0), oct13, emptyList(), change).days.map { it.day.day.id })
    }

    @Test
    fun upToAWeekOffResumesAndMoreThanTwoRepeatsTheLastWeek() {
        assertEquals(Advice.Resume(7), advise(Situation.TimeOff, lastTrained = oct13.minusDays(7)))
        assertEquals(Advice.Resume(null), advise(Situation.TimeOff, lastTrained = null))
        val repeat = proposed(advise(Situation.TimeOff, at = Position(4, 1), lastTrained = oct13.minusDays(15)))
        assertEquals(listOf(Operation.RepeatWeek("back after 15 days")), repeat.operations)
        assertEquals(Position(4, 0), Plan.accept(program, Position(4, 1), repeat, oct13).after)
    }

    @Test
    fun withAWeekdayPlanTimeOffCountsFromTheFirstPlannedDayMissed() {
        // 13 Oct 2026 is a Tuesday. Training Tuesdays only, two weeks since the last session is
        // one planned day missed a week ago: resume.
        val tuesdays = setOf(java.time.DayOfWeek.TUESDAY)
        assertEquals(7, RuleCoach.daysOff(oct13.minusDays(14), oct13, tuesdays))
        assertEquals(Advice.Resume(14), RuleCoach.advise(Situation.TimeOff, Constraints(), plan(), library.exercises, oct13.minusDays(14), weekdays = tuesdays))
        // The same gap with no plan is two weeks off.
        assertEquals(14, RuleCoach.daysOff(oct13.minusDays(14), oct13))
        // Today itself isn't missed yet.
        assertEquals(0, RuleCoach.daysOff(oct13.minusDays(7), oct13, tuesdays))

        // Monday, Wednesday and Friday, last trained on Friday 2 Oct: Monday 5 Oct was missed 8 days ago.
        val mwf = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY)
        assertEquals(8, RuleCoach.daysOff(LocalDate.of(2026, 10, 2), oct13, mwf))
        val change = proposed(RuleCoach.advise(Situation.TimeOff, Constraints(), plan(), library.exercises, LocalDate.of(2026, 10, 2), weekdays = mwf))
        // Its words count the days since the last session.
        assertEquals("Back after 11 days: an easier first session", change.summary)
        assertTrue(RuleCoach.timeOff(RuleCoach.daysOff(LocalDate.of(2026, 10, 2), oct13, mwf)))
    }

    @Test
    fun aFinishedProgramHasNoDayLeftForTheRules() {
        val done = ActiveProgram(1, program, Position(program.weeks + 1, 0), today = oct13)
        listOf(Situation.Pain(Area.Hands), Situation.TimeOff, Situation.NoGym, Situation.ShortOnTime(30), Situation.PoorRecovery).forEach {
            assertEquals(Advice.Finished, RuleCoach.advise(it, it.constraints(), done, library.exercises, oct13.minusDays(20)))
        }
    }

    @Test
    fun atHomeEveryExerciseIsDumbbellsOrBodyweightOrLeftOut() {
        val change = proposed(advise(Situation.NoGym))
        val day = trained(change)
        val byId = library.exercises.associateBy { it.id }
        assertTrue(day.slots.isNotEmpty())
        day.slots.forEach { assertTrue(it.exercise, byId.getValue(it.exercise).equipment in Constraints.HomeEquipment) }
        // The lateral raise has a DB alternative.
        assertEquals("db_lateral_raise", day.slot("lateral_raise")!!.exercise)
    }

    @Test
    fun shortOnTimeCutsSetsFromTheEndThenExercises() {
        val full = RuleCoach.minutes(program.plannedDay(Position(1, 0))!!)
        assertTrue("Day A takes $full min", full in 40..70)
        val day = trained(proposed(advise(Situation.ShortOnTime(30))))
        assertTrue(RuleCoach.minutes(day) <= 30)
        // The first exercise always stays.
        assertEquals("bench", day.slots.first().id)
        assertEquals(Advice.NothingToChange, advise(Situation.ShortOnTime(120)))
    }

    @Test
    fun poorRecoveryTakesASetOffAndAddsARir() {
        val change = proposed(advise(Situation.PoorRecovery))
        assertEquals(Scope.Dates(oct13, oct13), change.scope)
        assertEquals(2, change.operations.filterIsInstance<Operation.AdjustSets>().first { it.slot == "bench" }.sets)
        assertEquals(1, change.operations.filterIsInstance<Operation.AdjustRir>().single().by)
    }

    @Test
    fun aSoreShoulderLosesOverheadWorkAndLightensThePresses() {
        val change = proposed(advise(Situation.Pain(Area.Shoulder), at = Position(3, 0)))
        val byId = library.exercises.associateBy { it.id }
        val dayA = Plan.day(program, Position(3, 0), oct13, listOf(Plan.accept(program, Position(3, 0), change, oct13)))!!
        val dayB = Plan.day(program, Position(3, 1), oct13, listOf(Plan.accept(program, Position(3, 0), change, oct13)))!!
        (dayA.slots + dayB.slots).forEach { assertTrue(it.exercise, !byId.getValue(it.exercise).overhead) }
        val lighter = change.operations.filterIsInstance<Operation.AdjustLoad>().map { it.slot }
        assertTrue("bench" in lighter)
        assertTrue("squat" !in lighter)
    }

    @Test
    fun constraintsAloneMakeAChangeAndNothingToChangeWhenTheyHold() {
        val noGrip = proposed(advise(Situation.TimeOff, Constraints(noGrip = true), lastTrained = oct13.minusDays(3)))
        assertTrue(noGrip.operations.contains(Operation.Swap("bench", "smith_machine_bench_press", "no grip")))
        assertEquals(Advice.NothingToChange, advise(Situation.NoGym, Constraints(), lastTrained = oct13))
    }

    @Test
    fun missingEquipmentSwapsFromTheAlternativesThenThePattern() {
        val dayB = program.plannedDay(Position(1, 1))!!
        assertEquals(
            listOf(SessionSwap("leg_extension", "leg_extension", "reverse_nordic")),
            RuleCoach.missingEquipment(dayB, library.exercises, setOf("leg_extension")),
        )
        // With the alternative missing too, another knee extension from the library.
        val other = RuleCoach.missingEquipment(dayB, library.exercises, setOf("leg_extension", "reverse_nordic")).single().replacement
        assertEquals(com.hyperlog.app.library.MovementPattern.KneeExtension, library.exercises.first { it.id == other }.pattern)
    }
}
