package com.hyperlog.app.domain

import com.hyperlog.app.domain.StackReading.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a weight stack from a few weights printed on it, and learning one from weights logged on
 * a machine (SPEC, Gyms and equipment → Setting up a stack from its labels).
 */
class StackReadingTest {
    private fun read(vararg printed: Double, unit: WeightUnit = WeightUnit.Kg, top: Double? = null, builtIn: List<Double> = emptyList()) =
        StackReading.read(printed.toList(), unit, top, builtIn)

    private fun Result.stack(): LadderSpec.PinStack = (this as Result.Read).spec

    // kg steps

    @Test
    fun threeKgPlatesGiveTheStartAndStep() {
        val stack = read(5.0, 10.0, 15.0).stack()
        assertEquals(WeightUnit.Kg, stack.unit)
        assertEquals(listOf(5.0, 10.0, 15.0, 20.0), stack.pins.take(4))
    }

    @Test
    fun twoPlatesAreEnoughForAnEvenStack() {
        assertEquals(listOf(2.5, 5.0, 7.5), read(2.5, 5.0).stack().pins.take(3))
    }

    @Test
    fun withoutATopTheStackHasTwentyPlates() {
        val pins = read(5.0, 10.0).stack().pins
        assertEquals(20, pins.size)
        assertEquals(100.0, pins.last(), 0.0)
    }

    @Test
    fun theTopOrALastPlateSetsTheLength() {
        assertEquals(listOf(5.0, 10.0, 15.0, 20.0), read(5.0, 10.0, top = 20.0).stack().pins)
        // A top between plates stops at the plate below it.
        assertEquals(80.0, read(5.0, 10.0, top = 82.0).stack().pins.last(), 0.0)
    }

    @Test
    fun aLighterFirstPlateNeedsThreeValues() {
        // 2.5, then steps of 5 from 5.
        val stack = read(2.5, 5.0, 10.0).stack()
        assertEquals(listOf(2.5, 5.0, 10.0, 15.0, 20.0), stack.pins.take(5))
        assertEquals(2.5, stack.first!!, 0.0)
    }

    @Test
    fun dumbbellsReadLikeAStack() {
        val pins = read(1.0, 2.0, 3.0, top = 45.0).stack().pins
        assertEquals(45, pins.size)
        assertEquals(listOf(1.0, 2.0, 3.0), pins.take(3))
    }

    // lb stacks

    @Test
    fun kgLabelsOfAnLbStackAreReadAsLb() {
        // 10 lb plates labelled to 0.1 kg.
        val stack = read(4.5, 9.1, 13.6).stack()
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(10.0, stack.step, 0.0)
        assertEquals(listOf(4.5, 9.1, 13.6, 18.1), stack.pins.take(4))
    }

    @Test
    fun fiveLbStepsInKgLabels() {
        val stack = read(2.3, 4.5, 6.8).stack()
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(5.0, stack.step, 0.0)
    }

    @Test
    fun wholeKgLabelsOfFifteenLbPlatesAreReadAsLbAndKeepTheirLabels() {
        // 80, 95 and 110 lb are 36.3, 43.1 and 49.9 kg; this machine's labels cut them to 36, 43, 49.
        val stack = read(36.0, 43.0, 49.0).stack()
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(15.0, stack.step, 0.0)
        assertEquals(listOf(36.0, 43.0, 49.0), stack.pins.take(3))
        assertTrue(stack.labelsCut)
        assertEquals(1.0, stack.labelKg, 0.0)
    }

    @Test
    fun roundedWholeKgLabelsStayRounded() {
        val stack = read(36.0, 43.0, 50.0).stack()
        assertEquals(listOf(36.0, 43.0, 50.0, 57.0), stack.pins.take(4))
        assertEquals(false, stack.labelsCut)
    }

    @Test
    fun anEvenKgStepIsPreferredToAnLbExplanation() {
        // 10 and 20 kg are also within a kg of 22.5 and 45 lb, but kg steps fit cleanly.
        assertEquals(WeightUnit.Kg, read(10.0, 20.0, 30.0).stack().unit)
    }

    @Test
    fun aStackCanBeTypedInLb() {
        val stack = read(10.0, 20.0, 30.0, unit = WeightUnit.Lb).stack()
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(listOf(4.5, 9.1, 13.6), stack.pins.take(3))
    }

    @Test
    fun anLbTopIsTypedLikeTheLabels() {
        val stack = read(4.5, 9.1, 13.6, top = 90.7).stack()
        assertEquals(90.7, stack.pins.last(), 0.0)
        assertEquals(20, stack.pins.size)
    }

    @Test
    fun theLbPresetsOffsetReads() {
        // 2.5 lb, then 5 lb steps: 1.1, 3.4, 5.7 kg.
        val stack = read(1.1, 3.4, 5.7).stack()
        assertEquals(WeightUnit.Lb, stack.unit)
        assertEquals(2.5, stack.start, 0.0)
        assertEquals(5.0, stack.step, 0.0)
    }

    // add-ons

    @Test
    fun theAddOnsComeThrough() {
        val stack = read(5.0, 10.0, builtIn = listOf(2.5, 2.5)).stack()
        assertEquals(listOf(2.5, 2.5), stack.builtIn)
        assertEquals(listOf(5.0, 7.5, 10.0, 12.5, 15.0), stack.ladder().rungs.take(5))
    }

    // ambiguous input asks for one more

    @Test
    fun oneWeightIsNotEnough() {
        assertEquals(Result.OneMore(StackReading.Need.More), read(5.0))
        assertEquals(Result.OneMore(StackReading.Need.More), read())
    }

    @Test
    fun platesThatArentNextToEachOtherAskForTheOnesBetween() {
        // 36 and 49 could be 15 lb plates two apart, or 10 lb plates three apart.
        assertEquals(Result.OneMore(StackReading.Need.NextToEachOther), read(36.0, 49.0))
    }

    @Test
    fun aGapInTypedLbPlatesAsksForTheOnesBetween() {
        assertEquals(Result.OneMore(StackReading.Need.NextToEachOther), read(10.0, 20.0, 40.0, 50.0, unit = WeightUnit.Lb))
    }

    @Test
    fun aRepeatedOrFallingWeightIsNotAStack() {
        assertEquals(Result.OneMore(StackReading.Need.More), read(5.0, 5.0))
        assertEquals(StackReading.read(listOf(5.0, 10.0, 15.0), WeightUnit.Kg), StackReading.read(listOf(15.0, 5.0, 10.0), WeightUnit.Kg))
    }

    // learning from logged weights

    private fun learn(vararg kg: Double, on: LadderSpec = LadderSpec.DefaultPinStack, plates: List<Double> = LadderSpec.DefaultPinPlates) =
        StackReading.learn(kg.toList(), on, plates)

    @Test
    fun weightsOnTheGuessTeachNothing() {
        assertNull(learn(40.0, 42.5, 45.0, 46.25))
    }

    @Test
    fun loggedLbLabelsOffAKgGuessReadAsAnLbStack() {
        val learned = learn(22.7, 27.2, 31.8)!!
        assertEquals(WeightUnit.Lb, learned.unit)
        assertEquals(10.0, learned.step, 0.0)
        assertTrue(listOf(22.7, 27.2, 31.8).all { kg -> learned.pins.any { kotlin.math.abs(it - kg) < 0.05 } })
    }

    @Test
    fun loggedKgOffAnLbGuessReadAsKgSteps() {
        val learned = learn(40.0, 45.0, 55.0, on = LadderSpec.lbStack())!!
        assertEquals(WeightUnit.Kg, learned.unit)
        assertEquals(5.0, learned.step, 0.0)
    }

    @Test
    fun twoLoggedWeightsAreNotEnoughToLearnFrom() {
        assertNull(learn(22.7, 27.2))
    }

    @Test
    fun loggedWeightsThatFitTwoStacksWaitForMore() {
        // 80, 110 and 140 lb fit 10 and 15 lb steps alike.
        assertNull(learn(36.0, 50.0, 64.0))
    }

    @Test
    fun theLearnedStackKeepsTheMachinesAddOns() {
        val learned = learn(22.7, 27.2, 31.8, on = LadderSpec.DefaultPinStack.copy(builtIn = listOf(2.5)))!!
        assertEquals(listOf(2.5), learned.builtIn)
    }

    @Test
    fun theLearnedStackReachesTheHeaviestWeightLogged() {
        val learned = learn(22.7, 27.2, 31.8, 113.4)!!
        assertTrue(learned.pins.last() >= 113.4)
    }

    // shorthand with steps that change partway

    private fun readText(text: String) = StackReading.read(text, WeightUnit.Kg)

    @Test
    fun aRackWhoseStepChangesIsWrittenWithGaps() {
        val rack = readText("1, 2, 3 … 10, 12.5, 15 … 45").stack()
        assertEquals((1..10).map { it.toDouble() } + (0..13).map { 12.5 + it * 2.5 }, rack.plates)
        assertEquals("1–10 kg by 1 · 12.5–45 by 2.5", StackReading.summary(rack))
    }

    @Test
    fun twoDotsAndDecimalCommasWorkToo() {
        assertEquals(readText("1, 2, 3 … 10, 12.5, 15 … 45"), readText("1 2 3 .. 10 12,5 15 .. 45"))
        assertEquals(readText("1, 2, 3 … 10, 12.5, 15 … 45"), readText("1, 2, 3 … 10, 12,5, 15 … 45"))
    }

    @Test
    fun aLastGapIsTheTop() {
        assertEquals(read(1.0, 2.0, 3.0, top = 45.0), readText("1 2 3 … 45"))
        assertEquals(read(4.5, 9.1, 13.6, top = 90.7), readText("4,5 9,1 13,6 … 90,7"))
    }

    @Test
    fun aGapBeforeAnyStepAsksForMore() {
        assertEquals(Result.OneMore(StackReading.Need.More), readText("5 … 100 … 120"))
    }

    @Test
    fun somethingThatIsntAWeightIsNoStack() {
        assertEquals(Result.NoStack, readText("5 ten 15"))
        assertNull(StackReading.parse("abc"))
    }

    @Test
    fun exactPlatesFillGapsWithTheStepBefore() {
        assertEquals(listOf(5.0, 10.0, 15.0, 20.0), StackReading.exact("5 10 … 20"))
        assertNull(StackReading.exact("5 … 20"))
    }

    @Test
    fun theSummaryNamesALighterFirstPlateAndLbSteps() {
        assertEquals("2.5, then 5–100 kg by 5", StackReading.summary(read(2.5, 5.0, 10.0, top = 100.0).stack()))
        assertEquals("4.5–90.7 kg by 10 lb", StackReading.summary(read(4.5, 9.1, 13.6).stack()))
        assertEquals("5–100 kg by 5", StackReading.summary(LadderSpec.DefaultPinStack))
    }

    // in words

    @Test
    fun aStackReadsAsItsSteps() {
        assertEquals("5 kg steps", StackReading.steps(LadderSpec.DefaultPinStack))
        assertEquals("10 lb steps", StackReading.steps(read(4.5, 9.1, 13.6).stack()))
        assertEquals("2.5, then 5 kg steps", StackReading.steps(read(2.5, 5.0, 10.0).stack()))
    }
}
