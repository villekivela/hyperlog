package com.hyperlog.app.session

import com.hyperlog.app.domain.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a finished session can say about itself (#272). */
class SessionMomentsTest {
    private fun set(kg: Double, reps: Int) = LoggedSet(kg, reps, 0, 1)

    @Test
    fun heavierIsUp() = assertEquals(LiftChange.Up, liftChange(listOf(set(105.0, 4)), listOf(set(100.0, 6))))

    @Test
    fun asHeavyForMoreRepsIsUp() = assertEquals(LiftChange.Up, liftChange(listOf(set(100.0, 7)), listOf(set(100.0, 6))))

    @Test
    fun theSameTopSetIsTheSame() = assertEquals(LiftChange.Same, liftChange(listOf(set(100.0, 6), set(90.0, 8)), listOf(set(100.0, 6))))

    @Test
    fun lighterIsDown() = assertEquals(LiftChange.Down, liftChange(listOf(set(95.0, 10)), listOf(set(100.0, 6))))

    @Test
    fun asHeavyForFewerRepsIsDown() = assertEquals(LiftChange.Down, liftChange(listOf(set(100.0, 5)), listOf(set(100.0, 6))))

    @Test
    fun theTopSetDecidesWhicheverPlaceItIn() = assertEquals(LiftChange.Up, liftChange(listOf(set(90.0, 8), set(105.0, 4)), listOf(set(100.0, 6), set(90.0, 8))))

    @Test
    fun nothingToCompareWithIsNoChange() = assertEquals(null, liftChange(listOf(set(100.0, 6)), emptyList()))

    private fun lift(name: String, change: LiftChange?, top: LoggedSet, last: LoggedSet?, planned: Int? = 2, sets: Int = 2, aimed: Int = 2, onEffort: Int = 2) =
        ExerciseSummary(name, List(sets) { top }, change = change, lastTop = last, planned = planned, aimed = aimed, onEffort = onEffort)

    private fun summary(vararg lifts: ExerciseSummary) = SessionSummary(1, 0, 3_600_000, lifts.toList())

    @Test
    fun everythingDoneOnEffortWithLiftsUpIsStrong() {
        val session = summary(lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6)), lift("B", LiftChange.Up, set(60.0, 9), set(60.0, 8)))
        assertEquals(SessionRating.Strong, session.rating)
    }

    /** Holding every number on a plan done in full is good training, not a failure. */
    @Test
    fun aPlanDoneInFullWithLiftsHeldIsSolid() {
        val session = summary(lift("A", LiftChange.Same, set(100.0, 6), set(100.0, 6)), lift("B", LiftChange.Same, set(60.0, 8), set(60.0, 8)))
        assertEquals(SessionRating.Solid, session.rating)
    }

    /** Half the sets in the bank still counts, and says so kindly. */
    @Test
    fun aShortSessionIsBanked() {
        val session = summary(lift("A", LiftChange.Down, set(90.0, 6), set(100.0, 6), planned = 4, sets = 1, aimed = 1, onEffort = 0))
        assertEquals(SessionRating.Banked, session.rating)
    }

    /** A first session has no last time: it is read on what it has. */
    @Test
    fun whatCantBeMeasuredDoesntCountAgainstASession() {
        assertEquals(SessionRating.Strong, summary(lift("A", null, set(100.0, 6), null)).rating)
        assertEquals(SessionRating.Solid, summary(lift("A", null, set(100.0, 6), null, planned = null, aimed = 0, onEffort = 0)).rating)
    }

    @Test
    fun theSessionAddsUpItsMeasures() {
        val session = summary(
            lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6), planned = 3, sets = 2, aimed = 2, onEffort = 1),
            lift("B", null, set(60.0, 9), null, planned = null, sets = 1, aimed = 0, onEffort = 0),
        )
        assertEquals(3, session.planned)
        assertEquals(2, session.aimed)
        assertEquals(1, session.onEffort)
    }

    @Test
    fun theBestLiftGainedMostOnLastTime() {
        val session = summary(
            lift("Small", LiftChange.Up, set(102.5, 6), set(100.0, 6)),
            lift("Big", LiftChange.Up, set(66.0, 6), set(60.0, 6)),
            lift("Same", LiftChange.Same, set(50.0, 6), set(50.0, 6)),
        )
        assertEquals("Big", session.bestLift?.name)
    }

    @Test
    fun moreRepsAtTheSameWeightCanBeTheBest() {
        assertEquals("Reps", summary(lift("Reps", LiftChange.Up, set(60.0, 9), set(60.0, 8))).bestLift?.name)
    }

    /** Review: a 0 kg lift made 0/0, which sorts above any real gain. */
    @Test
    fun aBodyweightLiftNeverOutranksARealGainOnNothing() {
        val session = summary(lift("Dip", LiftChange.Up, set(0.0, 12), set(0.0, 10)), lift("Bench", LiftChange.Up, set(105.0, 6), set(100.0, 6)))
        assertEquals("Bench", session.bestLift?.name)
    }

    /** Review: three of six exercises skipped read `9 of 9` and could rate Strong. */
    @Test
    fun anExerciseSkippedStillCountsAgainstThePlan() {
        val session = summary(lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6))).copy(skippedSets = 6)
        assertEquals(8, session.planned)
        assertEquals(2, session.plannedDone)
        assertEquals(SessionRating.Banked, session.rating)
    }

    /** Review: sets added on top of a plan read `14 of 12`. */
    @Test
    fun setsBeyondThePlanDontPassIt() {
        assertEquals(2, summary(lift("A", null, set(100.0, 6), null, planned = 2, sets = 4)).plannedDone)
    }

    /** Round 4: the read-back has no week, no skipped sets and no deload to go on. */
    @Test
    fun theReadBackRefreshesTheSetsAndNothingElse() {
        val finished = SessionSummary(
            1, 0, 3_600_000,
            listOf(ExerciseSummary("A", listOf(set(100.0, 6)), exerciseId = "a", change = LiftChange.Up, lastTop = set(95.0, 6), planned = 3, aimed = 1, onEffort = 1)),
            WeekDone(7, 3, 20), skippedSets = 8,
        )
        val readBack = SessionSummary(1, 0, 9_000_000, listOf(ExerciseSummary("A", listOf(set(100.0, 6)), exerciseId = "a", change = LiftChange.Down, lastTop = set(120.0, 6), planned = 1)))
        assertEquals(finished, finished.refreshedBy(readBack))
    }

    /** A correction made from Session done is the one thing the read-back brings. */
    @Test
    fun aCorrectedSetIsReadAgainstTheSameLastTime() {
        val finished = SessionSummary(1, 0, 3_600_000, listOf(ExerciseSummary("A", listOf(set(100.0, 6)), exerciseId = "a", change = LiftChange.Up, lastTop = set(95.0, 6))))
        val corrected = SessionSummary(1, 0, 3_600_000, listOf(ExerciseSummary("A", listOf(set(90.0, 6)), records = listOf(null), exerciseId = "a")))
        val refreshed = finished.refreshedBy(corrected).exercises.single()
        assertEquals(listOf(set(90.0, 6)), refreshed.sets)
        assertEquals(LiftChange.Down, refreshed.change)
    }

    /** Session done opens an exercise by its place in the stored session. */
    @Test
    fun theReadBacksOrderStands() {
        val a = ExerciseSummary("A", listOf(set(100.0, 6)), exerciseId = "a")
        val b = ExerciseSummary("B", listOf(set(50.0, 6)), exerciseId = "b")
        val refreshed = SessionSummary(1, 0, 1, listOf(a, b)).refreshedBy(SessionSummary(1, 0, 1, listOf(b, a)))
        assertEquals(listOf("B", "A"), refreshed.exercises.map { it.name })
    }

    /** Round 4: `1 of 12 sets` read Solid, as the other measures averaged it up. */
    @Test
    fun underHalfThePlanIsNeverSolid() {
        assertEquals(SessionRating.Banked, summary(lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6), planned = 12, sets = 1, aimed = 1, onEffort = 1)).rating)
        assertEquals(SessionRating.Banked, summary(lift("A", LiftChange.Same, set(100.0, 6), set(100.0, 6), planned = 9, sets = 2)).rating)
    }

    @Test
    fun halfThePlanCanBeSolid() {
        assertEquals(SessionRating.Solid, summary(lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6), planned = 4, sets = 2)).rating)
    }

    /** Most of a plan, every lift up: good, and still not all of it. */
    @Test
    fun aPlanLeftUnfinishedIsNeverStrong() {
        assertEquals(SessionRating.Solid, summary(lift("A", LiftChange.Up, set(105.0, 6), set(100.0, 6), planned = 3, sets = 2)).rating)
    }

    /** Round 4: Session done said 2 min and Today 1 minute for the same session. */
    @Test
    fun aSessionsMinutesRoundTheSameEverywhere() {
        assertEquals(2, sessionMinutes(0, 90_000))
        assertEquals(1, sessionMinutes(0, 80_000))
    }

    @Test
    fun noLiftUpHasNoBestLift() {
        assertEquals(null, summary(lift("A", LiftChange.Same, set(100.0, 6), set(100.0, 6))).bestLift)
    }
}
