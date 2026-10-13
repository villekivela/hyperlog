package com.hyperlog.app.domain

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Test

/** Warm-ups ramp from the first work set's weight onto real weights (SPEC, Screens → Today). */
class WarmupTest {
    private fun exercise(equipment: Equipment, pattern: MovementPattern) =
        Exercise("x", "X", equipment, pattern, GripDemand.Low, listOf("Chest"))

    private val bench = exercise(Equipment.Barbell, MovementPattern.HorizontalPush)
    private val plates = LadderSpec.DefaultPlates.ladder()

    @Test
    fun aHeavyBenchTopSetGetsFourRampSetsOnThePlates() {
        assertEquals(
            listOf(WarmupSet(40.0, 8), WarmupSet(55.0, 5), WarmupSet(67.5, 3), WarmupSet(77.5, 2)),
            Warmups.ramp(90.0, bench, 6, plates),
        )
    }

    @Test
    fun plateLoadedWarmUpsKeepToFiveKgSoTheSmallPlatesStayOff() {
        // 45, 60, 75 and 85% of 90 kg, on 5 kg steps: a tie (67.5) goes to the lighter weight.
        assertEquals(
            listOf(WarmupSet(40.0, 8), WarmupSet(55.0, 5), WarmupSet(65.0, 3), WarmupSet(75.0, 2)),
            Warmups.ramp(90.0, bench, 6, plates, plateLoaded = true),
        )
    }

    @Test
    fun aCompoundInHigherRepsGetsThreeRampSets() {
        assertEquals(
            listOf(WarmupSet(45.0, 8), WarmupSet(65.0, 5), WarmupSet(85.0, 3)),
            Warmups.ramp(100.0, bench, 10, plates),
        )
    }

    @Test
    fun theRampFollowsTheFirstWorkSetsRepsNotTheBottomOfItsRange() {
        // A 6–8 set done for 8 is not a heavy set: three ramp sets.
        assertEquals(3, Warmups.ramp(100.0, bench, 8, plates).size)
        assertEquals(4, Warmups.ramp(100.0, bench, 6, plates).size)
        // Reps not known yet: three.
        assertEquals(3, Warmups.ramp(100.0, bench, null, plates).size)
    }

    @Test
    fun anIsolationExerciseGetsOneWarmUp() {
        val lateral = exercise(Equipment.Dumbbell, MovementPattern.LateralRaise)
        assertEquals(
            listOf(WarmupSet(6.0, 8)),
            Warmups.ramp(10.0, lateral, 12, LadderSpec.DefaultDumbbells.ladder()),
        )
    }

    @Test
    fun aLightTopSetKeepsOnlyWarmUpsThatClimbAndStartsAtTheBar() {
        // 45% of 30 is under the bar, so the ramp starts on the empty bar; repeats are dropped.
        assertEquals(
            listOf(WarmupSet(20.0, 8), WarmupSet(22.5, 3), WarmupSet(25.0, 2)),
            Warmups.ramp(30.0, bench, 6, plates),
        )
    }

    @Test
    fun theEmptyBarAsTheWorkWeightHasNoWarmUps() {
        assertEquals(emptyList<WarmupSet>(), Warmups.ramp(20.0, bench, 6, plates))
    }

    @Test
    fun bodyweightExercisesHaveNoWarmUps() {
        val pullUp = exercise(Equipment.WeightedBodyweight, MovementPattern.VerticalPull)
        assertEquals(emptyList<WarmupSet>(), Warmups.ramp(20.0, pullUp, 6, WeightLadder.Default))
    }
}
