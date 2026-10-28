package com.hyperlog.app.ui.program

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.DueReview
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Where a block review opens (SPEC, Program → Block review): Review on a finished block's card, and Today's line. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class BlockReviewEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private val ids = setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")
    private val main = (ProgramParser.parse(TestPrograms.twoDays("Test"), ids) as ProgramParser.Result.Valid).program
    private val program = main.copy(blocks = main.blocks + main.blocks[0].copy(id = "peak", name = "Peak", weekStart = 3, weekEnd = 4))
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun aFinishedBlocksCardHasReview() {
        var opened: String? = null
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = ActiveProgram(1, program, Position(3, 0), today = monday), errors = null,
                    onImport = {}, onCloseErrors = {}, onBack = {}, onReview = { opened = it },
                )
            }
        }
        // Peak is under way: no review yet.
        compose.onNodeWithText("Review").assertDoesNotExist()
        compose.onNodeWithText("Main").performClick()
        compose.onNodeWithText("Review").performClick()
        assertEquals("main", opened)
    }

    private fun today(due: DueReview?, selected: LocalDate? = null, onOpen: (DueReview) -> Unit = {}, position: Position = Position(3, 0)) = compose.setContent {
        HyperlogTheme {
            TodayScreen(
                sessionRunning = false, onResume = {}, onStart = { _, _, _ -> },
                program = ActiveProgram(1, program, position, today = monday),
                dueReview = due, onOpenReview = onOpen,
                selectedDay = selected?.toEpochDay(), onSelectDay = {},
            )
        }
    }

    @Test
    fun todaySaysAFinishedBlockIsDoneAndOpensItsReview() {
        var opened: DueReview? = null
        val due = DueReview(1, "main", "Main", programDone = false)
        today(due, onOpen = { opened = it })
        compose.onNodeWithText("Main is done · See the review").assertIsDisplayed().performClick()
        assertEquals(due, opened)
    }

    @Test
    fun aFinishedProgramIsDone() {
        today(DueReview(1, "peak", "Peak", programDone = true), position = Position(5, 0))
        compose.onNodeWithText("Test is done · See the review").assertIsDisplayed()
    }

    @Test
    fun theLineIsOnlyOnToday() {
        today(DueReview(1, "main", "Main", programDone = false), selected = monday.plusDays(2))
        compose.onNodeWithText("Main is done · See the review").assertDoesNotExist()
    }

    @Test
    fun aPastProgramsSheetOpensItsProgramReview() {
        var opened: Long? = null
        val past = com.hyperlog.app.program.ListedProgram(7, "Test", program, "{}", com.hyperlog.app.program.ListedProgram.Status.Past(0L, 1L, Position(3, 0)))
        compose.setContent {
            HyperlogTheme {
                ProgramsPage(listOf(past), null, onRunAgain = {}, onResume = {}, onOpenSaved = {}, onExport = {}, onBack = {}, onProgramReview = { opened = it })
            }
        }
        compose.onNodeWithText("Test").performClick()
        compose.onNodeWithText("Program review").performClick()
        assertEquals(7L, opened)
    }
}
