package com.hyperlog.app.ui.session

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForPad() = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription("Log set").or(hasContentDescription("Next"))).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gymRow(name: String): SemanticsNodeInteraction = compose.onNode(isSelectable().and(hasText(name, substring = true)))

    // The pad follows the logger's saved state, which lands off the main thread: wait for the key to show.
    private fun key(description: String) = compose.also { c ->
        c.waitUntil(5_000) { c.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
    }.onNodeWithContentDescription(description).performClick().also { compose.waitForIdle() }

    private fun startAt(gym: String) {
        waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        waitFor(gym)
        gymRow(gym).performClick()
        compose.onNodeWithText("Start").performClick()
        waitFor("Add exercise")
    }

    private fun addExercise(name: String) {
        compose.onNode(hasSetTextAction()).performTextInput(name.lowercase())
        waitFor(name)
        compose.onNodeWithText(name).performClick()
    }

    @Test
    fun startingASessionPreselectsTodaysUsualGymAndTheGymCanBeChanged() {
        val toolo = runBlocking {
            gyms.addGym("Kallio")
            gyms.addGym("Töölö").also { gyms.setWeekdays(it, setOf(LocalDate.now().dayOfWeek)) }
        }
        waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        waitFor("Kallio")

        gymRow("Töölö").assertIsSelected()
        gymRow("Kallio").assertIsNotSelected()
        compose.onNode(hasText("Usual on ", substring = true)).assertIsDisplayed()

        gymRow("Kallio").performClick()
        gymRow("Kallio").assertIsSelected()
        compose.onNodeWithText("Start").performClick()
        waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForPad()

        compose.onNodeWithText("Last exercise").performClick()
        waitFor("Session")
        compose.onNodeWithText("Kallio").assertIsDisplayed()
        check(toolo > 0)
    }

    @Test
    fun theFirstLegExtensionAtAGymAsksForTheMachineAndItsLadderThenRemembersIt() {
        runBlocking { gyms.addGym("Kallio") }
        startAt("Kallio")
        addExercise("Leg Extension")

        waitFor("Which machine?")
        compose.onNodeWithText("Kallio remembers it for next time.").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Log set").or(hasContentDescription("Next"))).fetchSemanticsNodes().let { check(it.isEmpty()) }
        compose.onNodeWithText("Add machine").performClick()

        waitFor("New machine")
        // A machine is named for what it is; the name comes filled in.
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("lb stack: 2.5, then 5 lb steps").performClick()
        compose.onNodeWithText("Save").performClick()
        waitForPad()
        // 10.2 kg is a rung of the lb stack, so no weight check stops the set.
        key("1"); key("0"); key("Decimal point"); key("2"); key("Next"); key("1"); key("2"); key("RIR 1"); key("Log set")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("12")).fetchSemanticsNodes().size >= 2 }

        key("More")
        compose.onNodeWithText("Finish session").performClick()
        compose.onNodeWithText("Finish").performClick()
        waitFor("Done")
        compose.onNodeWithText("Done").performClick()

        startAt("Kallio")
        addExercise("Leg Extension")
        waitForPad()
        compose.onNodeWithText("Leg Extension").performClick()
        waitFor("Leg Extension · Kallio")
    }

    @Test
    fun aMachineIsPickedFromTheGymsEquipment() {
        runBlocking {
            val kallio = gyms.addGym("Kallio")
            gyms.saveItem(EquipmentItem(gymId = kallio, name = "Cable station", ladder = LadderSpec.lbStack()))
        }
        startAt("Kallio")
        addExercise("Cable Lateral Raise")
        waitFor("Which machine?")

        compose.onNodeWithText("1.1 · 3.4 · 5.7 · 7.9 … 89.6 kg").assertIsDisplayed()
        compose.onNodeWithText("Cable station").performClick()
        waitForPad()
    }
}
