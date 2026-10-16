package com.hyperlog.app.ui.coach

import com.hyperlog.app.openTab
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app starting with a session already running, as after a process restart or a restore: the
 * coach still knows the last finished session. Found on the phone: Time off read "No sessions yet";
 * now the prompt's days off would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CoachAfterRestartTest {
    private val now = System.currentTimeMillis()

    /** A session finished 3 days ago and one running now, written before the app starts. */
    private val sessions = object : ExternalResource() {
        override fun before() = runBlocking {
            val db = HyperlogDatabase.get(ApplicationProvider.getApplicationContext())
            db.sessions().insertSession(SessionEntity(startedAt = now - 3 * DAY, finishedAt = now - 3 * DAY + 3_600_000))
            db.sessions().insertSession(SessionEntity(startedAt = now - 600_000))
            Unit
        }
    }

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(sessions).around(compose)

    @Test
    fun timeOffCountsFromTheLastFinishedSessionWhileOneRuns() {
        compose.waitFor("Add exercise")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Leave").performClick()
        compose.waitFor("Resume session")

        compose.openTab("Coach")
        compose.waitFor("In your words")
        compose.onNode(hasSetTextAction()).performTextInput("Tired.")
        compose.onNodeWithText("Ask Claude").performClick()
        // The session running may have asked for something first (notifications): find the share sheet.
        var chooser: Intent? = null
        compose.waitUntil(5_000) {
            generateSequence { shadowOf(compose.activity).nextStartedActivity }.firstOrNull { it.action == Intent.ACTION_CHOOSER }?.also { chooser = it } != null
        }
        @Suppress("DEPRECATION")
        val prompt = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(prompt, prompt.contains(", 3 days ago."))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
