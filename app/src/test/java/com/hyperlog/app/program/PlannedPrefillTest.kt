package com.hyperlog.app.program

import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedPrefillTest {
    /** The shared plates: a 20 kg bar, so 2.5 kg steps. */
    private val plates = LadderSpec.DefaultPlates.ladder()
    /** An lb stack shown in kg: 1.1, 3.4, 5.7, 7.9, 10.2 … */
    private val stack = LadderSpec.lbStack().ladder()

    private val top = PlannedSet(RepRange(4, 6), 1, 180)
    private val backoff = PlannedSet(RepRange(6, 8), 1, 180, backoff = Backoff(0, 10))
    private val bench = listOf(top, backoff, backoff)

    @Test
    fun aBackOffIsTheTopSetMinusThePercentRoundedDownToARung() {
        assertEquals(90.0, backoffWeight(100.0, 10, plates), 0.001)
        // 92.5 − 10% = 83.25: the rung below is 82.5, never the nearer 85.
        assertEquals(82.5, backoffWeight(92.5, 10, plates), 0.001)
        // 10.2 − 10% = 9.2 on a 5 lb stack: 7.9.
        assertEquals(7.9, backoffWeight(10.2, 10, stack), 0.001)
        // Below the lightest rung there is only the lightest rung.
        assertEquals(20.0, backoffWeight(21.0, 10, plates), 0.001)
    }

    @Test
    fun theFirstSetStartsFromLastTimesFirstSetAtTheTopOfTheRangeAndTheTargetRir() {
        val entry = plannedEntry(bench, logged = emptyList(), lastTime = listOf(LoggedSet(85.0, 5, 0, 1)), plates)
        assertEquals(SetEntry("85", "6", "", 1, Field.Reps, partialMode = false, fresh = true), entry)
    }

    @Test
    fun withNoHistoryTheFirstSetStartsOnAnEmptyWeight() {
        val entry = plannedEntry(bench, logged = emptyList(), lastTime = emptyList(), plates)
        assertEquals(SetEntry("", "6", "", 1, Field.Weight, partialMode = false, fresh = true), entry)
    }

    @Test
    fun theBackOffAfterALoggedTopSetIsPrefilledWithItsWeightAndItsOwnRange() {
        val entry = plannedEntry(bench, logged = listOf(LoggedSet(92.5, 5, 0, 1)), lastTime = emptyList(), plates)
        assertEquals(SetEntry("82.5", "8", "", 1, Field.Reps, partialMode = false, fresh = true), entry)
    }

    @Test
    fun aSetLikeTheOneBeforeTakesItsWeightAndRepsCappedAtTheTopOfTheRange() {
        val lateral = listOf(PlannedSet(RepRange(10, 12), 0, 90), PlannedSet(RepRange(10, 12), 0, 90))
        assertEquals(
            SetEntry("7.9", "11", "", 0, Field.Reps, partialMode = false, fresh = true),
            plannedEntry(lateral, listOf(LoggedSet(7.9, 11, 0, 0)), emptyList(), stack),
        )
        assertEquals("12", plannedEntry(lateral, listOf(LoggedSet(7.9, 14, 0, 0)), emptyList(), stack).full)
    }

    @Test
    fun upcomingBackOffsShowTheWeightWorkedOutFromTheTopSetBeingTyped() {
        assertEquals(listOf(90.0, 90.0), (1..2).map { plannedWeight(it, bench, emptyList(), emptyList(), plates, activeKg = 100.0) })
        assertEquals(85.0, plannedWeight(1, bench, listOf(LoggedSet(95.0, 5, 0, 1)), emptyList(), plates, activeKg = 85.0))
    }
}
