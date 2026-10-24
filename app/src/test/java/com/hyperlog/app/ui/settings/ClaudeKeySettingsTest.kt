package com.hyperlog.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings → Claude key (SPEC, Coaching → With a Claude key): entered, checked, shown by its
 * ending with Remove, and this month's cost under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ClaudeKeySettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private var status by mutableStateOf(ClaudeKeyStatus())
    private val saved = mutableListOf<String>()
    private var removed = 0
    private var dismissed = 0

    private fun show() = compose.setContent {
        HyperlogTheme {
            SettingsScreen(
                gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                startingWeights = StartingWeightsStatus(count = 0, invalid = false), onImportStartingWeights = {},
                claudeKey = status,
                onSaveKey = { saved += it },
                onRemoveKey = { removed++ },
                onDismissKey = { dismissed++ },
            )
        }
    }

    @Test
    fun aKeyIsEnteredAndCheckedBeforeItIsShownByItsEnding() {
        show()
        compose.onNodeWithText("Claude key").assertIsDisplayed()
        compose.onNodeWithText("Ask Claude from the app with an API key").performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("API key").performTextInput("  sk-ant-api03-test-a1b2 ")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf("sk-ant-api03-test-a1b2"), saved)

        status = ClaudeKeyStatus(check = KeyCheck.Checking)
        compose.onNodeWithText("Checking the key…").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        status = ClaudeKeyStatus(ending = "a1b2", monthCost = 2_400_000)
        compose.onNodeWithText("API key").assertDoesNotExist()
        compose.onNodeWithText("Key ending …a1b2").assertIsDisplayed()
        compose.onNodeWithText("This month: ≈ $2.40").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRefusedKeySaysSoAndIsntSaved() {
        show()
        compose.onNodeWithText("Claude key").performClick()
        compose.onNodeWithText("API key").performTextInput("sk-ant-wrong")
        compose.onNodeWithText("Save").performClick()
        status = ClaudeKeyStatus(check = KeyCheck.Refused)
        compose.onNodeWithText("Claude doesn't accept this key.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
        compose.onNodeWithText("API key").assertDoesNotExist()
        compose.onNodeWithText("Ask Claude from the app with an API key").assertIsDisplayed()
    }

    @Test
    fun noConnectionSaysToTryAgain() {
        show()
        compose.onNodeWithText("Claude key").performClick()
        compose.onNodeWithText("API key").performTextInput("sk-ant-api03-test-a1b2")
        compose.onNodeWithText("Save").performClick()
        status = ClaudeKeyStatus(check = KeyCheck.Unreachable)
        compose.onNodeWithText("Couldn't reach Claude. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun removeForgetsTheKey() {
        status = ClaudeKeyStatus(ending = "a1b2")
        show()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
        compose.onNodeWithText("This month: ≈ $0.00").performScrollTo().assertIsDisplayed()
    }
}
