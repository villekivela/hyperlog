package com.hyperlog.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.coach.CoachLine
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The coach line on Today (SPEC, Coaching → The coach line). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TodayCoachLineTest {
    @get:Rule
    val compose = createComposeRule()

    private val ids = setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")
    private val program = (ProgramParser.parse(TestPrograms.twoDays("Test"), ids) as ProgramParser.Result.Valid).program
    private val monday = LocalDate.of(2026, 10, 26)
    private val names = mapOf("barbell_bench_press" to "Bench press", "leg_extension" to "Leg extension", "cable_lateral_raise" to "Cable lateral raise")

    private fun show(line: CoachLine?, selected: LocalDate? = null, onCheckIn: (String, CoachLine) -> Unit = { _, _ -> }, onHide: (CoachLine) -> Unit = {}) = compose.setContent {
        HyperlogTheme {
            TodayScreen(
                sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                program = ActiveProgram(1, program, Position(1, 0), today = monday),
                exerciseNames = names,
                coachLine = line, onCheckInLine = onCheckIn, onHideCoachLine = onHide,
                selectedDay = selected?.toEpochDay(), onSelectDay = {},
            )
        }
    }

    @Test
    fun checkInStartsACheckInWithTheLineAsItsFirstAnswer() {
        var answer: String? = null
        var handed: CoachLine? = null
        show(CoachLine.Stall("barbell_bench_press", sessions = 3, last = monday), onCheckIn = { words, line -> answer = words; handed = line })
        compose.onNodeWithText("Bench press hasn't moved in 3 sessions").assertIsDisplayed()
        compose.onNodeWithText("Check in").performClick()
        assertEquals("Bench press hasn't moved in 3 sessions.", answer)
        assertEquals(CoachLine.Stall("barbell_bench_press", sessions = 3, last = monday), handed)
    }

    @Test
    fun eachPatternReadsAsItsLine() {
        var line by androidx.compose.runtime.mutableStateOf<CoachLine?>(CoachLine.DayMissed("a", "Day A", missed = 3, of = 4, last = monday))
        compose.setContent {
            HyperlogTheme {
                TodayScreen(
                    sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                    program = ActiveProgram(1, program, Position(1, 0), today = monday),
                    exerciseNames = names, coachLine = line,
                )
            }
        }
        compose.onNodeWithText("Day A missed 3 of its last 4 turns").assertIsDisplayed()
        line = CoachLine.DayMissed("a", "Day A", missed = 4, of = 4, last = monday)
        compose.onNodeWithText("Day A missed its last 4 turns").assertIsDisplayed()
        line = CoachLine.Grinding("leg_extension", sessions = 3, last = monday)
        compose.onNodeWithText("Leg extension hit 0 RIR in 3 sessions running").assertIsDisplayed()
        line = CoachLine.SameSwap("s1", "barbell_bench_press", "smith_machine_bench_press", sessions = 3, last = monday)
        compose.onNodeWithText("Bench press swapped 3 times running").assertIsDisplayed()
    }

    @Test
    fun theCrossHidesTheLine() {
        var hidden: CoachLine? = null
        val line = CoachLine.Grinding("leg_extension", sessions = 3, last = monday)
        show(line, onHide = { hidden = it })
        compose.onNodeWithContentDescription("Hide this line").performClick()
        assertEquals(line, hidden)
    }

    @Test
    fun theLineShowsOnlyWhileTodayIsSelected() {
        show(CoachLine.Grinding("leg_extension", sessions = 3, last = monday), selected = monday.plusDays(2))
        compose.onNodeWithText("Leg extension hit 0 RIR in 3 sessions running").assertDoesNotExist()
    }
}
