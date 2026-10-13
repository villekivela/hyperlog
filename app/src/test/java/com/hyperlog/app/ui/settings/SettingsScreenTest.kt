package com.hyperlog.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.backup.Backups
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings offers backups (SPEC, Backups) and the one-off starting-weights import (SPEC, Fresh start). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var exported = 0
    private var picked = 0
    private var confirmed = 0
    private var dismissed = 0

    private fun show(
        status: StartingWeightsStatus = StartingWeightsStatus(count = 0, invalid = false),
        backup: BackupState = BackupState.Idle,
        onImport: () -> Unit = {},
    ) = compose.setContent {
        HyperlogTheme {
            SettingsScreen(
                gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                startingWeights = status, onImportStartingWeights = onImport,
                backup = backup,
                onExport = { exported++ },
                onRestore = { picked++ },
                onConfirmRestore = { confirmed++ },
                onDismissBackup = { dismissed++ },
            )
        }
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun checkForUpdatesNowAsksAndSaysWhatItFound() {
        var asked = 0
        var found by androidx.compose.runtime.mutableStateOf<com.hyperlog.app.update.UpdateViewModel.CheckedNow?>(null)
        compose.setContent {
            HyperlogTheme {
                SettingsScreen(
                    gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                    startingWeights = StartingWeightsStatus(count = 0, invalid = false), onImportStartingWeights = {},
                    onCheckNow = { asked++ }, checkedNow = found,
                )
            }
        }
        compose.onNodeWithText("Check for updates now").performScrollTo().performClick()
        assertEquals(1, asked)
        found = com.hyperlog.app.update.UpdateViewModel.CheckedNow.NoneNewer
        compose.onNodeWithText("No newer version, or no connection").assertExists()
    }

    @Test
    fun beforeAnImportItSaysWhereTheyComeFrom() {
        var picked = 0
        show { picked++ }
        compose.onNodeWithText("Starting weights from the old app").assertIsDisplayed()
        compose.onNodeWithText("From the old app's backup, no history").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun afterAnImportItCountsTheExercises() {
        show(StartingWeightsStatus(count = 42, invalid = false))
        compose.onNodeWithText("42 exercises · tap to import again").assertIsDisplayed()
    }

    @Test
    fun aFileThatIsntAnOldBackupSaysSo() {
        show(StartingWeightsStatus(count = 0, invalid = true))
        compose.onNodeWithText("That file isn't a backup from the old app").assertIsDisplayed()
    }

    @Test
    fun exportSavesEverythingToAFile() {
        show()
        compose.onNodeWithText("Everything on this phone, in one file").assertIsDisplayed()
        compose.onNodeWithText("Export backup").performClick()
        assertEquals(1, exported)
    }

    @Test
    fun anExportSaysWhenItWasSaved() {
        show(backup = BackupState.Exported(at("2026-10-13T14:05")))
        compose.onNodeWithText("Saved Tue 13 Oct, 14:05").assertIsDisplayed()
    }

    @Test
    fun restorePicksAFileAndAsksBeforeReplacingAnything() {
        show()
        compose.onNodeWithText("Replaces everything on this phone").assertIsDisplayed()
        compose.onNodeWithText("Restore from a backup").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun theConfirmationNamesTheBackupsDate() {
        show(backup = BackupState.Confirm(at("2026-10-12T19:30")))
        compose.onNodeWithText("Replace everything on this phone?").assertIsDisplayed()
        compose.onNodeWithText("The backup from Mon 12 Oct, 19:30", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }

    @Test
    fun confirmingRestores() {
        show(backup = BackupState.Confirm(at("2026-10-12T19:30")))
        compose.onNodeWithText("Restore").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun aRefusedFileSaysWhyAndThatNothingChanged() {
        var state by mutableStateOf<BackupState>(BackupState.Idle)
        compose.setContent {
            HyperlogTheme {
                SettingsScreen(
                    gymCount = 0, exerciseCount = 173, onGyms = {}, onLibrary = {}, onBack = {},
                    startingWeights = StartingWeightsStatus(0, false), onImportStartingWeights = {},
                    backup = state, onExport = {}, onRestore = {}, onConfirmRestore = {}, onDismissBackup = {},
                )
            }
        }
        mapOf(
            Backups.Reason.NotABackup to "That file isn't a Hyperlog backup · nothing changed",
            Backups.Reason.Newer to "Made by a newer Hyperlog · update the app first",
            Backups.Reason.Older to "Made by a Hyperlog too old to read · nothing changed",
            Backups.Reason.Damaged to "The backup is damaged · nothing changed",
        ).forEach { (reason, text) ->
            state = BackupState.Refused(reason)
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        state = BackupState.ExportFailed
        compose.onNodeWithText("Couldn't save the file").assertIsDisplayed()
    }

    @Test
    fun theFootnoteSaysAndroidKeepsACopyToo() {
        show()
        compose.onNodeWithText("Android also keeps a copy in your Google account", substring = true).assertIsDisplayed()
    }
}
