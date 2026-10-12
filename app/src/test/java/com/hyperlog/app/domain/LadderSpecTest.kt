package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LadderSpecTest {
    @Test
    fun anLbStackShowsInKg() {
        val stack = LadderSpec.lbStack()
        assertEquals(listOf(1.1, 3.4, 5.7, 7.9, 10.2), stack.ladder().rungs.take(5))
    }

    @Test
    fun aKgStackStepsFromItsStart() {
        val stack = LadderSpec.Steps(start = 5.0, step = 2.5, unit = WeightUnit.Kg, count = 4)
        assertEquals(listOf(5.0, 7.5, 10.0, 12.5), stack.ladder().rungs)
    }

    @Test
    fun theDefaultDumbbellsFollowTheSpec() {
        val rungs = LadderSpec.DefaultDumbbells.ladder().rungs
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.5, 25.0, 30.0, 35.0), rungs.take(14))
    }

    @Test
    fun platesLoadInPairsOnTheBar() {
        val bar = LadderSpec.Plates(baseKg = 20.0, pairs = listOf(10.0, 5.0, 2.5))
        // Each pair adds twice its weight: 20, 25, 30, 35, 40, 45, 50, 55.
        assertEquals(listOf(20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0), bar.ladder().rungs)
    }

    @Test
    fun anExplicitListIsSortedWithoutRepeats() {
        assertEquals(listOf(4.5, 9.0, 13.5), LadderSpec.Explicit(listOf(9.0, 4.5, 13.5, 9.0)).ladder().rungs)
    }

    @Test
    fun everySpecSurvivesStorage() {
        listOf(
            LadderSpec.lbStack(),
            LadderSpec.Steps(5.0, 2.5, WeightUnit.Kg, 30),
            LadderSpec.Explicit(listOf(1.25, 2.5)),
            LadderSpec.Plates(20.0, listOf(20.0, 20.0, 10.0, 1.25)),
        ).forEach { assertEquals(it, LadderSpec.decode(it.encode())) }
        assertNull(LadderSpec.decode("nonsense"))
    }
}
