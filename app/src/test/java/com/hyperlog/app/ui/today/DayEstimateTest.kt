package com.hyperlog.app.ui.today

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.PastSessionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** How long a day takes (SPEC, Screens → Today): past sessions first, else the whole session counted. */
class DayEstimateTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program

    private fun day(week: Int, index: Int) = program.plannedDay(Position(week, index))!!
    private fun minutes(day: PlannedDay, finished: List<PastSessionItem> = emptyList()) = aboutMinutes(day, library.exercises, finished)

    private fun countedSecondsOf(day: PlannedDay) = countedSeconds(day, library.exercises)

    private fun session(id: Long, dayIndex: Int, minutes: Int, startedAt: Long = id * 1_000_000_000) = PastSessionItem(
        id = id, startedAt = startedAt, finishedAt = startedAt + minutes * 60_000L, title = "Day", gym = null,
        exercises = emptyList(), programId = 1, position = Position(3, dayIndex),
    )

    @Test
    fun theOwnersDayAInRampInWithNoHistoryTakesAnHour() {
        val a = day(1, 0)
        val swapped = a.copy(slots = a.slots.map { if (it.exercise == "2_grip_lat_pulldown") it.copy(exercise = "cable_lat_pullover") else it })
        assertEquals(7, swapped.slots.size)
        assertEquals(14, swapped.slots.sumOf { it.sets.size })
        assertTrue(minutes(swapped) in 55..65)
    }

    private fun set(rest: Int, technique: Technique? = null) = SetPrescription(RepRange(8, 10), rir = 1, rest = rest, technique = technique)
    private fun slot(id: String, group: Int, vararg sets: SetPrescription, exercise: String = "cable_lateral_raise") =
        PlannedSlot(id, "$group", group, exercise, emptyList(), null, sets.toList())

    /** A made-up day over [slots], all isolation lifts, so each exercise warms up once. */
    private fun dayOf(vararg slots: PlannedSlot) = day(3, 0).copy(slots = slots.toList())

    // One exercise: changeover 90 + warm-up 60 + work 45 per set (the 3000 s rest after the day.s last set is not counted).
    @Test
    fun noRestIsCountedAfterTheDaysLastSet() {
        val oneSet = countedSecondsOf(dayOf(slot("a", 0, set(rest = 3000))))
        assertEquals(90 + 60 + 45, oneSet)
    }

    @Test
    fun restIsCountedBetweenSetsAndBetweenExercises() {
        assertEquals(90 + 60 + 2 * 45 + 100, countedSecondsOf(dayOf(slot("a", 0, set(100), set(100)))))
        assertEquals(2 * (90 + 60 + 45) + 100, countedSecondsOf(dayOf(slot("a", 0, set(100)), slot("b", 1, set(100)))))
    }

    @Test
    fun partnersOfASupersetHaveNoRestBetweenThemAndOneChangeover() {
        // A, B, then the pair's rest (the later partner's) once, before the next exercise.
        val pair = dayOf(slot("a", 0, set(60)), slot("b", 0, set(120)), slot("c", 1, set(90)))
        assertEquals((90 + 60 + 45) + (60 + 45) + 120 + (90 + 60 + 45), countedSecondsOf(pair))
    }

    @Test
    fun aDropSetAddsItsDropsAndNoRestBeforeThem() {
        val plain = countedSecondsOf(dayOf(slot("a", 0, set(100), set(100))))
        val drops = countedSecondsOf(dayOf(slot("a", 0, set(100), set(100, Technique.Dropset(drops = 2)))))
        assertEquals(plain + 60, drops)
        assertEquals(plain + 45, countedSecondsOf(dayOf(slot("a", 0, set(100), set(100, Technique.MyoReps())))))
        assertEquals(plain + 15, countedSecondsOf(dayOf(slot("a", 0, set(100), set(100, Technique.LengthenedPartials())))))
    }

    @Test
    fun aMainLiftWarmsUpThreeTimesAndAnIsolationExerciseOnce() {
        val main = countedSecondsOf(dayOf(slot("a", 0, set(0), exercise = "barbell_bench_press")))
        assertEquals(90 + 3 * 60 + 45, main)
    }

    @Test
    fun finishedSessionsOfTheDayGiveTheMedianOfTheLatestThree() {
        val a = day(3, 0)
        val sessions = listOf(session(1, 0, 90), session(2, 0, 50), session(3, 0, 70), session(4, 0, 60), session(5, 0, 80))
        // The latest three are 3, 4, 5: 70, 60 and 80.
        assertEquals(70, minutes(a, sessions))
    }

    @Test
    fun oneOrTwoSessionsGiveTheirMedianToTheNearestFive() {
        val a = day(3, 0)
        assertEquals(60, minutes(a, listOf(session(1, 0, 62))))
        assertEquals(55, minutes(a, listOf(session(1, 0, 50), session(2, 0, 62))))
    }

    @Test
    fun otherDaysSessionsAreIgnored() {
        val a = day(3, 0)
        val none = minutes(a)
        assertEquals(none, minutes(a, listOf(session(1, 1, 20), session(2, 2, 30))))
        assertEquals(40, minutes(a, listOf(session(1, 1, 20), session(2, 0, 40))))
    }

    @Test
    fun theStarterProgramsDaysInBuildAreNearTheirBudgets() {
        val budgets = listOf(57, 55, 46)
        budgets.forEachIndexed { i, budget ->
            val m = minutes(day(3, i))
            assertTrue("Day ${'A' + i}: $m min against $budget", Math.abs(m - budget) <= 10)
        }
    }
}
