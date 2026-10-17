package com.hyperlog.app.ui.today

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToIndex
import com.hyperlog.app.coach.Workouts
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.WeekdayPlan
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Today holds its day until the finished sessions are read (#58: a cold start drew today untrained). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TodayLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val sat17 = LocalDate.of(2026, 10, 17)
    private fun at(hour: Int) = sat17.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Week 7's Day A, finished today. */
    private val dayA = PastSessionItem(
        1, at(17), at(18), program.plannedDay(Position(7, 0))!!.day.name, "Kalliokoski",
        // Long enough to scroll.
        (1..8).map { ExerciseSummary("Exercise $it", listOf(LoggedSet(92.5, 5, 0, 1))) },
        programId = 1, position = Position(7, 0),
    )

    @Test
    fun beforeTheSessionsAreReadTodayShowsNoPlanAndNoStart() {
        val source = MutableStateFlow<List<PastSessionItem>?>(null)
        compose.setContent {
            val sessions by source.collectAsState()
            HyperlogTheme {
                TodayScreen(
                    sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                    program = ActiveProgram(1, program, Position(7, 1), today = sat17),
                    library = library.exercises, weekdayPlan = WeekdayPlan.None, sessions = sessions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasTestTag(HeroCardTag)).assertDoesNotExist()
        compose.onNodeWithText("Start session").assertDoesNotExist()
        source.value = listOf(dayA)
        compose.waitForIdle()
        compose.onNodeWithText("Done · Sat 17 Oct · Kalliokoski").assertExists()
        compose.onNodeWithText("Start session").assertDoesNotExist()
    }

    private val hotel = (ProgramParser.workout(
        """
        { "workout": { "name": "Hotel dumbbells, 45 min", "summary": "Upper body with dumbbells only.",
          "groups": [ { "slots": [ { "id": "press", "exercise": "db_bench_press", "sets": [ { "count": 3, "reps": "8-10", "rir": 2, "rest": 120 } ] } ] } ] } }
        """,
        library.exercises.map { it.id }.toSet(),
    ) as ProgramParser.WorkoutResult.Valid).workout

    @Test
    fun beforeTheSessionsAreReadNoStartFloatsOverTheBlankDay() {
        val source = MutableStateFlow<List<PastSessionItem>?>(null)
        compose.setContent {
            val sessions by source.collectAsState()
            HyperlogTheme {
                TodayScreen(
                    sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                    program = ActiveProgram(1, program, Position(7, 1), today = sat17),
                    library = library.exercises, weekdayPlan = WeekdayPlan.None, sessions = sessions,
                    workout = Workouts.Waiting(7, hotel, sat17, gymId = null),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Start workout").assertDoesNotExist()
        source.value = emptyList()
        compose.waitForIdle()
        compose.onNodeWithText("Start workout").assertExists()
    }

    @Test
    fun aDayThatTurnsFromPlannedToDoneOpensAtItsTop() {
        val source = MutableStateFlow<List<PastSessionItem>?>(emptyList())
        compose.setContent {
            val sessions by source.collectAsState()
            HyperlogTheme {
                TodayScreen(
                    sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                    program = ActiveProgram(1, program, Position(7, 0), today = sat17),
                    library = library.exercises, weekdayPlan = WeekdayPlan.None, sessions = sessions,
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasTestTag(TodayDayListTag)).performScrollToIndex(5)
        compose.waitForIdle()
        // Today's Day A, finished: the done day starts at its card, not at the plan's scroll.
        source.value = listOf(dayA)
        compose.waitForIdle()
        compose.onNodeWithText("Done · Sat 17 Oct · Kalliokoski").assertIsDisplayed()
    }
}
