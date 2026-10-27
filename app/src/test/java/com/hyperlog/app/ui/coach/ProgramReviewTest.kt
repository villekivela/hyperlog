package com.hyperlog.app.ui.coach

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.click
import com.hyperlog.app.coach.ProgramFormat
import com.hyperlog.app.coach.ProgramProposal
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.waitFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A new program's review (SPEC, Coaching → New program): the program as Program shows it, with its
 * swaps and new exercises, and Start program asking first when a program runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramReviewTest {
    @get:Rule
    val compose = createComposeRule()

    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val names = library.exercises.associate { it.id to it.name }
    private val starter = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program

    /** The format's example with the curl done as a new drag curl, and a seal row swapped for the library's row. */
    private val proposal: ProgramProposal by lazy {
        val file = ProgramFormat.example(library.exercises).toString()
        val curl = file.substringAfter("\"id\":\"curl\",\"exercise\":\"").substringBefore('"')
        val row = file.substringAfter("\"id\":\"row\",\"exercise\":\"").substringBefore('"')
        val reply = """{"program": ${file.replace("\"exercise\":\"$curl\"", "\"exercise\":\"barbell_drag_curl\"")},
            "swaps": [{"from": "Seal Row", "to": "$row"}],
            "newExercises": [{"id": "barbell_drag_curl", "name": "Barbell Drag Curl", "equipment": "barbell", "pattern": "elbow_flexion",
              "grip": "high", "muscles": ["Biceps"], "secondary": []}]}"""
        (ProgramProposal.read(Json.parseToJsonElement(reply).jsonObject, ids, names) as ProgramProposal.Result.Valid).proposal
    }

    private var started = 0
    private var rejected = 0
    private var saved = 0

    private fun show(running: ActiveProgram?, file: String? = null) = compose.setContent {
        HyperlogTheme {
            ProgramReviewScreen(proposal, names, running, onStart = { started++ }, onReject = { rejected++ }, file = file, onSave = { saved++ })
        }
    }

    @Test
    fun theReviewShowsTheProgramItsSwapsAndItsNewExercises() {
        show(running = null)
        compose.waitFor("Full body, 2 days")
        val row = names.getValue(proposal.swaps.single().to)
        compose.onNodeWithText("Seal Row → $row").performScrollTo()
        compose.onNodeWithText("New in your library").performScrollTo()
        compose.onNodeWithText("Barbell Drag Curl", substring = true).performScrollTo()
        // The blocks and the first week's days, as Program shows them.
        compose.onNodeWithText("Ramp-in").performScrollTo()
        compose.onNodeWithText("Day A — Squat, press, row", substring = true).performScrollTo()
    }

    @Test
    fun theReviewShowsTheFitUnderEachDayButItIsNotATap() {
        val plan = com.hyperlog.app.week.WeekdayPlan(
            gyms = mapOf(java.time.DayOfWeek.MONDAY to 1L, java.time.DayOfWeek.THURSDAY to null),
            minutes = mapOf(java.time.DayOfWeek.MONDAY to 5),
        )
        val gym = com.hyperlog.app.gym.Gym(1, "Harbour", emptyList(), missing = setOf(proposal.program.blocks.first().days.first().groups.first().slots.first().exercise))
        compose.setContent {
            HyperlogTheme {
                ProgramReviewScreen(
                    proposal, names, null, onStart = {}, onReject = {},
                    fit = ReviewFit(plan, listOf(gym), library.exercises, java.time.LocalDate.of(2026, 10, 26)),
                )
            }
        }
        compose.onNodeWithText("· Monday has 5", substring = true).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("1 swap at Harbour").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aProgramFromAFileSaysWhichFile() {
        show(running = null, file = "Nippard-Upper-Lower.pdf")
        compose.waitFor("From Nippard-Upper-Lower.pdf")
    }

    @Test
    fun withNoProgramRunningStartProgramStartsIt() {
        show(running = null)
        compose.click("Start program")
        assertEquals(1, started)
    }

    @Test
    fun withAProgramRunningStartProgramAsksFirstAsImportDoes() {
        show(running = ActiveProgram(1, starter, Position(6, 0)))
        compose.click("Start program")
        compose.waitFor("Replace ${starter.title}?")
        compose.onNodeWithText("You're in week 6 of ${starter.title}; the new program starts at week 1, day 1 instead. Past sessions are kept.").assertExists()
        assertEquals(0, started)
        compose.click("Replace")
        assertEquals(1, started)
    }

    @Test
    fun saveForLaterKeepsItWithoutAskingAndLeavesTheRunningProgram() {
        show(running = ActiveProgram(1, starter, Position(6, 0)))
        compose.click("Save for later")
        compose.onNodeWithText("Replace ${starter.title}?").assertDoesNotExist()
        assertEquals(listOf(1, 0, 0), listOf(saved, started, rejected))
    }

    @Test
    fun rejectDropsIt() {
        show(running = null)
        compose.click("Reject")
        assertEquals(listOf(0, 1), listOf(started, rejected))
    }
}
