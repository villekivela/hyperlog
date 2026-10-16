package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Set text shows RIR as a coloured chip in place of `@ N`, read as "N RIR" (SPEC, RIR chip). */
@RunWith(RobolectricTestRunner::class)
class RirChipTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(text: String, target: Boolean = false) {
        compose.setContent { HyperlogTheme { RirText(text, target = target) } }
    }

    @Test
    fun aLoggedSetReadsItsRirAsAFilledChip() {
        show("80 × 5 @ 1")
        compose.onNodeWithText("80 × 5 1 RIR").assertIsDisplayed()
        compose.onAllNodesWithText("@", substring = true).assertCountEquals(0)
        compose.onAllNodesWithTag(RirChipLoggedTag, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(RirChipTargetTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aTargetReadsItsRirAsAnOutlinedChip() {
        show("1 × 4–6 @ 1  ·  2 × 6–8 @ 2", target = true)
        compose.onNodeWithText("1 × 4–6 1 RIR  ·  2 × 6–8 2 RIR").assertIsDisplayed()
        compose.onAllNodesWithTag(RirChipTargetTag, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun aTargetInsideALoggedLineKeepsItsOwnChip() {
        show("Set 1 was 60 kg × 8 @ 0, target @ 2 → 57.5 kg")
        compose.onNodeWithText("Set 1 was 60 kg × 8 0 RIR, target 2 RIR → 57.5 kg").assertIsDisplayed()
        compose.onAllNodesWithTag(RirChipLoggedTag, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(RirChipTargetTag, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theWordRirAfterTheNumberGoesIntoTheChip() {
        show("From old app: 60 kg × 8 @ 2 RIR, 12 Sep")
        compose.onNodeWithText("From old app: 60 kg × 8 2 RIR, 12 Sep").assertIsDisplayed()
    }

    @Test
    fun textWithoutRirIsLeftAsItIs() {
        show("25 × 10, 18 × 7")
        compose.onNodeWithText("25 × 10, 18 × 7").assertIsDisplayed()
        compose.onAllNodesWithTag(RirChipLoggedTag, useUnmergedTree = true).assertCountEquals(0)
    }
}
