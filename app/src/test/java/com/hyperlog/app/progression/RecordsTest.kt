package com.hyperlog.app.progression

import com.hyperlog.app.domain.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records (SPEC, Records): a work set that beats every earlier set of the exercise. */
class RecordsTest {
    private fun set(kg: Double, reps: Int, rir: Int? = 1) = LoggedSet(kg, reps, 0, rir)

    private fun session(vararg sets: LoggedSet, deload: Boolean = false) =
        Performance(sets.map { WorkSet(it, null) }, deload)

    @Test
    fun aSetWhoseEstimateBeatsEveryEarlierSetIsAnEstimateRecord() {
        val history = listOf(session(set(90.0, 5), set(90.0, 5)))
        // 92.5 × 5 @ 1 is more than 90 × 5 @ 1; 90 × 4 @ 1 is less.
        val today = session(set(92.5, 5), set(90.0, 4))
        assertEquals(listOf(Record.Estimate, null), Records.of(history, today))
    }

    @Test
    fun moreRepsThanEverAtAWeightLiftedBeforeIsARepRecord() {
        // 100 × 3 @ 0 gives the best estimate; 80 × 8 @ 3 is below it but beats 80 × 6.
        val history = listOf(session(set(100.0, 3, rir = 0), set(80.0, 6, rir = 3)))
        // 85 kg was never lifted, and 80 × 6 only matches.
        val today = session(set(80.0, 8, rir = 0), set(85.0, 7, rir = 0), set(80.0, 6, rir = 0))
        assertEquals(listOf(Record.Reps, null, null), Records.of(history, today))
    }

    @Test
    fun aSetThatIsBothReadsAsAnEstimateRecord() {
        val history = listOf(session(set(80.0, 6)))
        assertEquals(listOf(Record.Estimate), Records.of(history, session(set(80.0, 8))))
    }

    @Test
    fun twoDumbbellsCountBothButRepsAreAtTheWeightOfOne() {
        val history = listOf(session(set(30.0, 8)).copy(dumbbells = 2))
        // 32 × 8 a dumbbell beats 30 × 8; 30 × 9 is more reps at 30 kg each, and its estimate beats 30 × 8 too.
        val today = session(set(32.0, 8), set(30.0, 7, rir = 0), set(30.0, 9, rir = 0)).copy(dumbbells = 2)
        assertEquals(listOf(Record.Estimate, null, Record.Reps), Records.of(history, today))
    }

    @Test
    fun weightedBodyweightCountsTheBodyInTheEstimate() {
        // 80 kg of body and 20 on the belt: 100 kg × 5 @ 1.
        val history = listOf(session(set(20.0, 5)).copy(bodyweightKg = 80.0))
        // Heavier by 3 kg with the same belt is more; lighter by 3 kg with 2.5 more on the belt is less.
        assertEquals(listOf(Record.Estimate), Records.of(history, session(set(20.0, 5)).copy(bodyweightKg = 83.0)))
        assertEquals(listOf(null), Records.of(history, session(set(22.5, 5)).copy(bodyweightKg = 77.0)))
    }

    @Test
    fun anExercisesFirstSessionHasNothingToBeat() {
        // Set 2 beats set 1 on both counts, but nothing came before the session.
        assertEquals(listOf(null, null), Records.of(emptyList(), session(set(80.0, 5), set(85.0, 6))))
    }

    @Test
    fun deloadSetsNeverMakeARecord() {
        val history = listOf(session(set(80.0, 5)))
        assertEquals(listOf(null), Records.of(history, session(set(90.0, 6), deload = true)))
    }

    @Test
    fun partialRepsNeverMakeARecord() {
        val history = listOf(session(set(80.0, 8, rir = 0)))
        // 8 + 4 partials is 8 full reps: the same set.
        assertEquals(listOf(null), Records.of(history, session(LoggedSet(80.0, 8, 4, 0))))
    }

    @Test
    fun aWeightKeptPastACheckIsNoRecordUntilConfirmed() {
        val history = listOf(session(set(80.0, 5)))
        val today = Performance(listOf(WorkSet(set(100.0, 5), null, counts = false)))
        assertEquals(listOf(null), Records.of(history, today))
    }
}
