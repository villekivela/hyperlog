package com.hyperlog.app.program

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanChunksTest {
    @Test
    fun setsLikeTheOneBeforeAreCountedTogetherWithTheirTechniques() {
        val top = SetPrescription(RepRange(4, 6), 1, 180)
        val backoff = SetPrescription(RepRange(6, 8), 1, 180, backoff = Backoff(0, 10))
        val myo = SetPrescription(RepRange(10, 12), 0, 90, technique = Technique.MyoReps(4))
        assertEquals(
            listOf(PlanChunk(1, RepRange(4, 6), 1, backoff = false), PlanChunk(2, RepRange(6, 8), 1, backoff = true)),
            planChunks(listOf(top, backoff, backoff)),
        )
        assertEquals(
            listOf(PlanChunk(2, RepRange(10, 12), 0, backoff = false, techniques = listOf(Technique.MyoReps(4)))),
            planChunks(listOf(myo.copy(technique = null), myo)),
        )
    }
}
