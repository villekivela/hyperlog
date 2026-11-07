package com.hyperlog.app.mannequin

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which rep an exercise draws: its own, its pattern's, or any (#271). */
class ExercisePosesTest {
    private val poses = ExercisePoses.parse(
        """
        { "joints": ["pelvis"], "poses": {
          "hack_squat": { "start": [1, 2], "end": [3, 4], "far": "l", "equipment": "machine", "pattern": "squat" },
          "barbell_rdl": { "start": [5, 6], "end": [7, 8], "far": "r", "equipment": "barbell", "pattern": "hinge" }
        } }
        """,
    )

    private fun exercise(id: String, pattern: MovementPattern, equipment: Equipment = Equipment.Machine) =
        Exercise(id, id, equipment, pattern, GripDemand.None, listOf("Quads"))

    @Test
    fun anExerciseWithAClipDrawsItsOwnRep() {
        val shown = poses.poseFor(exercise("hack_squat", MovementPattern.Squat))!!
        assertTrue(shown.exact)
        assertEquals(listOf(1, 2), shown.pose.start)
        assertEquals(listOf(3, 4), shown.pose.end)
        assertEquals("l", shown.pose.far)
        assertEquals(listOf("pelvis"), shown.pose.joints)
    }

    @Test
    fun oneNotYetPosedDrawsItsPatternsRepMarkedAsNotItsOwn() {
        val shown = poses.poseFor(exercise("belt_squat", MovementPattern.Squat))!!
        assertFalse(shown.exact)
        assertEquals(listOf(3, 4), shown.pose.end)
    }

    /** As the clips: a pose shows a shipped exercise in its own equipment (Mannequins.clipFor). */
    @Test
    fun anExerciseEditedToOtherEquipmentIsNoLongerItsOwnRep() {
        assertFalse(poses.poseFor(exercise("hack_squat", MovementPattern.Squat, Equipment.Barbell))!!.exact)
    }

    @Test
    fun aPatternWithNoPosesStillDrawsARep() {
        val shown = poses.poseFor(exercise("wrist_curl", MovementPattern.CalfRaise))!!
        assertFalse(shown.exact)
    }

    /** The figure does the rep: the clip's two ends, and every place between. */
    @Test
    fun aRepRunsFromItsStartToItsEnd() {
        val pose = RepPose(listOf("pelvis"), listOf(100, 200), listOf(300, 600), "l")
        assertEquals(listOf(100, 200), pose.at(0f))
        assertEquals(listOf(300, 600), pose.at(1f))
        assertEquals(listOf(200, 400), pose.at(0.5f))
    }

    @Test
    fun noPosesAtAllDrawNothing() {
        assertNull(ExercisePoses.parse("""{ "joints": [], "poses": {} }""").poseFor(exercise("x", MovementPattern.Squat)))
    }
}
