package com.hyperlog.app.ui.gym

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class LadderEditorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theLbStackPresetShowsItsRealWeightsInKgAndSaves() {
        var saved: Pair<String, LadderSpec>? = null
        compose.setContent {
            HyperlogTheme {
                LadderEditor(
                    title = "New equipment", name = "", spec = null, kinds = LadderKind.entries,
                    onSave = { n, s -> saved = n to s }, onDelete = null, onClose = {},
                )
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Name, e.g. Cable station").performTextInput("Cable station")
        compose.onNodeWithText("lb stack: 2.5, then 5 lb steps").performClick()

        compose.onNodeWithText("1.1 · 3.4 · 5.7 · 7.9 · 10.2 · 12.5 … 89.6 kg").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals("Cable station" to LadderSpec.lbStack(), saved)
    }

    @Test
    fun platesTakeABaseAndPairs() {
        var saved: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                LadderEditor(
                    title = "Barbell plates", name = null, spec = LadderSpec.Plates(20.0, emptyList()), kinds = listOf(LadderKind.Plates),
                    onSave = { _, s -> saved = s }, onDelete = null, onClose = {},
                )
            }
        }
        compose.onNodeWithText("Pairs of plates in kg: one number a pair, or 3×20").performTextInput("10 2,5")
        compose.onNodeWithText("20 · 25 · 40 · 45 kg").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(LadderSpec.Plates(20.0, listOf(10.0, 2.5)), saved)
    }

    @Test
    fun aPlateLoadedMachineOpensOnPlatesWithTheGymsPlatesAndAZeroStart() {
        var saved: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                LadderEditor(
                    title = "New equipment", name = "", spec = null, kinds = LadderKind.entries,
                    onSave = { _, s -> saved = s }, onDelete = null, onClose = {},
                    defaultKind = LadderKind.Plates, defaultPlates = LadderSpec.DefaultPlates,
                )
            }
        }
        compose.onNodeWithText("Name, e.g. Cable station").performTextInput("Leg press")
        compose.onNodeWithText("Starting weight (empty machine or sled), kg").assertExists()
        // The shared plates, three pairs of 20 written as such, so all of them load.
        compose.onNodeWithText("3×20 15 10 5 2.5 1.25").assertExists()
        compose.onNodeWithText("… 187.5 kg", substring = true).assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(LadderSpec.Plates(0.0, LadderSpec.DefaultPlates.pairs.sortedDescending()), saved)
    }

    @Test
    fun pairsOfTheSamePlateCanBeWrittenAsACount() {
        var saved: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                LadderEditor(
                    title = "Leg press", name = "Leg press", spec = LadderSpec.Plates(40.0, emptyList()), kinds = LadderKind.entries,
                    onSave = { _, s -> saved = s }, onDelete = null, onClose = {},
                )
            }
        }
        compose.onNodeWithText("Pairs of plates in kg: one number a pair, or 3×20").performTextInput("4x25 2,5")
        compose.onNodeWithText("Save").performClick()
        assertEquals(LadderSpec.Plates(40.0, listOf(25.0, 25.0, 25.0, 25.0, 2.5)), saved)
    }
}
