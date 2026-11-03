package com.hyperlog.app.mannequin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An exercise done with a different grip set by set has a clip per grip, in the order the sets take them (SPEC, Technique videos → The mannequin). */
class GripClipsTest {
    private val index = """
        {"clips": {"2_grip_lat_pulldown": {
          "clip": "2_grip_lat_pulldown_wide.glb", "equipment": "cable",
          "camera": {"target": [0, 1.1, 0.4], "halfHeight": 1.2, "azimuth": 62, "elevation": 8},
          "tempo": {"lowering": 1, "bottom": 0.3, "lifting": 2, "top": 0.5},
          "resources": ["mannequin.bin"],
          "grips": [
            {"name": "wide", "clip": "2_grip_lat_pulldown_wide.glb", "camera": {"target": [0, 1.1, 0.4], "halfHeight": 1.2, "azimuth": 62, "elevation": 8}},
            {"name": "narrow", "clip": "2_grip_lat_pulldown_narrow.glb", "camera": {"target": [0, 1.0, 0.4], "halfHeight": 1.1, "azimuth": 62, "elevation": 8}}
          ]
        }}}
    """.trimIndent()

    private val clip = Mannequins.parse(index).clips.single()

    @Test
    fun eachGripHasItsClipInTheOrderTheSetsTakeThem() {
        assertEquals(listOf("wide", "narrow"), clip.grips!!.map { it.name })
        assertEquals("mannequins/2_grip_lat_pulldown_narrow.glb", clip.grips!![1].file)
    }

    @Test
    fun eachSetTakesItsGripAndSetsPastTheLastKeepTheLast() {
        assertEquals("wide", clip.gripForSet(0).name)
        assertEquals("narrow", clip.gripForSet(1).name)
        assertEquals("narrow", clip.gripForSet(4).name)
    }

    @Test
    fun anExerciseWithOneGripHasNone() {
        val oneGrip = index.substringBefore(",\n  \"grips\"") + "\n}}}"
        assertNull(Mannequins.parse(oneGrip).clips.single().grips)
    }
}
