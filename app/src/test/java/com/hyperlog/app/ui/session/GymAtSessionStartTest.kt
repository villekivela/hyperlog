package com.hyperlog.app.ui.session

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.await
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.press
import com.hyperlog.app.waitFor
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class GymAtSessionStartTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val gyms get() = Gyms(HyperlogDatabase.get(ApplicationProvider.getApplicationContext()))

    private fun waitForPad() = compose.await(hasContentDescription("Log set").or(hasContentDescription("Next")))

    private fun gymRow(name: String): SemanticsNodeInteraction = compose.onNode(isSelectable().and(hasText(name, substring = true)))

    private fun startAt(gym: String) {
        compose.waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor(gym)
        gymRow(gym).performClick()
        compose.onNodeWithText("Start").performClick()
        compose.waitFor("Add exercise")
    }

    private fun addExercise(name: String) {
        compose.onNode(hasSetTextAction()).performTextInput(name.lowercase())
        compose.waitFor(name)
        compose.onNodeWithText(name).performClick()
    }

    @Test
    fun startingASessionPreselectsTodaysUsualGymAndTheGymCanBeChanged() {
        val riverside = runBlocking {
            gyms.addGym("Central")
            gyms.addGym("Riverside").also { gyms.setWeekdays(it, setOf(LocalDate.now().dayOfWeek)) }
        }
        compose.waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Central")

        gymRow("Riverside").assertIsSelected()
        gymRow("Central").assertIsNotSelected()
        compose.onNode(hasText("Usual on ", substring = true)).assertIsDisplayed()

        gymRow("Central").performClick()
        gymRow("Central").assertIsSelected()
        compose.onNodeWithText("Start").performClick()
        compose.waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForPad()

        compose.onNodeWithText("Last exercise").performClick()
        compose.waitFor("Session")
        compose.onNodeWithText("Central").assertIsDisplayed()
        check(riverside > 0)
    }

    @Test
    fun aGymWithNoPlanOrHistoryIsPickedWithoutCallingItUsual() {
        runBlocking { gyms.addGym("Central"); gyms.addGym("Riverside") }
        compose.waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Central")
        gymRow("Central").assertIsSelected()
        assertEquals(0, compose.onAllNodes(hasText("Usual on ", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun theFirstLegExtensionAtAGymAsksForTheMachineAndItsLadderThenRemembersIt() {
        runBlocking { gyms.addGym("Central") }
        startAt("Central")
        addExercise("Leg Extension")

        compose.waitFor("Which machine?")
        compose.onNodeWithText("Central remembers it for next time.").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Log set").or(hasContentDescription("Next"))).fetchSemanticsNodes().let { check(it.isEmpty()) }
        compose.onNodeWithText("Add machine").performClick()

        compose.waitFor("New machine")
        // A machine is named for what it is; the name comes filled in.
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("Edit exactly").performClick()
        compose.onNodeWithText("Stack").performClick()
        compose.onNodeWithText("lb stack: 2.5, then 5 lb steps").performClick()
        compose.onNodeWithText("Save").performClick()
        waitForPad()
        // 10.2 kg is a rung of the lb stack, so no weight check stops the set.
        compose.press("1"); compose.press("0"); compose.press("Decimal point"); compose.press("2"); compose.press("Next"); compose.press("1"); compose.press("2"); compose.press("RIR 1"); compose.press("Log set")
        compose.await(hasText("12")) { it >= 2 }

        compose.press("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        compose.waitFor("Done")
        compose.onNodeWithText("Done").performClick()

        startAt("Central")
        addExercise("Leg Extension")
        waitForPad()
        compose.onNodeWithText("Leg Extension").performClick()
        compose.waitFor("Leg Extension · Central")
    }

    @Test
    fun aMachineIsPickedFromTheGymsEquipment() {
        runBlocking {
            val central = gyms.addGym("Central")
            gyms.saveItem(EquipmentItem(gymId = central, name = "Cable station", ladder = LadderSpec.lbStack()))
        }
        startAt("Central")
        addExercise("Cable Lateral Raise")
        compose.waitFor("Which machine?")

        compose.onNodeWithText("1.1–89.6 kg by 5 lb").assertIsDisplayed()
        compose.onNodeWithText("Cable station").performClick()
        waitForPad()
    }
}
