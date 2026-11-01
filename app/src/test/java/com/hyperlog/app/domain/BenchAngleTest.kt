package com.hyperlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchAngleTest {
    @Test
    fun aPhoneLyingFlatReadsZeroAndStandingUpNinety() {
        assertEquals(0, BenchAngle.tilt(0f, 0f, 9.81f))
        assertEquals(90, BenchAngle.tilt(0f, 9.81f, 0f))
        assertEquals(90, BenchAngle.tilt(9.81f, 0f, 0f))
    }

    @Test
    fun aBackrestAtThirtyDegreesReadsThirtyWhicheverWayThePhoneLies() {
        // sin 30° = 0.5, cos 30° = 0.866: the phone's top end raised, then its side, then face down.
        assertEquals(30, BenchAngle.tilt(0f, 4.905f, 8.496f))
        assertEquals(30, BenchAngle.tilt(4.905f, 0f, 8.496f))
        assertEquals(30, BenchAngle.tilt(0f, 4.905f, -8.496f))
    }

    @Test
    fun noGravityGivesNoReading() {
        assertNull(BenchAngle.tilt(0f, 0f, 0f))
    }

    @Test
    fun aTypedAngleIsWholeDegreesUpToNinety() {
        assertEquals(45, BenchAngle.typed("45"))
        assertEquals(0, BenchAngle.typed("0"))
        assertEquals(90, BenchAngle.typed("90"))
        assertNull(BenchAngle.typed("95"))
        assertNull(BenchAngle.typed(""))
    }

    @Test
    fun withinThreeDegreesOfLastTimeIsOnTarget() {
        assertTrue(BenchAngle.near(33, 30))
        assertTrue(BenchAngle.near(27, 30))
        assertFalse(BenchAngle.near(34, 30))
        assertFalse(BenchAngle.near(26, 30))
    }
}
