package com.hyperlog.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.SessionStart
import com.hyperlog.app.week.WeekdayPlan
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

/** Today with the program's days taking turns on training days (SPEC, Today; Training days and the rotation). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TodayRotationTest {
    @get:Rule
    val compose = createComposeRule()

    private val program = (ProgramParser.parse(TestPrograms.twoDays("Test"), setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")) as ProgramParser.Result.Valid).program
    private val days = setOf(MONDAY, THURSDAY, SATURDAY)

    /** The program rotating from Monday 26 Oct, seen on [today]. */
    private fun at(today: LocalDate, sessions: List<SessionStart> = emptyList()): ActiveProgram {
        val rotation = Rotations.of(program, RotationAnchor(LocalDate.of(2026, 10, 26), week = 1, day = 0), days, sessions, today)
        return ActiveProgram(1, program, rotation.next, today = today, rotation = rotation)
    }

    private fun show(program: ActiveProgram, onTrainMissed: () -> Unit = {}) = compose.setContent {
        HyperlogTheme {
            TodayScreen(
                sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                program = program, weekdayPlan = WeekdayPlan(days.associateWith { null }),
                onTrainMissed = { _, _ -> onTrainMissed() },
            )
        }
    }

    @Test
    fun onADayWithNoTrainingDayTodayShowsTheNextDayWithoutStartAndOffersTheMissedOne() {
        var trained = false
        show(at(LocalDate.of(2026, 10, 27)), onTrainMissed = { trained = true })
        compose.onNodeWithText("Next · Thu 29 Oct · Week 1").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertDoesNotExist()
        // Monday's day is missed: struck through on the calendar, and on offer today.
        compose.onNode(hasContentDescription("Mon 26 Oct, Day A, missed")).assertExists()
        compose.onNodeWithText("Monday’s Day A is missed · Train it today").performClick()
        assertTrue(trained)
    }

    @Test
    fun startIsOnlyForADayPlannedOnToday() {
        show(at(LocalDate.of(2026, 10, 29)))
        compose.onNodeWithText("Week 1 · Main").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertIsDisplayed()
        compose.onNodeWithText("Train it today", substring = true).assertDoesNotExist()
    }

    @Test
    fun aMissedDayPickedShowsItsPlanWithNoStart() {
        show(at(LocalDate.of(2026, 10, 27)))
        compose.onNode(hasContentDescription("Mon 26 Oct, Day A, missed")).performClick()
        compose.onNodeWithText("Missed · Mon 26 Oct · Week 1").assertIsDisplayed()
        compose.onNodeWithText("Start session").assertDoesNotExist()
    }
}
