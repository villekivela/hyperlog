package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SetEntryTest {
    private val ladder = WeightLadder.steps(start = 2.5, step = 2.5, count = 80)

    private fun SetEntry.type(vararg keys: PadKey) = keys.fold(this) { e, k -> e.press(k, ladder) }

    @Test
    fun theNextSetTakesTheWeightAndRirButNeverTheReps() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2))
        assertEquals("60", entry.weight)
        assertEquals(2, entry.rir)
        assertEquals("", entry.full)
        assertFalse(entry.canLog)
        assertEquals(SetEntry.Done.Nothing, entry.done())
        assertEquals(SetEntry.Done.Log(LoggedSet(60.0, 8, 0, 2)), entry.type(PadKey.Digit(8)).done())
    }

    @Test
    fun selectingThePickedRirAgainClearsIt() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2)).type(PadKey.Digit(1), PadKey.Digit(0))
        assertEquals(1, entry.selectRir(1).rir)
        assertNull(entry.selectRir(2).rir)
        assertEquals(SetEntry.Done.Log(LoggedSet(60.0, 10, 0, null)), entry.selectRir(2).done())
    }

    @Test
    fun plusOrMinusOnAnEmptyWeightStartsAtTheLightestRung() {
        // An lb stack: 1.1, 3.4, 5.7 … Found on the phone: + from empty went straight to 3.4.
        val stack = LadderSpec.lbStack().ladder()
        assertEquals("1.1", SetEntry.prefill(null).press(PadKey.Plus, stack).weight)
        assertEquals("3.4", SetEntry.prefill(null).press(PadKey.Plus, stack).press(PadKey.Plus, stack).weight)
        assertEquals("1.1", SetEntry.prefill(null).press(PadKey.Minus, stack).weight)
    }

    @Test
    fun aSplitSetTakesTheLeftRepsThenTheRightThenLogsBoth() {
        var entry = SetEntry.prefill(LoggedSet(20.0, 10, 0, 1)).type(PadKey.Digit(1), PadKey.Digit(0)).split(true)
        // Splitting starts the right side on the same reps as the left.
        assertEquals("10", entry.right)
        entry = entry.type(PadKey.Digit(9))
        val moved = entry.done() as SetEntry.Done.Moved
        assertEquals(Field.Right, moved.entry.field)
        entry = moved.entry.type(PadKey.Digit(8))
        assertEquals("8", entry.right)
        assertEquals("9 / 8", entry.repsText)
        assertEquals(SetEntry.Done.Log(LoggedSet(20.0, 9, 0, 1, rightReps = 8)), entry.done())
        // Back to one number for both sides.
        assertNull(entry.split(false).right)
        assertEquals(Field.Reps, entry.split(false).field)
    }

    @Test
    fun aSummaryPutsDropsMiniSetsAndHoldsAfterTheSet() {
        val drop = LoggedSet(25.0, 9, 0, 1, extensions = listOf(Extension(Extension.Kind.Drop, 18.0, 7), Extension(Extension.Kind.Drop, 12.0, 6)))
        assertEquals("25 × 9 @ 1, 18 × 7, 12 × 6", drop.summary())
        val myo = LoggedSet(60.0, 12, 0, 0, extensions = listOf(Extension(Extension.Kind.MiniSet, 60.0, 4), Extension(Extension.Kind.MiniSet, 60.0, 3)))
        assertEquals("60 × 12 @ 0 + 4 + 3", myo.summary())
        assertEquals("60 × 12 + 6p @ 0 + 20 s", LoggedSet(60.0, 12, 6, 0, extensions = listOf(Extension(Extension.Kind.Hold, 60.0, 20))).summary())
        assertEquals("30 × 10/9 @ 1", LoggedSet(30.0, 10, 0, 1, rightReps = 9).summary())
    }

    @Test
    fun theWeakerSideOfASplitSetIsWhatProgressionReads() {
        assertEquals(8, LoggedSet(20.0, 9, 0, 1, rightReps = 8).progressionReps)
        assertEquals(9, LoggedSet(20.0, 9, 3, 1).progressionReps)
    }

    @Test
    fun weightThenRepsThenRirLogsTheSet() {
        var entry = SetEntry.prefill(null)
        assertEquals(Field.Weight, entry.field)
        entry = entry.type(PadKey.Digit(6), PadKey.Digit(2), PadKey.Dot, PadKey.Digit(5))
        val moved = entry.done() as SetEntry.Done.Moved
        entry = moved.entry.type(PadKey.Digit(1), PadKey.Digit(0)).selectRir(1)
        assertEquals(SetEntry.Done.Log(LoggedSet(62.5, 10, 0, 1)), entry.done())
    }

    @Test
    fun partialsGoIntoTheSameCellAfterTheFlip() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2))
            .type(PadKey.Digit(1), PadKey.Digit(2))
            .partialMode(true)
            .type(PadKey.Digit(6))
        assertEquals("12", entry.full)
        assertEquals("6", entry.partial)
        assertEquals(SetEntry.Done.Log(LoggedSet(60.0, 12, 6, 2)), entry.done())
    }

    @Test
    fun minusAndPlusStepOneRungOnWeightAndOneRepOnReps() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2)).type(PadKey.Digit(1), PadKey.Digit(0))
        assertEquals("11", entry.type(PadKey.Plus).full)
        val weight = entry.focus(Field.Weight).type(PadKey.Minus)
        assertEquals("57.5", weight.weight)
    }

    @Test
    fun nothingLogsWithoutWeightAndReps() {
        val noRir = SetEntry.prefill(null).type(PadKey.Digit(4), PadKey.Digit(0)).focus(Field.Reps).type(PadKey.Digit(8))
        assertNull(noRir.rir)
        assertEquals(SetEntry.Done.Log(LoggedSet(40.0, 8, 0, null)), noRir.done())
        val noWeight = SetEntry.prefill(null).focus(Field.Reps).type(PadKey.Digit(8))
        assertFalse(noWeight.canLog)
        val noReps = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2)).type(PadKey.Backspace, PadKey.Backspace)
        assertEquals("", noReps.full)
        assertEquals(SetEntry.Done.Nothing, noReps.done())
    }

    @Test
    fun backspaceEditsThePrefillInsteadOfReplacingIt() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).type(PadKey.Digit(1), PadKey.Digit(2), PadKey.Backspace)
        assertEquals("1", entry.full)
    }

    @Test
    fun weightTakesOneDotAndAtMostFiveCharacters() {
        val entry = SetEntry.prefill(null).type(
            PadKey.Digit(1), PadKey.Dot, PadKey.Digit(2), PadKey.Dot, PadKey.Digit(5), PadKey.Digit(5), PadKey.Digit(5),
        )
        assertEquals("1.255", entry.weight)
    }

    @Test
    fun repsTakeAtMostThreeDigitsAndPartialsTwo() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 12, 0, 2))
            .type(PadKey.Digit(1), PadKey.Digit(2), PadKey.Digit(3), PadKey.Digit(4))
            .partialMode(true)
            .type(PadKey.Digit(5), PadKey.Digit(6), PadKey.Digit(7))
        assertEquals("123", entry.full)
        assertEquals("56", entry.partial)
    }

    @Test
    fun theRepsCellReadsFullPlusPartials() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).type(PadKey.Digit(1), PadKey.Digit(2)).partialMode(true).type(PadKey.Digit(6))
        assertEquals("12 + 6", entry.repsText)
        assertEquals("", SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).repsText)
    }
}
