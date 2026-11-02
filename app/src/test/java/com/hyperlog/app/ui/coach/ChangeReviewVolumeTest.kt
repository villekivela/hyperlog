package com.hyperlog.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.click
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeDiff
import com.hyperlog.app.coach.ChangeVolume
import com.hyperlog.app.coach.MuscleVolume
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.VolumeStatus
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Change's review shows the plan's sets per muscle before → after (SPEC, New program → Sets per muscle). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ChangeReviewVolumeTest {
    @get:Rule
    val compose = createComposeRule()

    private val program = Program("Full body", null, listOf(Block("b", "Build", 1, 4, listOf(Day("a", "A", emptyList())))))
    private val change = Change("Quads drop to 6 sets", Scope.RestOfBlock, listOf(Operation.AdjustSets("squat", 6)))

    private fun volume(muscle: String, sets: Double, status: VolumeStatus) = MuscleVolume(muscle, sets, NumberRange(8, 14), false, status)

    @Test
    fun theReviewListsEachMusclesSetsBeforeAndAfterAndMarksOneOutsideItsRangeButAcceptStillWorks() {
        var accepted = 0
        val review = ChangeReview(
            change, ChangeDiff(null, null, emptyList()), program, null,
            volume = ChangeVolume(
                listOf(volume("Chest", 10.0, VolumeStatus.Within), volume("Quads", 6.0, VolumeStatus.Under)),
                before = mapOf("Chest" to 10.0, "Quads" to 12.5), week = 9,
            ),
        )
        compose.setContent { HyperlogTheme { ChangeReviewScreen(review, emptyMap(), onAccept = { accepted++ }, onReject = {}) } }

        compose.onNodeWithText("Sets per muscle · week 9").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12.5 → 6 sets a week · 8–14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("under").performScrollTo().assertIsDisplayed()
        // A muscle the Change leaves alone isn't listed.
        compose.onNodeWithText("Chest").assertDoesNotExist()
        compose.click("Accept")
        assertEquals(1, accepted)
    }
}
