package com.hyperlog.app.program

import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.progression.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedPrefillTest {
    /** The shared plates: a 20 kg bar, so 2.5 kg steps. */
    private val plates = LadderSpec.DefaultPlates.ladder()
    /** An lb stack shown in kg: 1.1, 3.4, 5.7, 7.9, 10.2 … */
    private val stack = LadderSpec.lbStack().ladder()

    private val top = SetPrescription(RepRange(4, 6), 1, 180)
    private val backoff = SetPrescription(RepRange(6, 8), 1, 180, backoff = Backoff(0, 10))
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
    fun eachDropIsThePreviousWeightMinusThePercentRoundedDownToARung() {
        val dumbbells = LadderSpec.DefaultDumbbells.ladder()
        // 25 − 25% = 18.75 → 18; 18 − 25% = 13.5 → 12 (screens/03-drop-set-in-progress.png).
        assertEquals(listOf(17.5, 12.5), dropWeights(25.0, Technique.Dropset(drops = 2, percent = 25), dumbbells))
        // 100 − 20% = 80, 80 − 20% = 64 → 62.5, 62.5 − 20% = 50 on the plates.
        assertEquals(listOf(80.0, 62.5, 50.0), dropWeights(100.0, Technique.Dropset(drops = 3, percent = 20), plates))
        // At the lightest rung the drops stay there.
        assertEquals(listOf(1.1, 1.1), dropWeights(3.4, Technique.Dropset(drops = 2, percent = 25), stack))
    }

    @Test
    fun theEntryTakesTheSuggestionsWeightAndTheTargetRirButNoReps() {
        val entry = plannedEntry(bench, logged = emptyList(), Suggestion(87.5, 5, Suggestion.Kind.Up, "why"))
        assertEquals(SetEntry("87.5", "", "", 1, Field.Reps, partialMode = false, fresh = true), entry)
    }

    @Test
    fun withNoSuggestionTheFirstSetStartsOnAnEmptyWeightAndEmptyReps() {
        val entry = plannedEntry(bench, logged = emptyList(), suggestion = null)
        assertEquals(SetEntry("", "", "", 1, Field.Weight, partialMode = false, fresh = true), entry)
    }

    @Test
    fun upcomingBackOffsShowTheWeightWorkedOutFromTheTopSetBeingTyped() {
        assertEquals(listOf(90.0, 90.0), (1..2).map { plannedWeight(it, bench, { null }, toDo = 0, emptyList(), plates, activeKg = 100.0) })
        assertEquals(85.0, plannedWeight(1, bench, listOf(LoggedSet(95.0, 5, 0, 1))::getOrNull, toDo = 1, emptyList(), plates, activeKg = 85.0))
    }
}
