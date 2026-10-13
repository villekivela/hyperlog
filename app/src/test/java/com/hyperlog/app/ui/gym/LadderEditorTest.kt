package com.hyperlog.app.ui.gym

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        compose.onNodeWithText("Stack").performClick()
        compose.onNodeWithText("lb stack: 2.5, then 5 lb steps").performClick()

        compose.onNodeWithText("1.1–89.6 kg by 5 lb · 40 weights").assertExists()
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
        compose.onNodeWithText("20 kg + plates, to 45 kg · 4 weights").assertExists()
        compose.onNodeWithText("Check: 20 · 25 · 40 …, looks right?").assertExists()
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
        compose.onNodeWithText("to 187.5 kg", substring = true).assertExists()
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

    @Test
    fun aPinStackTakesItsAddOnsAsACountAndTheGymsPinPlates() {
        var saved: LadderSpec? = null
        compose.setContent {
            HyperlogTheme {
                LadderEditor(
                    title = "New equipment", name = "", spec = null, kinds = LadderKind.Items,
                    onSave = { _, s -> saved = s }, onDelete = null, onClose = {}, pinPlates = listOf(1.25),
                )
            }
        }
        compose.onNodeWithText("Name, e.g. Cable station").performTextInput("Chest press")
        compose.onNodeWithText("Add-ons on the machine, kg (2×2.5)").performTextInput("2x2.5")
        // Pins 5 to 100 in 5s, two 2.5 kg add-ons, the gym's 1.25 on the pin: every 1.25 from 5 to 106.25.
        compose.onNodeWithText("5–100 kg by 5 + add-ons · 82 weights").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(LadderSpec.DefaultPinStack.copy(builtIn = listOf(2.5, 2.5)), saved)
    }

    private fun reading(name: String?, onSave: (LadderSpec) -> Unit) = compose.setContent {
        HyperlogTheme {
            LadderEditor(
                title = "New equipment", name = name, spec = null, kinds = if (name != null) LadderKind.Items else listOf(LadderKind.List, LadderKind.Stack),
                onSave = { _, s -> onSave(s) }, onDelete = null, onClose = {}, readable = true,
            )
        }
    }

    @Test
    fun aStackIsReadFromThreeLabelsAndChecked() {
        var saved: LadderSpec? = null
        reading("") { saved = it }
        compose.onNodeWithText("Name, e.g. Cable station").performTextInput("Lat pulldown")
        compose.onNodeWithText("Type 2–3 weights from the stack").assertExists()
        compose.onNodeWithTag(ReadingFieldTag).performTextInput("36 43 49")
        compose.onNodeWithText("Reads as 15 lb steps").assertExists()
        compose.onNodeWithText("Check: 36 · 43 · 49 …, looks right?").assertExists()
        compose.onNodeWithText("Save").performClick()
        val stack = saved as LadderSpec.PinStack
        assertEquals(com.hyperlog.app.domain.WeightUnit.Lb, stack.unit)
        assertEquals(listOf(36.0, 43.0, 49.0), stack.pins.take(3))
    }

    @Test
    fun platesThatSkipAskForTheOnesBetweenAndCantBeSaved() {
        reading("") {}
        compose.onNodeWithText("Name, e.g. Cable station").performTextInput("Lat pulldown")
        compose.onNodeWithTag(ReadingFieldTag).performTextInput("36 49")
        compose.onNodeWithText("Type plates next to each other, from the lightest").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun aRackWhoseStepChangesIsSummedUpAndOpensToEveryWeight() {
        var saved: LadderSpec? = null
        reading(null) { saved = it }
        compose.onNodeWithTag(ReadingFieldTag).performTextInput("1 2 3 … 10 12,5 15 … 45")
        compose.onNodeWithText("1–10 kg by 1 · 12.5–45 by 2.5 · 24 weights").assertExists()
        compose.onNodeWithTag(LadderPreviewTag).performClick()
        compose.onNodeWithText("1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 · 9 · 10 · 12.5", substring = true).assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(LadderSpec.Explicit((1..10).map { it.toDouble() } + (0..13).map { 12.5 + it * 2.5 }), saved)
    }

    @Test
    fun editExactlyGivesTheFieldsAndBack() {
        reading("") {}
        compose.onNodeWithText("Edit exactly").performClick()
        compose.onNodeWithText("Stack").assertExists()
        compose.onNodeWithText("Type from the stack instead").performClick()
        compose.onNodeWithTag(ReadingFieldTag).assertExists()
    }
}
