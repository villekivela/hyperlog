package com.hyperlog.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.waitFor
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class MainActivityTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    @Test
    fun theFirstRunOffersTheStarterProgramBesideAnAdHocSession() {
        compose.waitFor("Today")
        compose.onNodeWithText("Starter program").assertIsDisplayed()
        compose.onNodeWithText("Hyperlog Full Body").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertIsDisplayed()
        compose.onNodeWithText("Import a program").assertIsDisplayed()
    }

    @Test
    fun runningTheStarterProgramPutsItsFirstDayOnToday() {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        compose.onNodeWithText("Day A — Heavy bench, squat, pulldown").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("1 × 4–6 @ 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("5a").assertIsDisplayed()
    }

    @Test
    fun theProgramScreenOffersItTooWithNoProgram() {
        compose.waitFor("Today")
        compose.openTab("Program")
        compose.waitFor("Run this program")
        compose.onNodeWithText("Import program").assertIsDisplayed()
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Ramp-in · weeks 1–2")
        compose.onNodeWithText("Hyperlog Full Body").assertIsDisplayed()
        compose.onNodeWithText("Run this program").assertDoesNotExist()
    }

    @Test
    fun theNavigationBarGoesBetweenTodayProgramCoachAndHistory() {
        compose.waitFor("Today")
        compose.onNode(navItem("Program")).performClick()
        compose.waitFor("Import program")
        compose.onNode(navItem("Coach")).performClick()
        compose.waitFor("What’s in the way?")
        compose.onNode(navItem("History")).performClick()
        compose.waitFor("Sessions")
        compose.onNode(navItem("Today")).performClick()
        compose.waitFor("Run this program")
    }

    @Test
    fun theNavigationBarHidesWhileTheSessionIsOnScreen() {
        compose.waitFor("Start session")
        compose.onNode(hasTestTag(NavigationBarTag)).assertIsDisplayed()
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Add exercise")
        compose.onNode(hasTestTag(NavigationBarTag)).assertDoesNotExist()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Leave").performClick()
        compose.waitFor("Resume session")
        compose.onNode(hasTestTag(NavigationBarTag)).assertIsDisplayed()
    }

    @Test
    fun settingsOpensFromEveryDestinationsTopBarAndGoesBackThere() {
        compose.waitFor("Today")
        listOf("Program", "Coach", "History").forEach { tab ->
            compose.onNode(navItem(tab)).performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.waitFor("Exercise library")
            compose.onNode(hasTestTag(NavigationBarTag)).assertDoesNotExist()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.await(navItem(tab).and(isSelected()))
        }
    }

    @Test
    fun backFromAnotherDestinationGoesToToday() {
        compose.waitFor("Today")
        compose.onNode(navItem("History")).performClick()
        compose.waitFor("Sessions")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.await(navItem("Today").and(isSelected()))
    }

}
