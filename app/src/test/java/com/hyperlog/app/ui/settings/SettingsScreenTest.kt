package com.hyperlog.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Settings offers the one-off starting-weights import (SPEC, Fresh start). */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(status: StartingWeightsStatus, onImport: () -> Unit = {}) = compose.setContent {
        HyperlogTheme {
            SettingsScreen(
                gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                startingWeights = status, onImportStartingWeights = onImport,
            )
        }
    }

    @Test
    fun beforeAnImportItSaysWhereTheyComeFrom() {
        var picked = 0
        show(StartingWeightsStatus(count = 0, invalid = false)) { picked++ }
        compose.onNodeWithText("Starting weights from the old app").assertIsDisplayed()
        compose.onNodeWithText("From an old Hyperlog backup, no history").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun afterAnImportItCountsTheExercises() {
        show(StartingWeightsStatus(count = 42, invalid = false))
        compose.onNodeWithText("42 exercises · tap to import again").assertIsDisplayed()
    }

    @Test
    fun aFileThatIsntABackupSaysSo() {
        show(StartingWeightsStatus(count = 0, invalid = true))
        compose.onNodeWithText("That file isn't a Hyperlog backup").assertIsDisplayed()
    }
}
