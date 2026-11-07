package com.hyperlog.app.ui.session

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.press
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForTappable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Session done through the app, with its read-back landing (round 4). The read-back rebuilt the
 * summary and threw away what finishing worked out, under the reader's eyes; every test that drew
 * the screen on its own, or stopped at finish(), missed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SessionDoneReadBackTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    @Test
    fun theReadBackKeepsWhatFinishingWorkedOut() {
        compose.waitFor("Today")
        val result = runBlocking { ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(TestPrograms.twoDays("Test")) }
        assertEquals(ProgramParser.Result.Valid::class, result::class)
        compose.waitFor("Day A")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
        compose.press("8"); compose.press("0"); compose.press("Next"); compose.press("6"); compose.press("Log set")
        compose.waitFor("70")
        compose.press("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()

        // A row opens only once the read-back is in.
        compose.waitForTappable("Barbell Bench Press")

        val sets = compose.onAllNodes(hasText(" sets", substring = true)).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("1 of 4 sets"), sets.filter { " of " in it })
    }
}
