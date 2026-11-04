package com.hyperlog.app.mannequin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which bench angle the mannequin shows, and which baked clip it plays at it (SPEC, Technique videos → The mannequin). */
class BenchClipsTest {
    private val camera = StageCamera(listOf(0f, 0.5f, 0f), 1f, 60f, 10f)
    private val bench = BenchClips(
        typical = 30,
        hinge = listOf(0.44f, -0.1f),
        clips = (listOf(85) + (0..75 step 15)).associateWith { BenchClip("mannequins/press_$it.glb", camera) },
    )

    @Test
    fun theSessionsAngleComesFirst() {
        assertEquals(40, bench.shown(session = 40, last = 20).angle)
    }

    @Test
    fun withNoneThisSessionItIsLastTimes() {
        assertEquals(20, bench.shown(session = null, last = 20).angle)
    }

    @Test
    fun withNeitherItIsTheTypicalAngle() {
        assertEquals(30, bench.shown(session = null, last = null).angle)
    }

    @Test
    fun theClipPlayedIsTheNearestBakedAngle() {
        assertEquals(30, bench.shown(37, null).baked)
        assertEquals(45, bench.shown(38, null).baked)
        assertEquals(0, bench.shown(7, null).baked)
        assertEquals(15, bench.shown(8, null).baked)
        assertEquals("mannequins/press_45.glb", bench.shown(38, null).clip.file)
    }

    @Test
    fun theBenchIsTurnedToTheExactAngle() {
        assertEquals(37, bench.shown(37, null).angle)
    }

    @Test
    fun anAnglePastTheBakedRangeUsesTheEndClipAndKeepsItsAngle() {
        // The bench angle goes 0 to 90; the clips stop at 85.
        assertEquals(85, bench.shown(90, null).baked)
        assertEquals(90, bench.shown(90, null).angle)
    }

    @Test
    fun theNearestIsFoundAmongTheAnglesTheIndexListsNotAFixedRange() {
        val sparse = BenchClips(30, bench.hinge, listOf(0, 30, 60).associateWith { BenchClip("c$it", camera) })
        assertEquals(30, sparse.shown(40, null).baked)
        assertEquals(60, sparse.shown(46, null).baked)
    }

    @Test
    fun theBackrestTurnsAboutTheHingeAsAColumnMajorTransform() {
        // A point on the pad, 1 m behind the hinge (z = -1 from it), turned up 90° about x, lands 1 m above the hinge.
        val m = benchBackTransform(90, listOf(0.4f, -0.02f))
        fun apply(y: Float, z: Float) = floatArrayOf(m[4] * y + m[8] * z + m[12], m[5] * y + m[9] * z + m[13], m[6] * y + m[10] * z + m[14])
        val at = apply(0f, -1f)
        assertEquals(0f, at[0], 1e-5f)
        assertEquals(1.4f, at[1], 1e-5f)
        assertEquals(-0.02f, at[2], 1e-5f)
        // Flat leaves the pad where it was, behind the hinge.
        val flat = benchBackTransform(0, listOf(0.4f, -0.02f))
        assertEquals(0.4f, flat[13], 1e-6f)
        assertEquals(-0.02f, flat[14], 1e-6f)
        assertEquals(1f, flat[0], 0f)
        assertEquals(1f, flat[15], 0f)
    }
}
