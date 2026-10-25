package com.hyperlog.app.ui.program

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.click
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ListedProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.waitFor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Program → ⋮ → Programs (SPEC, Program → Programs): every program held; a saved one opens its page, another's sheet has Run again, Resume and Export. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val starterSource = File("src/main/assets/${StarterProgram.ASSET}").readText()
    private val starter = (ProgramParser.parse(starterSource, ids, library.merged) as ProgramParser.Result.Valid).program
    private val twoDays = (ProgramParser.parse(TestPrograms.twoDays("Saved one"), ids) as ProgramParser.Result.Valid).program

    private fun at(date: String) = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val running = ListedProgram(3, starter.title, starter, starterSource, ListedProgram.Status.Running(Position(6, 0), finished = false))
    private val saved = ListedProgram(4, "Saved one", twoDays, TestPrograms.twoDays("Saved one"), ListedProgram.Status.Saved(at("2026-10-24")))
    private val past = ListedProgram(
        1, starter.title, starter, starterSource, ListedProgram.Status.Past(at("2026-09-01"), at("2026-10-13")),
    )

    private val calls = mutableListOf<String>()

    @Before
    fun zone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Helsinki"))

    private fun show(kept: List<ListedProgram>, active: ActiveProgram? = ActiveProgram(3, starter, Position(6, 0))) = compose.setContent {
        HyperlogTheme {
            ProgramsPage(
                kept, active,
                onRunAgain = { calls += "again $it" },
                onResume = { calls += "resume $it" },
                onOpenSaved = { calls += "open $it" },
                onExport = { calls += "export ${it.id}" },
                onBack = { calls += "back" },
            )
        }
    }

    @Test
    fun itListsTheRunningSavedAndPastProgramsWithTheirBlocksWeeksAndDates() {
        show(listOf(saved, running, past))
        compose.waitFor("Running")
        compose.onNodeWithText("Saved").assertExists()
        compose.onNodeWithText("Past").assertExists()
        val size = "${starter.blocks.size} blocks · ${starter.weeks} weeks"
        compose.onNodeWithText("$size · Week 6").assertExists()
        compose.onNodeWithText("1 block · 2 weeks · Saved 24 Oct").assertExists()
        compose.onNodeWithText("$size · 1 Sep – 13 Oct").assertExists()
    }

    @Test
    fun runAgainAsksAndStartsAPastProgramAgain() {
        show(listOf(running, past))
        compose.onAllNodesWithText(starter.title)[1].performClick()
        compose.waitFor("Ran 1 Sep – 13 Oct")
        compose.click("Run again")
        compose.waitFor("Replace ${starter.title}?")
        // It isn't a new program: it starts again.
        compose.onNodeWithText("You're in week 6 of ${starter.title}; ${starter.title} starts again at week 1, day 1 instead. Past sessions are kept.").assertExists()
        compose.click("Replace")
        assertEquals(listOf("again 1"), calls)
    }

    @Test
    fun aRunOfOneDayReadsAsThatDay() {
        val oneDay = ListedProgram(5, "One day", twoDays, TestPrograms.twoDays("One day"), ListedProgram.Status.Past(at("2026-10-25"), at("2026-10-25") + 3_600_000))
        show(listOf(running, oneDay))
        compose.waitFor("1 block · 2 weeks · 25 Oct")
        compose.click("One day")
        compose.waitFor("Ran 25 Oct")
    }

    @Test
    fun everyProgramExportsButOnlyASavedOneCanBeDeleted() {
        show(listOf(running, past))
        compose.onAllNodesWithText(starter.title)[1].performClick()
        compose.waitFor("Export")
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.click("Export")
        assertEquals(listOf("export 1"), calls)
    }

    @Test
    fun theRunningProgramsSheetSaysWhereYouAreAndRunsAgainOnlyOnceFinished() {
        show(listOf(running))
        compose.click(starter.title)
        compose.waitFor("You're in week 6")
        compose.onNodeWithText("Run again").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun aFinishedRunningProgramRunsAgain() {
        val finished = running.copy(status = ListedProgram.Status.Running(Position(starter.weeks + 1, 0), finished = true))
        show(listOf(finished), active = ActiveProgram(3, starter, Position(starter.weeks + 1, 0)))
        compose.onNodeWithText("Program finished", substring = true).assertExists()
        compose.click(starter.title)
        compose.click("Run again")
        compose.waitFor("Replace ${starter.title}?")
        compose.click("Replace")
        assertEquals(listOf("again 3"), calls)
    }

    @Test
    fun aSavedProgramOpensItsPage() {
        show(listOf(saved, running))
        compose.click("Saved one")
        assertEquals(listOf("open 4"), calls)
    }

    private val leftMidWay = past.copy(status = ListedProgram.Status.Past(at("2026-09-01"), at("2026-10-13"), resumeAt = Position(3, 1)))

    @Test
    fun resumeAsksAndPicksAProgramReplacedMidWayUpWhereItWas() {
        show(listOf(running, leftMidWay))
        compose.onAllNodesWithText(starter.title)[1].performClick()
        compose.waitFor("Ran 1 Sep – 13 Oct · Left in week 3")
        // Run again is there too, below.
        compose.onNodeWithText("Run again").assertExists()
        compose.click("Resume")
        compose.waitFor("Replace ${starter.title}?")
        compose.onNodeWithText("You're in week 6 of ${starter.title}; ${starter.title} picks up at week 3, day 2 instead. Past sessions are kept.").assertExists()
        assertEquals(emptyList<String>(), calls)
        compose.click("Replace")
        assertEquals(listOf("resume 1"), calls)
    }

    @Test
    fun aProgramReplacedAtItsStartOrFinishedHasNoResume() {
        show(listOf(running, past))
        compose.onAllNodesWithText(starter.title)[1].performClick()
        compose.waitFor("Run again")
        compose.onNodeWithText("Resume").assertDoesNotExist()
    }

    @Test
    fun withNothingRunningResumeResumesAtOnce() {
        show(listOf(leftMidWay), active = null)
        compose.click(starter.title)
        compose.click("Resume")
        assertEquals(listOf("resume 1"), calls)
    }
}
