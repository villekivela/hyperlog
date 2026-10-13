package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightCheckTest {
    private val dumbbells = LadderSpec.OldDefaultDumbbells.ladder()

    @Test
    fun tenTimesTheLastSetOffersWeightsNearTheLastSet() {
        val check = WeightCheck.of(255.0, Reference(25.0, fromThisSession = true), dumbbells, ladderKnown = true)!!
        assertEquals("10× last set", check.reason)
        assertEquals(listOf(22.5, 25.0, 30.0), check.options)
    }

    @Test
    fun halfOrLessIsChecked() {
        val check = WeightCheck.of(2.5, Reference(25.0, fromThisSession = false), WeightLadder.Default, ladderKnown = false)!!
        assertEquals("1/10 of last time", check.reason)
        assertEquals(listOf(22.5, 25.0, 27.5), check.options)
    }

    @Test
    fun aWeightOffTheLadderOffersTheRungsAroundIt() {
        val check = WeightCheck.of(23.0, Reference(22.5, fromThisSession = true), dumbbells, ladderKnown = true)!!
        assertEquals("Not on this equipment", check.reason)
        assertEquals(listOf(20.0, 22.5, 25.0), check.options)
    }

    @Test
    fun nearbyRealWeightsPass() {
        assertNull(WeightCheck.of(30.0, Reference(25.0, fromThisSession = true), dumbbells, ladderKnown = true))
        assertNull(WeightCheck.of(37.0, Reference(25.0, fromThisSession = true), dumbbells, ladderKnown = false))
        assertNull(WeightCheck.of(18.0, null, dumbbells, ladderKnown = true))
    }

    @Test
    fun nothingOnTheBeltAfterALoadedSetPasses() {
        // A weighted pull-up at bodyweight alone after 10 kg on the belt (no known ladder).
        assertNull(WeightCheck.of(0.0, Reference(10.0, fromThisSession = true), WeightLadder.Default, ladderKnown = false))
        // On real equipment 0 kg is still off the ladder.
        assertEquals("Not on this equipment", WeightCheck.of(0.0, Reference(20.0, fromThisSession = true), dumbbells, ladderKnown = true)!!.reason)
    }
}
