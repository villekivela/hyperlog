package com.hyperlog.app.mannequin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where in the baked lowering the mannequin is, through the loop of one rep. */
class RepTimelineTest {
    private val rep = RepTimeline(lowering = 1.5f, bottom = 0.25f, lifting = 1.1f, top = 0.7f)
    private fun at(seconds: Float) = rep.position(seconds)

    @Test
    fun itLowersHoldsLiftsAndHoldsAgainThenLoops() {
        assertEquals(0f, at(0f), 1e-4f)
        assertEquals(0.5f, at(0.75f), 1e-4f)
        assertEquals(1f, at(1.5f), 1e-4f)
        assertEquals(1f, at(1.7f), 1e-4f)
        assertEquals(0.5f, at(1.75f + 0.55f), 1e-4f)
        assertEquals(0f, at(2.85f), 1e-4f)
        assertEquals(0f, at(3.5f), 1e-4f)
        assertEquals(0.5f, at(3.55f + 0.75f), 1e-4f)
        assertEquals(3.55f, rep.length, 1e-4f)
    }

    @Test
    fun itEasesInAndOutOfEachMove() {
        // A quarter of the way through the lowering, it has covered less than a quarter of it.
        assertEquals(0.15625f, at(0.375f), 1e-4f)
    }
}
