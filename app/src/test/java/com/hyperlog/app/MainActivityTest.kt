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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToNode
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
        compose.onNode(navItem("Settings")).assertIsDisplayed()
        compose.onNodeWithText("Start session").assertIsDisplayed()
        compose.onNodeWithText("Import a program").assertIsDisplayed()
    }

    @Test
    fun runningTheStarterProgramPutsItsFirstDayOnToday() {
        compose.waitFor("Run this program")
        compose.onNodeWithText("Run this program").performClick()
        compose.waitFor("Week 1 · Ramp-in")
        // The day's letter is in its cookie, its name after the letter beside it.
        compose.onNodeWithText("Heavy bench, squat, pulldown").assertIsDisplayed()
        compose.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
        compose.onNodeWithText("1 × 4–6\u00A02 RIR", substring = true).assertIsDisplayed()
        // The superset is further down, under the day's card.
        compose.onNode(hasScrollToIndexAction().and(hasTestTag(com.hyperlog.app.ui.today.CalendarTag).not())).performScrollToNode(hasText("5a"))
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
        compose.waitFor("In your words")
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
    fun settingsIsTheFifthTabAndHasNoBackArrow() {
        compose.waitFor("Today")
        compose.openTab("Settings")
        compose.waitFor("Exercise library")
        compose.onNode(navItem("Settings").and(isSelected())).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun noDestinationsTopBarHasAGear() {
        compose.waitFor("Today")
        listOf("Today", "Program", "Coach", "History").forEach { tab ->
            compose.openTab(tab)
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        }
    }

    @Test
    fun backFromSettingsGoesToToday() {
        compose.waitFor("Today")
        compose.openTab("Program")
        compose.openTab("Settings")
        compose.waitFor("Exercise library")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.await(navItem("Today").and(isSelected()))
    }

    @Test
    fun settingsSubScreensHideTheBarAndGoBackToSettings() {
        compose.waitFor("Today")
        compose.openTab("Settings")
        compose.click("Exercise library")
        compose.waitFor("Search 213 exercises")
        compose.onNode(hasTestTag(NavigationBarTag)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.await(navItem("Settings").and(isSelected()))
        compose.onNode(hasTestTag(NavigationBarTag)).assertIsDisplayed()
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
