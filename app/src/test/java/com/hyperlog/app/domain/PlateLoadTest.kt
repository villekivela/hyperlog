package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The plate calculator: the plates on each side for a weight (SPEC, Screens → Today). */
class PlateLoadTest {
    private val plates = LadderSpec.DefaultPlates

    @Test
    fun ninetyKgOnTheBarIsTwentyAndFifteenASide() {
        assertEquals(listOf(20.0, 15.0), plates.perSide(90.0))
    }

    @Test
    fun smallPlatesMakeUpTheRest() {
        assertEquals(listOf(20.0, 5.0, 2.5, 1.25), plates.perSide(77.5))
    }

    @Test
    fun theEmptyBarHasNoPlates() {
        assertEquals(emptyList<Double>(), plates.perSide(20.0))
    }

    @Test
    fun aWeightThePlatesCantMakeHasNoLoading() {
        assertNull(plates.perSide(21.0))
        assertNull(plates.perSide(15.0))
        assertNull(plates.perSide(500.0))
    }

    @Test
    fun theFewestPlatesWinEvenWhenTheHeaviestPlateDoesntFit() {
        // 25 a side: a 20 leaves 5, which this set doesn't have, so it is 15 + 10.
        assertEquals(listOf(15.0, 10.0), LadderSpec.Plates(20.0, listOf(20.0, 15.0, 10.0)).perSide(70.0))
    }

    @Test
    fun aPlateLoadedMachineCountsFromItsSled() {
        assertEquals(listOf(20.0, 20.0), LadderSpec.Plates(50.0, listOf(20.0, 20.0, 10.0)).perSide(130.0))
    }
}
