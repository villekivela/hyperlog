package com.hyperlog.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class MainActivityTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun theFirstRunOffersTheStarterProgramBesideAnAdHocSession() {
        waitFor("Today")
        compose.onNodeWithText("Starter program").assertIsDisplayed()
        compose.onNodeWithText("Hyperlog Full Body").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertIsDisplayed()
        compose.onNodeWithText("Import a program").assertIsDisplayed()
    }

    @Test
    fun runningTheStarterProgramPutsItsFirstDayOnToday() {
        waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        waitFor("Week 1 · Ramp-in")
        compose.onNodeWithText("Day A — Heavy bench, squat, pulldown").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("1 × 4–6 @ 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("5a").assertIsDisplayed()
    }

    @Test
    fun theProgramScreenOffersItTooWithNoProgram() {
        waitFor("Today")
        compose.onNodeWithContentDescription("Program").performClick()
        waitFor("Run this program")
        compose.onNodeWithText("Import program").assertIsDisplayed()
        compose.onNodeWithText("Run this program").performClick()
        waitFor("Ramp-in · weeks 1–2")
        compose.onNodeWithText("Hyperlog Full Body").assertIsDisplayed()
        compose.onNodeWithText("Run this program").assertDoesNotExist()
    }
}
