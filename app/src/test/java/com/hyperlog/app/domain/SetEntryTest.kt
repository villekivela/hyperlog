package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SetEntryTest {
    private val ladder = WeightLadder.steps(start = 2.5, step = 2.5, count = 80)

    private fun SetEntry.type(vararg keys: PadKey) = keys.fold(this) { e, k -> e.press(k, ladder) }

    @Test
    fun theFirstKeyReplacesThePrefill() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2)).type(PadKey.Digit(8))
        assertEquals("8", entry.full)
        assertEquals(Field.Reps, entry.field)
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
        val entry = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2))
        assertEquals("11", entry.type(PadKey.Plus).full)
        val weight = entry.focus(Field.Weight).type(PadKey.Minus)
        assertEquals("57.5", weight.weight)
    }

    @Test
    fun nothingLogsWithoutWeightRepsAndRir() {
        val noRir = SetEntry.prefill(null).type(PadKey.Digit(4), PadKey.Digit(0)).focus(Field.Reps).type(PadKey.Digit(8))
        assertNull(noRir.rir)
        assertFalse(noRir.canLog)
        assertEquals(SetEntry.Done.Nothing, noRir.done())
        val noReps = SetEntry.prefill(LoggedSet(60.0, 10, 0, 2)).type(PadKey.Backspace, PadKey.Backspace)
        assertEquals("", noReps.full)
        assertEquals(SetEntry.Done.Nothing, noReps.done())
    }

    @Test
    fun backspaceEditsThePrefillInsteadOfReplacingIt() {
        val entry = SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).type(PadKey.Backspace)
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
        val entry = SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).partialMode(true).type(PadKey.Digit(6))
        assertEquals("12 + 6", entry.repsText)
        assertEquals("12", SetEntry.prefill(LoggedSet(60.0, 12, 0, 2)).repsText)
    }
}
