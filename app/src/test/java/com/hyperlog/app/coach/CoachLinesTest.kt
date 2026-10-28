package com.hyperlog.app.coach

import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Group
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The patterns the coach line points at, each at its threshold (SPEC, Coaching → The coach line). */
class CoachLinesTest {
    private val monday = LocalDate.of(2026, 9, 7)

    /** Day A's turns a week apart, oldest first: true where it was missed. */
    private fun dayA(vararg missed: Boolean) =
        missed.mapIndexed { i, m -> DayTurn(monday.plusWeeks(i.toLong()), "a", "Day A", m) }

    @Test
    fun aDayMissedThreeOfItsLastFourTurnsIsALine() {
        val lines = CoachLines.of(CoachLog(turns = dayA(false, true, false, true, true)))
        assertEquals(listOf(CoachLine.DayMissed("a", "Day A", missed = 3, of = 4, last = monday.plusWeeks(4))), lines)
    }

    @Test
    fun twoMissesInTheLastFourTurnsAreNot() {
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(turns = dayA(true, true, false, true, false))))
    }

    /** Session [n]'s date (from 1), sessions 3 days apart. */
    private fun on(n: Int) = monday.plusDays(3L * (n - 1))

    /** Bench sessions a few days apart, oldest first, each with the estimate after it. */
    private fun bench(vararg estimates: Double, eased: Set<Int> = emptySet()) = estimates.mapIndexed { i, kg ->
        LoggedSession(on(i + 1), eased = i + 1 in eased, exercises = listOf(LoggedExercise("bench", estimateKg = kg)))
    }

    @Test
    fun anEstimateFlatOrDownOverThreeSessionsIsAStall() {
        val lines = CoachLines.of(CoachLog(sessions = bench(100.0, 102.5, 102.5, 101.0, 102.5)))
        assertEquals(listOf(CoachLine.Stall("bench", sessions = 3, last = on(5))), lines)
    }

    @Test
    fun twoSessionsWithoutGainAreNotAStall() {
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = bench(100.0, 100.0, 102.5, 102.5, 102.5))))
    }

    @Test
    fun aStallGrowsWithEachSessionWithoutGain() {
        val lines = CoachLines.of(CoachLog(sessions = bench(100.0, 100.0, 100.0, 100.0, 100.0)))
        assertEquals(listOf(CoachLine.Stall("bench", sessions = 4, last = on(5))), lines)
    }

    @Test
    fun aStallIsMeasuredFromTheEstimateBeforeTheSessions() {
        // Moving up a weight dips the estimate; two sessions under it are no stall yet.
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = bench(127.0, 123.0, 126.0))))
        // Noise around a flat estimate is three sessions without beating it.
        assertEquals(listOf(CoachLine.Stall("bench", sessions = 3, last = on(4))), CoachLines.of(CoachLog(sessions = bench(127.0, 125.0, 127.0, 125.0))))
    }

    @Test
    fun deloadAndEasyWeekSessionsAreNotCountedInAStall() {
        // Sessions 3 and 4 are a deload and an easy week: counted, they'd make three sessions without
        // gain after session 2; left out, one follows it.
        val eased = CoachLog(sessions = bench(100.0, 102.5, 102.5, 102.5, 102.5, eased = setOf(3, 4)))
        assertEquals(emptyList<CoachLine>(), CoachLines.of(eased))
        // Skipped, not breaking the run: the third counted session after them makes it a stall.
        val more = CoachLog(sessions = bench(100.0, 102.5, 102.5, 90.0, 102.5, 102.5, eased = setOf(4)))
        assertEquals(listOf(CoachLine.Stall("bench", sessions = 3, last = on(6))), CoachLines.of(more))
    }

    /** Leg press sessions, oldest first, each one set as `rir to target`. */
    private fun legPress(vararg sets: Pair<Int?, Int?>) = sets.mapIndexed { i, (rir, target) ->
        LoggedSession(on(i + 1), eased = false, exercises = listOf(LoggedExercise("leg-press", sets = listOf(RirSet(2, 2), RirSet(rir, target)))))
    }

    @Test
    fun aSetAtZeroRirWhereTheTargetWasTwoInEachOfTheLastThreeSessionsIsGrinding() {
        val lines = CoachLines.of(CoachLog(sessions = legPress(1 to 2, 0 to 2, 0 to 3, 0 to 2)))
        assertEquals(listOf(CoachLine.Grinding("leg-press", sessions = 3, last = on(4))), lines)
    }

    @Test
    fun zeroRirAtATargetOfOneOrInTwoSessionsIsNotGrinding() {
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = legPress(0 to 1, 0 to 2, 0 to 2))))
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = legPress(0 to 2, 1 to 2, 0 to 2, 0 to 2))))
        // No target (added ad hoc) is no grinding either.
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = legPress(0 to null, 0 to 2, 0 to 2))))
    }

    /** Sessions with slot "a2" (incline press) done as each exercise given. */
    private fun slotA2(vararg done: String) = done.mapIndexed { i, exercise ->
        val swapped = exercise != "incline-press"
        LoggedSession(on(i + 1), eased = false, exercises = listOf(LoggedExercise(exercise, slot = "a2", inPlaceOf = "incline-press".takeIf { swapped })))
    }

    @Test
    fun theSameSwapThreeSessionsInARowIsALine() {
        val lines = CoachLines.of(CoachLog(sessions = slotA2("incline-press", "incline-db", "incline-db", "incline-db")))
        assertEquals(listOf(CoachLine.SameSwap("a2", "incline-press", "incline-db", sessions = 3, last = on(4))), lines)
    }

    @Test
    fun twoOfTheSameSwapOrDifferentSwapsAreNot() {
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = slotA2("incline-db", "incline-press", "incline-db", "incline-db"))))
        assertEquals(emptyList<CoachLine>(), CoachLines.of(CoachLog(sessions = slotA2("incline-smith", "incline-db", "incline-db"))))
    }

    private fun slot(id: String, exercise: String) = Slot(id, exercise, emptyList(), null, emptyList())

    /** Day A benches and inclines, Day B leg presses. */
    private val program = Program(
        "Full body", null,
        listOf(
            Block(
                "build", "Build", 1, 8,
                listOf(
                    Day("a", "Day A", listOf(Group(false, listOf(slot("a1", "bench"), slot("a2", "incline-press"))))),
                    Day("b", "Day B", listOf(Group(false, listOf(slot("b1", "leg-press"))))),
                ),
            ),
        ),
    )

    private val missed = CoachLine.DayMissed("a", "Day A", missed = 3, of = 4, last = monday)
    private val stall = CoachLine.Stall("bench", sessions = 3, last = on(5))
    private val grinding = CoachLine.Grinding("leg-press", sessions = 3, last = on(5))
    private val swap = CoachLine.SameSwap("a2", "incline-press", "incline-db", sessions = 3, last = on(5))
    private val all = listOf(missed, stall, grinding, swap)

    private fun change(vararg operations: Operation) = Change("A change", Scope.RestOfProgram, operations.toList())

    @Test
    fun oneLineShowsAtATimeTheFirstInOrderNotHidden() {
        assertEquals(missed, CoachLines.shown(all, hidden = emptySet(), inForce = emptyList(), program))
        assertEquals(stall, CoachLines.shown(all, hidden = setOf(missed.key), inForce = emptyList(), program))
        assertNull(CoachLines.shown(all, hidden = all.map { it.key }.toSet(), inForce = emptyList(), program))
    }

    @Test
    fun aHiddenLineComesBackWhenItsPatternGrows() {
        val hidden = setOf(stall.key)
        assertNull(CoachLines.shown(listOf(stall), hidden, emptyList(), program))
        val grown = stall.copy(sessions = 4, last = on(6))
        assertEquals(grown, CoachLines.shown(listOf(grown), hidden, emptyList(), program))
        // Missed again, the day's line is new too.
        val again = missed.copy(missed = 4, last = monday.plusWeeks(1))
        assertEquals(again, CoachLines.shown(listOf(again), setOf(missed.key), emptyList(), program))
    }

    @Test
    fun aChangeInForceHidesTheLineOfItsExerciseOrDay() {
        // A load change on bench's slot covers the bench stall; the day's missed line goes with any edit to Day A.
        val bench = change(Operation.AdjustLoad("a1", percent = -10))
        assertEquals(grinding, CoachLines.shown(all, emptySet(), listOf(bench), program))
        // A skipped day covers every day's line; a swap of the slot covers its swap line.
        assertEquals(stall, CoachLines.shown(all, emptySet(), listOf(change(Operation.SkipDay())), program))
        assertEquals(stall, CoachLines.shown(listOf(swap, stall), emptySet(), listOf(change(Operation.Swap("a2", "incline-db"))), program))
        // A change to leg press touches nothing on Day A, so the missed line stays.
        assertEquals(missed, CoachLines.shown(all, emptySet(), listOf(change(Operation.AdjustRir("b1", by = 1))), program))
        // One on every slot covers every exercise.
        assertEquals(missed, CoachLines.shown(all, emptySet(), listOf(change(Operation.AdjustRir(by = 1))), program))
        assertEquals(swap, CoachLines.shown(listOf(stall, grinding, swap), emptySet(), listOf(change(Operation.AdjustRir(by = 1))), program))
    }
}
