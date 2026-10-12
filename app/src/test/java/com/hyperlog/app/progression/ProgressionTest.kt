package com.hyperlog.app.progression

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.program.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progression rules (SPEC, Automatic progressive overload), on ladders with odd steps: a
 * machine whose rungs aren't evenly spaced, an lb cable stack shown in kg, and barbell plates.
 */
class ProgressionTest {
    /** A plate-loaded machine with uneven gaps: 1.5, 2.5, 2.5, 3.5 and 5 kg. */
    private val machine = WeightLadder(listOf(40.0, 41.5, 44.0, 46.5, 50.0, 55.0))
    /** An lb stack shown in kg: 1.1 · 3.4 · 5.7 · 7.9 …, jumps of 40–200% at the light end. */
    private val cable = LadderSpec.lbStack().ladder()
    /** A 20 kg bar with the shared plates: 2.5 kg steps. */
    private val plates = LadderSpec.DefaultPlates.ladder()

    private fun planned(reps: String, rir: Int?, count: Int = 1): List<PlannedSet> {
        val (min, max) = reps.split("-").map { it.toInt() }.let { it.first() to it.last() }
        return List(count) { PlannedSet(RepRange(min, max), rir, rest = 120) }
    }

    private fun set(kg: Double, reps: Int, rir: Int, partials: Int = 0) = LoggedSet(kg, reps, partials, rir)

    /** A past session of these sets, done under [plan]. */
    private fun session(plan: List<PlannedSet>?, vararg sets: LoggedSet, deload: Boolean = false) =
        Performance(sets.mapIndexed { i, s -> WorkSet(s, plan?.getOrNull(i)) }, deload)

    private fun next(
        plan: List<PlannedSet>,
        ladder: WeightLadder,
        vararg history: Performance,
        today: List<LoggedSet> = emptyList(),
        deload: Boolean = false,
    ) = Progression.suggest(plan, Performance(today.mapIndexed { i, s -> WorkSet(s, plan.getOrNull(i)) }, deload), history.toList(), ladder)

    @Test
    fun everySetAtTheTopOfTheRangeAtTheTargetRirMovesUpOneRungAndSaysWhy() {
        val plan = planned("8-10", 2, count = 2)
        val s = next(plan, machine, session(plan, set(41.5, 10, 2), set(41.5, 10, 2)))!!
        assertEquals(44.0, s.kg, 0.001)
        assertEquals(Suggestion.Kind.Up, s.kind)
        assertEquals("Last time 41.5 kg × 10, 10 @ 2, top of 8–10 → 44 kg", s.why)
    }

    @Test
    fun aCableJumpTooBigForTheRangeHoldsTheWeightAndTargetsOneMoreRep() {
        val plan = planned("10-12", 0, count = 2)
        val s = next(plan, cable, session(plan, set(5.7, 12, 0), set(5.7, 12, 0)))!!
        assertEquals(5.7, s.kg, 0.001)
        assertEquals(13, s.reps)
        assertEquals(Suggestion.Kind.More, s.kind)
        assertEquals("Last time 5.7 kg × 12, 12 @ 0, top of 10–12; 7.9 kg is too big a jump → 13 reps", s.why)
    }

    @Test
    fun whenNeitherTheNextRungNorMoreRepsWorksItSaysProgressIsntPossible() {
        val plan = planned("10-12", 0)
        val pastRange = next(plan, cable, session(plan, set(5.7, 14, 0)))!!
        assertEquals(Suggestion.Kind.Stuck, pastRange.kind)
        assertEquals(5.7, pastRange.kg, 0.001)
        assertTrue(pastRange.why!!, "progress isn't possible" in pastRange.why!!)
        val heaviest = next(planned("8-10", 2), machine, session(planned("8-10", 2), set(55.0, 10, 2)))!!
        assertEquals(Suggestion.Kind.Stuck, heaviest.kind)
        assertEquals(55.0, heaviest.kg, 0.001)
    }

    @Test
    fun fixedRepsMoveUpWhenBeatenByOneOrMoreAndHoldWhenJustHit() {
        val plan = planned("5", 2)
        val beaten = next(plan, plates, session(plan, set(100.0, 5, 3)))!!
        assertEquals(Suggestion.Kind.Up, beaten.kind)
        assertEquals(102.5, beaten.kg, 0.001)
        assertEquals(5, beaten.reps)
        assertEquals(102.5, next(plan, plates, session(plan, set(100.0, 6, 2)))!!.kg, 0.001)
        val hit = next(plan, plates, session(plan, set(100.0, 5, 2)))!!
        assertEquals(Suggestion.Kind.More, hit.kind)
        assertEquals(100.0, hit.kg, 0.001)
    }

    @Test
    fun aFailureSetBeatsThePreviousRepsThenMovesUpAtTheTopOfTheRange() {
        val plan = planned("10-12", 0)
        val more = next(plan, machine, session(plan, set(44.0, 11, 0)))!!
        assertEquals(44.0 to 12, more.kg to more.reps)
        val up = next(plan, machine, session(plan, set(44.0, 12, 0)))!!
        assertEquals(46.5 to 10, up.kg to up.reps)
    }

    @Test
    fun oneRepShortKeepsTheWeight() {
        val plan = planned("8-10", 2)
        val s = next(plan, machine, session(plan, set(41.5, 7, 2)))!!
        assertEquals(Suggestion.Kind.Hold, s.kind)
        assertEquals(41.5 to 8, s.kg to s.reps)
        assertEquals("Last time 41.5 kg × 7 @ 2, one short of 8–10: same weight", s.why)
        // Hitting the reps closer to failure than the target is short too.
        assertEquals(Suggestion.Kind.Hold, next(plan, machine, session(plan, set(41.5, 8, 1)))!!.kind)
    }

    @Test
    fun twoOrMoreShortDropsOneRungOrAboutFivePercentOnAFineLadder() {
        val plan = planned("10-12", 0)
        val coarse = next(plan, cable, session(plan, set(7.9, 8, 0)))!!
        assertEquals(Suggestion.Kind.Down, coarse.kind)
        assertEquals(5.7, coarse.kg, 0.001)
        assertEquals("Last time 7.9 kg × 8 @ 0, 2 short of 10–12 → 5.7 kg", coarse.why)
        assertEquals(44.0, next(planned("8-10", 2), machine, session(planned("8-10", 2), set(46.5, 6, 2)))!!.kg, 0.001)
        val fine = planned("6-8", 1)
        assertEquals(95.0, next(fine, plates, session(fine, set(100.0, 4, 1)))!!.kg, 0.001)
    }

    @Test
    fun aDeloadNeverLowersTheWeightOrTheEstimate() {
        val normal = planned("8-10", 2)
        val easy = planned("8-10", 3)
        val history = arrayOf(session(normal, set(41.5, 10, 2)), session(easy, set(41.5, 6, 3), deload = true))
        val s = next(normal, machine, *history)!!
        assertEquals(Suggestion.Kind.Hold, s.kind)
        assertEquals(41.5, s.kg, 0.001)
        assertEquals(41.5 * 1.4, Progression.estimate(history.toList())!!.kg, 0.01)
    }

    @Test
    fun highRirSetsCanRaiseTheEstimateButNeverLowerIt() {
        val plan = planned("8-10", 2)
        assertEquals(61.6, Progression.estimate(listOf(session(plan, set(44.0, 10, 2)), session(plan, set(40.0, 8, 5))))!!.kg, 0.01)
        assertEquals(64.53, Progression.estimate(listOf(session(plan, set(41.5, 10, 2)), session(plan, set(44.0, 10, 4))))!!.kg, 0.01)
        // A normal set does lower it, and so does a session's best set only.
        assertEquals(53.33, Progression.estimate(listOf(session(plan, set(44.0, 10, 2)), session(plan, set(40.0, 8, 2))))!!.kg, 0.01)
    }

    @Test
    fun aDeloadWeekKeepsLastTimesWeightAtItsEasierTarget() {
        val s = next(planned("8-10", 3), machine, session(planned("8-10", 1), set(41.5, 10, 1)), deload = true)!!
        assertEquals(Suggestion.Kind.Deload, s.kind)
        assertEquals(41.5 to 8, s.kg to s.reps)
    }

    @Test
    fun aNewRepRangeGetsAWeightFromTheEstimateRoundedDownAndMarkedEstimated() {
        val s = next(planned("4-6", 1), machine, session(planned("8-10", 2), set(41.5, 10, 2)))!!
        assertTrue(s.estimated)
        assertEquals(46.5 to 6, s.kg to s.reps)
        assertEquals("Estimated from 41.5 kg × 10 @ 2, check on the first set", s.why)
    }

    @Test
    fun aSessionIsJudgedAgainstTheLastOneWithTheSameRepRange() {
        // Full Body benches 4–6 on Day A and 6–8 on Day C: each day progresses on its own.
        val heavy = planned("4-6", 2)
        val volume = planned("6-8", 2)
        val history = arrayOf(session(heavy, set(80.0, 6, 2)), session(volume, set(80.0, 6, 2)))
        val s = next(heavy, plates, *history)!!
        assertEquals(Suggestion.Kind.Up, s.kind)
        assertEquals(82.5, s.kg, 0.001)
        assertEquals(Suggestion.Kind.More, next(volume, plates, *history)!!.kind)
    }

    @Test
    fun theWeekTwelveHeavySingleTakesItsWeightFromTheEstimate() {
        val s = next(planned("1", 1), plates, session(planned("4-6", 1), set(92.5, 5, 1)))!!
        assertTrue(s.estimated)
        assertEquals(102.5 to 1, s.kg to s.reps)
    }

    @Test
    fun withinASessionTheNextSetFollowsTheRirGapAtAboutFivePercentAPointCappedAtTen() {
        val plan = planned("6-8", 2, count = 3)
        val easier = next(plan, plates, today = listOf(set(80.0, 8, 4)))!!
        assertEquals(Suggestion.Kind.Adjust, easier.kind)
        assertEquals(87.5, easier.kg, 0.001)
        assertEquals("Set 1 was 80 kg × 8 @ 4, target @ 2 → 87.5 kg", easier.why)
        assertEquals(87.5, next(plan, plates, today = listOf(set(80.0, 8, 7)))!!.kg, 0.001)
        assertEquals(75.0, next(plan, plates, today = listOf(set(80.0, 8, 1)))!!.kg, 0.001)
        val onTarget = next(plan, plates, today = listOf(set(80.0, 7, 2)))!!
        assertEquals(Suggestion.Kind.Follow, onTarget.kind)
        assertEquals(80.0 to 7, onTarget.kg to onTarget.reps)
        // Reps follow the set before, capped at the top of the range.
        val past = next(planned("10-12", 0, count = 2), cable, today = listOf(set(7.9, 14, 0)))!!
        assertEquals(7.9 to 12, past.kg to past.reps)
        // On a coarse stack a 10% change is no rung at all: same weight.
        assertEquals(5.7, next(planned("10-12", 0, count = 2), cable, today = listOf(set(5.7, 12, 2)))!!.kg, 0.001)
    }

    @Test
    fun aBackOffFollowsTheTopSetLoggedToday() {
        val top = PlannedSet(RepRange(4, 6), 1, 180)
        val backoff = PlannedSet(RepRange(6, 8), 1, 180, backoff = Backoff(from = 0, percent = 10))
        val s = next(listOf(top, backoff, backoff), plates, today = listOf(set(100.0, 5, 1)))!!
        assertEquals(Suggestion.Kind.Backoff, s.kind)
        assertEquals(90.0 to 8, s.kg to s.reps)
    }

    @Test
    fun partialRepsDontCountTowardsTheTopOfTheRange() {
        val plan = planned("8-10", 2)
        val s = next(plan, machine, session(plan, set(41.5, 9, 2, partials = 6)))!!
        assertEquals(Suggestion.Kind.More, s.kind)
        assertEquals(41.5 to 10, s.kg to s.reps)
    }

    @Test
    fun aSetThatDoesntCountIsSkipped() {
        val plan = planned("10-12", 0, count = 2)
        val today = Performance(listOf(WorkSet(set(79.0, 12, 0), plan[0], counts = false)))
        val s = Progression.suggest(plan, today, listOf(session(plan, set(7.9, 10, 0), set(7.9, 10, 0))), cable)!!
        assertEquals(7.9 to 11, s.kg to s.reps)
    }

    @Test
    fun epleysConstantIsLearnedFromTopAndBackOffSetsAndChangesTheEstimatedWeight() {
        val plan = listOf(PlannedSet(RepRange(4, 6), 1, 180), PlannedSet(RepRange(8, 10), 1, 180, backoff = Backoff(0, 15)))
        val history = listOf(session(plan, set(100.0, 5, 1), set(85.0, 10, 1)), session(plan, set(100.0, 5, 1), set(85.0, 10, 1)))
        assertEquals(22.33, Progression.constant(history), 0.01)
        assertEquals(30.0, Progression.constant(history.take(1)), 0.001)
        val s = next(planned("10-12", 1), plates, *history.toTypedArray())!!
        assertEquals(85.0, s.kg, 0.001)
    }

    @Test
    fun noHistoryMeansNoSuggestion() {
        assertNull(next(planned("8-10", 2), machine))
    }

    @Test
    fun anAdHocSessionIsJudgedAgainstTodaysPrescription() {
        val s = next(planned("8-10", 2), machine, session(null, set(41.5, 10, 2)))!!
        assertEquals(44.0, s.kg, 0.001)
    }
}
