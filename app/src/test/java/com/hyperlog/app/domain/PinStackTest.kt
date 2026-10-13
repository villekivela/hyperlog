package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A selectorised stack with add-ons (SPEC, Gyms and equipment → Stacks with add-ons). */
class PinStackTest {
    private val gymPlates = listOf(1.25, 2.5)

    @Test
    fun theDefaultStackStepsFiveKgFromFiveToAHundred() {
        val pins = LadderSpec.DefaultPinStack.ladder().rungs
        assertEquals(listOf(5.0, 10.0, 15.0), pins.take(3))
        assertEquals(100.0, pins.last(), 0.0)
        assertEquals(20, pins.size)
    }

    @Test
    fun theGymsPinPlatesFillInEveryCombinationOnEachPin() {
        val rungs = LadderSpec.DefaultPinStack.ladder(gymPlates).rungs
        // 5 with nothing, 1.25, 2.5 or both on the pin, then the next pin.
        assertEquals(listOf(5.0, 6.25, 7.5, 8.75, 10.0, 11.25), rungs.take(6))
        assertEquals(103.75, rungs.last(), 0.0)
    }

    @Test
    fun aMachineThatTakesNoPinPlatesIgnoresTheGyms() {
        val stack = LadderSpec.DefaultPinStack.copy(pinPlates = false)
        assertEquals(stack.ladder().rungs, stack.ladder(gymPlates).rungs)
    }

    @Test
    fun builtInAddOnsCountFromNoneToAllOfEach() {
        // Two identical 2.5 kg plates on top of the stack add 0, 2.5 or 5.
        val stack = LadderSpec.PinStack.steps(WeightUnit.Kg, start = 10.0, step = 10.0, top = 30.0, builtIn = listOf(2.5, 2.5), pinPlates = false)
        assertEquals(listOf(10.0, 12.5, 15.0, 20.0, 22.5, 25.0, 30.0, 32.5, 35.0), stack.ladder().rungs)
    }

    @Test
    fun nearDuplicateRungsAreKeptOnce() {
        // An lb stack (4.5 kg labels) with a 4.5 kg add-on: pin 4.5 + add-on lands on pin 9.1.
        val stack = LadderSpec.PinStack.steps(WeightUnit.Lb, start = 10.0, step = 10.0, top = 30.0, builtIn = listOf(4.5), pinPlates = false)
        val rungs = stack.ladder().rungs
        rungs.zipWithNext().forEach { (a, b) -> assertTrue("$a and $b are within 0.1 kg", b - a >= 0.1) }
        assertEquals(listOf(4.5, 9.1, 13.6, 18.1), rungs)
    }

    @Test
    fun anLbStackShowsItsPrintedLabels() {
        // 15 lb plates labelled in whole kg: 80, 95, 110 lb read 36, 43 and 50.
        val stack = LadderSpec.PinStack.steps(WeightUnit.Lb, start = 80.0, step = 15.0, top = 110.0, labelKg = 1.0, pinPlates = false)
        assertEquals(listOf(36.0, 43.0, 50.0), stack.ladder().rungs)
    }

    @Test
    fun labelsCanBeCutRatherThanRounded() {
        val stack = LadderSpec.PinStack.steps(WeightUnit.Lb, start = 80.0, step = 15.0, top = 110.0, labelKg = 1.0, labelsCut = true, pinPlates = false)
        assertEquals(listOf(36.0, 43.0, 49.0), stack.ladder().rungs)
    }

    @Test
    fun aDifferentFirstPlateComesBeforeTheSteps() {
        val stack = LadderSpec.PinStack.steps(WeightUnit.Kg, first = 2.5, start = 5.0, step = 5.0, top = 20.0, pinPlates = false)
        assertEquals(listOf(2.5, 5.0, 10.0, 15.0, 20.0), stack.ladder().rungs)
    }

    @Test
    fun theLoadingSaysWhichPinAndWhatGoesOnIt() {
        val stack = LadderSpec.PinStack.steps(WeightUnit.Kg, start = 10.0, step = 10.0, top = 100.0, builtIn = listOf(2.5, 2.5))
        assertEquals(PinLoad(pin = 40.0, builtIn = emptyList(), onPin = emptyList()), stack.load(40.0, gymPlates))
        // A built-in add-on is handier than a loose plate, so it is used first.
        assertEquals(PinLoad(pin = 40.0, builtIn = listOf(2.5), onPin = emptyList()), stack.load(42.5, gymPlates))
        assertEquals(PinLoad(pin = 40.0, builtIn = listOf(2.5, 2.5), onPin = emptyList()), stack.load(45.0, gymPlates))
        assertEquals(PinLoad(pin = 40.0, builtIn = listOf(2.5, 2.5), onPin = listOf(1.25)), stack.load(46.25, gymPlates))
        assertEquals(PinLoad(pin = 40.0, builtIn = emptyList(), onPin = listOf(1.25)), stack.load(41.25, gymPlates))
        assertNull(stack.load(41.0, gymPlates))
    }

    @Test
    fun theLoadingReadsAsAHint() {
        assertEquals("pin 40", PinLoad(40.0, emptyList(), emptyList()).hint())
        assertEquals("pin 40 + 2.5 on the pin", PinLoad(40.0, emptyList(), listOf(2.5)).hint())
        assertEquals("pin 36 + add-on", PinLoad(36.0, listOf(2.5), emptyList()).hint())
        assertEquals("pin 36 + 2 add-ons", PinLoad(36.0, listOf(2.5, 2.5), emptyList()).hint())
        assertEquals("pin 36 + 2 add-ons + 2.5 + 1.25 on the pin", PinLoad(36.0, listOf(2.5, 2.5), listOf(1.25, 2.5)).hint())
    }

    @Test
    fun itSurvivesStorage() {
        listOf(
            LadderSpec.DefaultPinStack,
            LadderSpec.PinStack.steps(WeightUnit.Lb, first = 5.0, start = 10.0, step = 15.0, top = 200.0, labelKg = 1.0, labelsCut = true, builtIn = listOf(2.5, 2.5), pinPlates = false),
        ).forEach { assertEquals(it, LadderSpec.decode(it.encode())) }
    }
}
