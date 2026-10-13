package com.hyperlog.app.ui.history

import com.hyperlog.app.await
import com.hyperlog.app.navItem
import com.hyperlog.app.openTab
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForDescription
import com.hyperlog.app.waitForTappable
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** History on screen (SPEC, Screens → History; docs/design/history.md). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class HistoryScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val db get() = HyperlogDatabase.get(ApplicationProvider.getApplicationContext<Context>())

    private fun at(date: LocalDate) = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** A finished ad-hoc session on [date] with one exercise's sets (kg to reps, at 1 RIR). */
    private fun session(date: LocalDate, exerciseId: String, vararg sets: Pair<Double, Int>, itemId: Long? = null) = runBlocking {
        val dao = db.sessions()
        val id = dao.insertSession(SessionEntity(startedAt = at(date), finishedAt = at(date) + 3_600_000))
        val se = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = exerciseId, position = 0, equipmentItemId = itemId))
        sets.forEachIndexed { i, (kg, reps) ->
            dao.insertSet(LoggedSetEntity(sessionExerciseId = se, position = i, weightKg = kg, fullReps = reps, partialReps = 0, rir = 1, loggedAt = at(date)))
        }
    }

    private fun openHistory() {
        compose.await(navItem("History"))
        compose.openTab("History")
    }

    @Test
    fun theSessionListOpensAnyPastSession() {
        compose.waitFor("Today")
        session(LocalDate.of(2026, 9, 28), "barbell_bench_press", 80.0 to 6)
        session(LocalDate.of(2026, 10, 1), "leg_extension", 50.0 to 12)

        openHistory()
        // The older one, second in the list.
        compose.waitForTappable("Mon 28 Sep")
        compose.onNodeWithText("Mon 28 Sep").performClick()
        // Its summary, then an exercise's table to correct.
        compose.waitFor("80 × 6 @ 1")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        compose.waitForDescription("Correct set 1")
    }

    @Test
    fun aMachineExercisesTrendShowsOneChartPerMachine() {
        compose.waitFor("Today")
        val (left, tall) = runBlocking {
            val gyms = Gyms(db)
            val kamppi = gyms.addGym("Kamppi")
            gyms.addItem(EquipmentItem(gymId = kamppi, name = "Cable left", ladder = LadderSpec.lbStack())) to
                gyms.addItem(EquipmentItem(gymId = kamppi, name = "Tall cable", ladder = LadderSpec.lbStack()))
        }
        session(LocalDate.of(2026, 9, 28), "cable_lateral_raise", 5.7 to 12, itemId = left)
        session(LocalDate.of(2026, 10, 1), "cable_lateral_raise", 10.2 to 10, itemId = tall)

        openHistory()
        compose.waitFor("Progress")
        compose.onNodeWithText("Progress").performClick()
        compose.waitForTappable("Cable Lateral Raise")
        compose.onNodeWithText("Cable Lateral Raise").performClick()

        compose.waitFor("Tall cable · Kamppi")
        compose.onNodeWithText("Cable left · Kamppi").assertIsDisplayed()
        compose.onNodeWithText("10.2 × 10 @ 1").assertIsDisplayed()
    }

    @Test
    fun bodyweightIsLoggedForToday() {
        openHistory()
        compose.waitFor("Progress")
        compose.onNodeWithText("Progress").performClick()
        compose.waitForDescription("Log bodyweight")
        compose.onNodeWithContentDescription("Log bodyweight").performClick()
        compose.waitFor("Save")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("82.4")
        compose.onNodeWithText("Save").performClick()

        compose.waitFor("82.4 kg")
        val logged = runBlocking { Bodyweights(db).entries.first() }
        assertEquals(LocalDate.now(), logged.single().date)
        assertEquals(82.4, logged.single().kg, 0.001)
    }
}
