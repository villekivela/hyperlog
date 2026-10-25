package com.hyperlog.app.ui.program

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.click
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ListedProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.waitFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** A saved program's page (SPEC, Program → Programs → Saved): the program read-only, with Start program, Export and Delete. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SavedProgramScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val names = library.exercises.associate { it.id to it.name }
    private val starter = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program

    private val yRaise = Exercise("kneeling_cable_y_raise", "Kneeling Cable Y-Raise", Equipment.Cable, MovementPattern.LateralRaise, GripDemand.Low, listOf("Side Delts"), emptyList())
    private val source = TestPrograms.twoDays("Saved one").replace("\"barbell_bench_press\"", "\"kneeling_cable_y_raise\"")
    private val program = (ProgramParser.parse(source, ids + yRaise.id) as ProgramParser.Result.Valid).program
    private val savedAt = LocalDate.parse("2026-10-24").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val saved = ListedProgram(4, "Saved one", program, source, ListedProgram.Status.Saved(savedAt, listOf(yRaise)))

    private val calls = mutableListOf<String>()

    private fun show(running: ActiveProgram? = ActiveProgram(3, starter, Position(6, 0))) = compose.setContent {
        HyperlogTheme {
            SavedProgramScreen(
                saved, names, running,
                onStart = { calls += "start" },
                onExport = { calls += "export" },
                onDelete = { calls += "delete" },
                onBack = { calls += "back" },
            )
        }
    }

    @Test
    fun itShowsTheProgramAsItWillRunWithItsNewExercisesAndNothingToChange() {
        show()
        compose.waitFor("Saved one")
        // When it was saved, as its row on Programs says.
        compose.onNodeWithText("Saved 24 Oct").assertExists()
        compose.onNodeWithText("New in your library").assertExists()
        // Under New in your library, and in its day.
        assertEquals(2, compose.onAllNodesWithText("Kneeling Cable Y-Raise").fetchSemanticsNodes().size)
        // The days as Program shows them, from week 1.
        compose.onNodeWithText("Week 1 · Day A", substring = true).performScrollTo().assertExists()
        // Nothing is next until it starts: plain day labels (owner, 2026-10-25).
        compose.onNodeWithText("Next").assertDoesNotExist()
        // Read-only: no review actions.
        compose.onNodeWithText("Reject").assertDoesNotExist()
        compose.onNodeWithText("Save for later").assertDoesNotExist()
    }

    @Test
    fun startAsksBeforeItReplacesTheRunningProgram() {
        show()
        compose.click("Start program")
        compose.waitFor("Replace ${starter.title}?")
        assertEquals(emptyList<String>(), calls)
        compose.click("Replace")
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun withNothingRunningStartStartsAtOnce() {
        show(running = null)
        compose.click("Start program")
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun deleteAsksFirstAndExportSavesItsFile() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Export")
        assertEquals(listOf("export"), calls)
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Delete")
        compose.waitFor("Delete Saved one?")
        compose.click("Delete")
        assertEquals(listOf("export", "delete"), calls)
    }

    @Test
    fun backLeavesIt() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
