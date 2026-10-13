package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every ladder in one line, as setup and the equipment lists show it. */
class LadderSummaryTest {
    @Test
    fun anLbStackReadsByItsLbStep() {
        assertEquals("1.1–89.6 kg by 5 lb", LadderSpec.lbStack().summary())
    }

    @Test
    fun theDefaultDumbbellsChangeStepAtTen() {
        assertEquals("1–10 kg by 1 · 12.5–50 by 2.5", LadderSpec.DefaultDumbbells.summary())
        assertEquals(26, LadderSpec.DefaultDumbbells.ladder().rungs.size)
    }

    @Test
    fun aPinStackNamesItsAddOns() {
        assertEquals("5–100 kg by 5", LadderSpec.DefaultPinStack.summary())
        assertEquals("5–100 kg by 5 + add-ons", LadderSpec.DefaultPinStack.summary(pinPlates = listOf(1.25, 2.5)))
        assertEquals("5–100 kg by 5 + add-ons", LadderSpec.DefaultPinStack.copy(builtIn = listOf(2.5), pinPlates = false).summary())
    }

    @Test
    fun platesNameTheirBaseAndTheTop() {
        assertEquals("20 kg + plates, to 207.5 kg", LadderSpec.DefaultPlates.summary())
        assertEquals("Plates, to 40 kg", LadderSpec.Plates(0.0, listOf(20.0)).summary())
    }

    @Test
    fun aListOfOddWeightsIsListed() {
        assertEquals("4.5–13.5 kg by 4.5", LadderSpec.Explicit(listOf(4.5, 9.0, 13.5)).summary())
        assertEquals("2 · 7 · 20 kg", LadderSpec.Explicit(listOf(2.0, 7.0, 20.0)).summary())
    }
}
