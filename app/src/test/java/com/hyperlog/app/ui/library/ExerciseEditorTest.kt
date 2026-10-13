package com.hyperlog.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor shows the exercise as the library holds it, even when the library catches up after it opens. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ExerciseEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val pecDeck = Exercise(
        id = "pec_deck", name = "Pec Deck", equipment = Equipment.Machine, pattern = MovementPattern.ChestFly,
        grip = GripDemand.None, muscles = listOf("Chest"), note = "Old note.",
    )

    @Test
    fun anExerciseSavedJustBeforeOpeningShowsItsNewValues() {
        var exercise by mutableStateOf(pecDeck)
        compose.setContent { ExerciseEditor(exercise, onSave = {}, onSetHidden = {}, onClose = {}) }
        compose.onNodeWithText("Old note.").assertIsDisplayed()

        // The library's list lands after the editor opened on the stale copy.
        exercise = pecDeck.copy(note = "Elbows high, squeeze for a second.")
        compose.waitForIdle()
        compose.onNodeWithText("Elbows high, squeeze for a second.").assertIsDisplayed()
    }

    @Test
    fun aDumbbellExerciseSaysWhetherItUsesTwo() {
        val bench = Exercise(
            id = "db_bench_press", name = "DB Bench Press", equipment = Equipment.Dumbbell, pattern = MovementPattern.HorizontalPush,
            grip = GripDemand.High, muscles = listOf("Chest"), twoDumbbells = true,
        )
        var saved: Exercise? = null
        compose.setContent { ExerciseEditor(bench, onSave = { saved = it }, onSetHidden = {}, onClose = {}) }
        compose.onNodeWithText("Two dumbbells").performScrollTo().assertIsOn()
        compose.onNodeWithText("Two dumbbells").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(false, saved!!.twoDumbbells)
    }

    @Test
    fun onlyDumbbellExercisesAskAboutTwo() {
        compose.setContent { ExerciseEditor(pecDeck, onSave = {}, onSetHidden = {}, onClose = {}) }
        assertEquals(0, compose.onAllNodesWithText("Two dumbbells").fetchSemanticsNodes().size)
    }

    @Test
    fun hidingKeepsWhatIsTyped() {
        var exercise by mutableStateOf(pecDeck)
        compose.setContent { ExerciseEditor(exercise, onSave = {}, onSetHidden = {}, onClose = {}) }
        compose.onNodeWithText("Old note.").assertIsDisplayed()
        exercise = pecDeck.copy(hidden = true)
        compose.waitForIdle()
        compose.onNodeWithText("Old note.").assertIsDisplayed()
    }
}
