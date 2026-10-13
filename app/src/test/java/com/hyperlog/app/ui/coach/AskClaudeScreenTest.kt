package com.hyperlog.app.ui.coach

import com.hyperlog.app.openTab
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.waitFor
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

/** Ask Claude from the Coach screen: the share sheet with the prompt, and pasting the reply back (SPEC, Ask Claude). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class AskClaudeScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db get() = HyperlogDatabase.get(context)

    private fun openSomethingElse() {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        compose.openTab("Coach")
        compose.waitFor("What’s in the way?")
        compose.onNodeWithText("Something else").performScrollTo().performClick()
    }

    private fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Claude", text))
    }

    @Test
    fun askClaudeOpensTheShareSheetWithTheWholePrompt() {
        openSomethingElse()
        compose.onNodeWithText("Ask Claude").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("My left elbow aches on curls.")
        compose.onNodeWithText("Ask Claude").performClick()

        compose.waitUntil(5_000) { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val prompt = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(prompt.contains("> My left elbow aches on curls."))
        assertTrue(prompt.contains("## Program: Hyperlog Full Body"))
        assertTrue(prompt.contains("## Sessions of the last 4 weeks"))
        assertTrue(prompt.contains("## Exercise library"))
        assertTrue(prompt.contains("\"op\": \"repeat_week\""))
    }

    @Test
    fun theKeyboardDoesNotCoverTheWordsField() {
        // Found on the phone: the field stayed under the keyboard, so the words were typed blind.
        openSomethingElse()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        val root = compose.activity.window.decorView
        val keyboardPx = root.height / 2
        val keyboard = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(root, keyboard) }
        compose.waitForIdle()

        val bottom = field.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("field bottom $bottom under the keyboard at ${root.height - keyboardPx}", bottom <= root.height - keyboardPx)
    }

    @Test
    fun aPastedChangeOpensItsDiff() {
        openSomethingElse()
        copy(
            "Here it is:\n```json\n" +
                """{"summary": "Elbow: no drop set on curls", "scope": "rest_of_block", "operations": [{"op": "swap", "slot": "curl", "exercise": "machine_preacher_curl"}]}""" +
                "\n```",
        )
        compose.onNodeWithText("Paste change").performClick()

        compose.waitFor("Accept")
        compose.onNodeWithText("Elbow: no drop set on curls").assertIsDisplayed()
        compose.onNodeWithText("In place of Bayesian Cable Curl").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        assertEquals(1, runBlocking { db.planChanges().changes(db.programs().active()!!.id).size })
    }

    @Test
    fun anInvalidPasteSaysWhatsWrongAndChangesNothing() {
        openSomethingElse()
        copy("""{"summary": "Rows", "scope": "rest_of_program", "operations": [{"op": "remove", "slot": "rows"}, {"op": "adjust_load", "percent": 80}]}""")
        compose.onNodeWithText("Paste change").performClick()

        compose.waitFor("2 problems", substring = true)
        compose.onNodeWithText("unknown slot \"rows\"").assertIsDisplayed()
        compose.onNodeWithText("must be −50 to 50").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Accept")).fetchSemanticsNodes().isEmpty())
        assertEquals(0, runBlocking { db.planChanges().changes(db.programs().active()!!.id).size })

        // A reply with no JSON says so too.
        copy("Sounds good, rest up!")
        compose.onNodeWithText("Paste change").performClick()
        compose.waitFor("no Change found", substring = true)
    }
}
