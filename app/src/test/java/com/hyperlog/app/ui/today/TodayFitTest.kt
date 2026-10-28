package com.hyperlog.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.WeekdayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate

/** Today's planned days against their training days: the time and swaps lines (SPEC, Training days and the rotation → The fit). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TodayFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val ids = setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")
    private val program = (ProgramParser.parse(TestPrograms.twoDays("Test"), ids) as ProgramParser.Result.Valid).program
    private val monday = LocalDate.of(2026, 10, 26)
    private val harbour = Gym(1, "Harbour", emptyList(), missing = ids)

    private fun show(plan: WeekdayPlan, onCheckIn: (com.hyperlog.app.week.FitAsk) -> Unit = {}) = compose.setContent {
        val rotation = Rotations.of(program, RotationAnchor(LocalDate.of(2026, 10, 26), week = 1, day = 0), plan.days, emptyList(), monday)
        HyperlogTheme {
            TodayScreen(
                sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                program = ActiveProgram(1, program, rotation.next, today = monday, rotation = rotation),
                weekdayPlan = plan, gyms = listOf(harbour), onCheckInFit = onCheckIn,
            )
        }
    }

    @Test
    fun aDayOverItsTrainingDaysMinutesShowsAnAmberLineThatStartsACheckIn() {
        var ask: com.hyperlog.app.week.FitAsk? = null
        show(WeekdayPlan(gyms = mapOf(MONDAY to null, THURSDAY to null, SATURDAY to null), minutes = mapOf(MONDAY to 5)), onCheckIn = { ask = it })
        compose.onNodeWithText("· Monday has 5", substring = true).assertIsDisplayed().performClick()
        val answer = ask!!.words
        assertTrue(answer, Regex("Day A runs ~\\d+ minutes but Monday has 5\\.").matches(answer))
        // It hands on the day it was on: its last slot, on its planned date.
        assertEquals("lateral", ask!!.slot)
        // The day to train today has no date of its own.
        assertNull(ask!!.date)
    }

    @Test
    fun aDayCountsTheSwapsItsTrainingDaysGymNeeds() {
        show(WeekdayPlan(gyms = mapOf(MONDAY to 1L, THURSDAY to null, SATURDAY to null)))
        compose.onNodeWithText("2 swaps at Harbour", substring = true).assertIsDisplayed()
    }
}
