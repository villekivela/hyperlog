package com.hyperlog.app.ui.program

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.click
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.KeptProgram
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

/** Program → ⋮ → Programs (SPEC, Program → Programs): every program held, with Start, Run again, Export and Delete. */
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

    private val running = KeptProgram(3, starter.title, starter, starterSource, KeptProgram.Status.Running(Position(6, 0), finished = false), trained = true)
    private val saved = KeptProgram(4, "Saved one", twoDays, TestPrograms.twoDays("Saved one"), KeptProgram.Status.Saved(at("2026-10-24")), trained = false)
    private val past = KeptProgram(
        1, starter.title, starter, starterSource, KeptProgram.Status.Past(at("2026-09-01"), at("2026-10-13")), trained = true,
    )

    private val calls = mutableListOf<String>()

    @Before
    fun zone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Helsinki"))

    private fun show(kept: List<KeptProgram>, active: ActiveProgram? = ActiveProgram(3, starter, Position(6, 0))) = compose.setContent {
        HyperlogTheme {
            ProgramsPage(
                kept, active,
                onStart = { calls += "start $it" },
                onRunAgain = { calls += "again $it" },
                onExport = { calls += "export ${it.id}" },
                onDelete = { calls += "delete $it" },
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
    fun startAsksBeforeItReplacesTheRunningProgram() {
        show(listOf(saved, running))
        compose.click("Start")
        compose.waitFor("Replace ${starter.title}?")
        assertEquals(emptyList<String>(), calls)
        compose.click("Replace")
        assertEquals(listOf("start 4"), calls)
    }

    @Test
    fun runAgainAsksAndStartsAPastProgramAgain() {
        show(listOf(running, past))
        compose.click("Run again")
        compose.waitFor("Replace ${starter.title}?")
        compose.click("Replace")
        assertEquals(listOf("again 1"), calls)
    }

    @Test
    fun withNothingRunningStartStartsAtOnce() {
        show(listOf(saved), active = null)
        compose.click("Start")
        assertEquals(listOf("start 4"), calls)
    }

    @Test
    fun deleteAsksAndRemovesASavedProgram() {
        show(listOf(saved, running))
        compose.onAllNodesWithContentDescription("More for Saved one")[0].performClick()
        compose.click("Delete")
        compose.waitFor("Delete Saved one?")
        assertEquals(emptyList<String>(), calls)
        compose.click("Delete")
        assertEquals(listOf("delete 4"), calls)
    }

    @Test
    fun everyProgramExportsButOneWithSessionsCantBeDeleted() {
        show(listOf(running, past))
        compose.onAllNodesWithContentDescription("More for ${starter.title}")[1].performClick()
        compose.waitFor("Export")
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.click("Export")
        assertEquals(listOf("export 1"), calls)
    }
}
