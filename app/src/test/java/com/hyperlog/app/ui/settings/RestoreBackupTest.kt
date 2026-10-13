package com.hyperlog.app.ui.settings

import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.session.SessionRuntime
import com.hyperlog.app.waitFor
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Export writes the file; on a fresh install, Settings → Restore from a backup asks, restores, and starts the app again on the backup. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class RestoreBackupTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    @Test
    fun restoringABackupOnAFreshInstallBringsEverythingBack() {
        val file = File(compose.activity.cacheDir, "hyperlog-backup.json")
        file.writeText(javaClass.getResource("/backup/hyperlog-backup-db14.json")!!.readText())
        compose.waitFor("Today")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitFor("Restore from a backup")

        // The system file picker hands back this file.
        ViewModelProvider(compose.activity)[BackupViewModel::class.java].pick(Uri.fromFile(file))
        compose.waitFor("Replace everything on this phone?")
        compose.onNodeWithText("Restore").performClick()

        compose.waitUntil(5_000) { compose.activity.isFinishing }
        // The restored session asks for notifications as it shows, so the restart may not be the first intent.
        val started = generateSequence { shadowOf(compose.activity).nextStartedActivity }.map { it.component?.className }.toList()
        assertTrue(started.toString(), MainActivity::class.java.name in started)
        val db = HyperlogDatabase.get(compose.activity)
        assertEquals(listOf("Central"), runBlocking { Gyms(db).gyms.first() }.map { it.name })
        val session = SessionRuntime.get(compose.activity).logger.state.value
        assertTrue(session != null)
        assertEquals("82.5", session!!.entry?.weight)
    }

    @Test
    fun exportWritesTheBackupToThePickedFile() {
        val file = File(compose.activity.cacheDir, "export.json")
        compose.waitFor("Today")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitFor("Export backup")

        // The system save dialog hands back this file.
        ViewModelProvider(compose.activity)[BackupViewModel::class.java].export(Uri.fromFile(file))
        compose.waitFor("Saved ", substring = true)
        assertTrue(file.readText().startsWith("{\"format\":\"hyperlog-backup\""))
    }
}
