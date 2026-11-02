package com.hyperlog.app.ui.settings

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.click
import com.hyperlog.app.coach.TrainingStyles
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.openTab
import com.hyperlog.app.waitFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The training style's wizard with no key and no scripted coach: local, and its Done goes on into Ask Claude. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TrainingStyleKeylessTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    @Test
    fun newProgramWithNoKeyOpensTheWizardFirstThenAskClaude() {
        compose.openTab("Coach")
        compose.click("New program")
        compose.waitFor("What are you training for?")
        repeat(9) { compose.onNodeWithText("Next").performClick(); compose.waitForIdle() }
        compose.waitFor("This is how you train")
        compose.click("Done")
        compose.waitFor("In your words")
        assertTrue(runBlocking { TrainingStyles(HyperlogDatabase.get(context), context).done.first() })
    }
}
