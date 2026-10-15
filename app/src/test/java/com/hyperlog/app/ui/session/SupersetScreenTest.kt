package com.hyperlog.app.ui.session

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.press
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A superset alternates on screen, with the partner named above the exercise (SPEC, Supersets). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SupersetScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    /** A pad key: the last node with [description], below anything on the strip that shares it. */
    private fun startDay() {
        compose.waitFor("Today")
        val sets = """[ { "count": 2, "reps": "8-10", "rir": 1 } ]"""
        runBlocking {
            ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(
                """
                { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 2, "days": [
                  { "id": "a", "name": "Day A", "groups": [
                    { "type": "superset", "slots": [
                      { "id": "curl", "exercise": "db_bicep_curl", "sets": $sets },
                      { "id": "french", "exercise": "db_french_press", "sets": $sets } ] },
                    { "slots": [ { "id": "lateral", "exercise": "db_lateral_raise", "sets": $sets } ] } ] } ] } ] }
                """,
            )
        }
        compose.waitFor("Day A")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
    }

    @Test
    fun theSupersetChipNamesThePartner() {
        startDay()
        compose.onNodeWithText("1a + DB French Press").assertIsDisplayed()
        // The Next line skips the partner: it names what follows the superset.
        compose.onNodeWithText("DB Lateral Raise").assertIsDisplayed()

        compose.press("1"); compose.press("5"); compose.press("Next"); compose.press("8"); compose.press("Log set")
        compose.waitFor("1b + DB Curl")
        compose.onNodeWithText("DB French Press").assertIsDisplayed()
    }

    @Test
    fun theSupersetChipBringsThePartnerUp() {
        startDay()
        compose.onNodeWithText("1a + DB French Press").performClick()
        compose.waitFor("1b + DB Curl")
        compose.onNodeWithText("DB French Press").assertIsDisplayed()
    }

    @Test
    fun theSessionListTagsSupersetPartners() {
        startDay()
        compose.onNodeWithText("DB Lateral Raise").performClick()
        compose.waitFor("Session")
        compose.onNodeWithText("1a").assertIsDisplayed()
        compose.onNodeWithText("1b").assertIsDisplayed()
    }
}
