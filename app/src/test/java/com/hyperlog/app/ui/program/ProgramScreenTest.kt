package com.hyperlog.app.ui.program

import androidx.compose.ui.test.assertIsDisplayed
import com.hyperlog.app.onScreen
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.assertButtonOrder
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.ProgramActions
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import java.time.LocalDate
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.program.StarterProgram
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Test
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.week.WeekdayPlan
import java.time.DayOfWeek
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ProgramScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun aRejectedFileListsEveryProblemAsPathThenMessage() {
        var retried = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = null,
                    errors = listOf(
                        """blocks[0].days[0].groups[1].slots[0].exercise: unknown exercise "bb_row"""",
                        """blocks[1].weekStart: must be 5, the week after the previous block""",
                    ),
                    onImport = { retried++ },
                    onCloseErrors = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithText("Not imported").assertIsDisplayed()
        compose.onNodeWithText("2 problems. Fix them and import again; nothing changed.").assertIsDisplayed()
        compose.onNodeWithText("blocks[0].days[0].groups[1].slots[0].exercise").assertIsDisplayed()
        compose.onNodeWithText("""unknown exercise "bb_row"""").assertIsDisplayed()
        compose.onNodeWithText("Choose file").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun withNoProgramItOffersTheImport() {
        var imported = 0
        compose.setContent {
            HyperlogTheme { ProgramScreen(active = null, errors = null, onImport = { imported++ }, onCloseErrors = {}, onBack = {}) }
        }
        compose.onNodeWithText("Import program").performClick()
        assertEquals(1, imported)
    }

    @Test
    fun withNoProgramRunningSavedProgramsAreAButtonBesideImportNotAMenu() {
        val saved = com.hyperlog.app.program.ListedProgram(
            2, "Saved one", program("Saved one"), TestPrograms.twoDays("Saved one"), com.hyperlog.app.program.ListedProgram.Status.Saved(0),
        )
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = null, errors = null, onImport = {}, onCloseErrors = {}, onBack = {},
                    starter = program("Hyperlog Full Body"), listedPrograms = listOf(saved),
                )
            }
        }
        compose.onNodeWithText("Run this program").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").assertDoesNotExist()
        compose.onNodeWithText("Programs").performClick()
        compose.onNodeWithText("Saved one").assertIsDisplayed()
    }

    @Test
    fun withAProgramRunningTheStarterIsInTheMenuAndAsksBeforeReplacing() {
        val program = (ProgramParser.parse(TestPrograms.twoDays("Mine"), AnyId) as ProgramParser.Result.Valid).program
        val starter = program.copy(title = "Hyperlog Full Body")
        var ran = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = ActiveProgram(1, program, Position(1, 0)),
                    errors = null,
                    onImport = {},
                    onCloseErrors = {},
                    onBack = {},
                    starter = starter,
                    onRunStarter = { ran++ },
                )
            }
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Run Hyperlog Full Body").performClick()
        compose.onNodeWithText("Replace Mine?").assertIsDisplayed()
        compose.assertButtonOrder("Cancel", "Replace")
        assertEquals(0, ran)
        compose.onNodeWithText("Replace").performClick()
        assertEquals(1, ran)
    }

    @Test
    fun withNoProgramTheStarterIsOfferedFirst() {
        val starter = (ProgramParser.parse(TestPrograms.twoDays("Hyperlog Full Body"), AnyId) as ProgramParser.Result.Valid).program
        var ran = 0
        var imported = 0
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(null, null, onImport = { imported++ }, onCloseErrors = {}, onBack = {}, starter = starter, onRunStarter = { ran++ })
            }
        }
        compose.onNodeWithText("Starter program").assertIsDisplayed()
        compose.onNodeWithText("Run this program").performClick()
        compose.onNodeWithText("Import program").performClick()
        assertEquals(1, ran)
        assertEquals(1, imported)
    }

    @Test
    fun theProgramActionsAreInTheMenuAndEachProposesAChange() {
        val proposed = mutableListOf<String>()
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active = ActiveProgram(1, program(), Position(1, 1)),
                    errors = null,
                    onImport = {},
                    onCloseErrors = {},
                    onBack = {},
                    onPropose = { proposed += it.summary },
                )
            }
        }
        compose.onNodeWithText("Skip day").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Skip day").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Deload week").performClick()
        assertEquals(listOf("Skip week 1 · Day B", "Week 1 as a deload"), proposed)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Shift program").performClick()
        compose.onNodeWithText("Shift the plan").assertIsDisplayed()
    }

    @Test
    fun theChangesAreOneRowThatOpensTheListWithRevert() {
        val program = program()
        fun record(id: Long, summary: String, reverted: Long? = null) = ChangeRecord(
            AcceptedChange(id, ProgramActions.skipDay(summary), LocalDate.of(2026, 10, 13), Position(1, 0), Position(1, 1), "main"),
            acceptedAt = 0, revertedAt = reverted,
        )
        val active = ActiveProgram(
            1, program, Position(1, 1),
            history = listOf(record(1, "Skip week 1 · Day A"), record(2, "Repeat week 1", reverted = 1)),
            today = LocalDate.of(2026, 10, 14),
        )
        var reverted: Long? = null
        compose.setContent {
            HyperlogTheme { ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, onRevert = { reverted = it }) }
        }
        compose.onNodeWithText("Repeat week 1").assertDoesNotExist()
        compose.onNodeWithText("Changes · 1 in force").performClick()
        compose.onNodeWithText("Repeat week 1").assertIsDisplayed()
        compose.onNodeWithText("Reverted", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Revert").performClick()
        compose.onNodeWithText("Revert").performClick()
        assertEquals(1L, reverted)
    }

    @Test
    fun tappingAChangeShowsItInFull() {
        val program = program()
        val long = "Hands need a week off grip work: the lateral raise comes out of the superset and the triceps stays"
        fun record(id: Long, summary: String, reverted: Long? = null) = ChangeRecord(
            AcceptedChange(id, ProgramActions.skipDay(summary), LocalDate.of(2026, 10, 13), Position(1, 0), Position(1, 1), "main"),
            acceptedAt = 0, revertedAt = reverted,
        )
        val active = ActiveProgram(
            1, program, Position(1, 1),
            history = listOf(record(1, "Skip week 1 · Day A"), record(2, long, reverted = 1)),
            today = LocalDate.of(2026, 10, 14),
        )
        var opened: Long? = null
        compose.setContent {
            HyperlogTheme { ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, onOpenChange = { opened = it }) }
        }
        compose.onNodeWithText("Changes · 1 in force").performClick()
        // One in force opens what it does, as its card on Coach does.
        compose.onNodeWithText("Skip week 1 · Day A").performClick()
        assertEquals(1L, opened)
        // One no longer in force has no diff to show: its summary in full, in a dialog.
        compose.onNodeWithText(long).performClick()
        compose.onNodeWithTag(ChangeDetailTag).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag(ChangeDetailTag).assertDoesNotExist()
    }

    @Test
    fun theBlockAndWeekPickedShowWhatChangedAndEveryDayInFull() {
        val active = ActiveProgram(1, starter(), Position(6, 0), today = LocalDate.of(2026, 10, 13))
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, exerciseNames = mapOf("barbell_bench_press" to "Barbell Bench Press"))
            }
        }
        // It opens on the block and week you're in.
        compose.onNodeWithText("From Ramp-in").assertIsDisplayed()
        // Build is Ramp-in's slots without its block-wide rule: the rule is said once, no slot listed.
        compose.onNodeWithText("Every set: 1 RIR closer to failure · techniques back").assertIsDisplayed()
        compose.onNodeWithText("Otherwise written as in Ramp-in").assertIsDisplayed()
        scrollTo("Week 6 · Day A — Heavy bench, squat, pulldown")
        compose.onAllNodesWithText("Week 6 · Day A — Heavy bench, squat, pulldown")[1].assertIsDisplayed()
        // Next heads the header's day and the day's own head.
        compose.onAllNodesWithText("Next")[1].assertIsDisplayed()

        scrollTo("Intensify")
        compose.onNodeWithText("Intensify").performClick()
        compose.onNodeWithText("From Build").assertIsDisplayed()
        compose.onAllNodesWithText("1 × 3–5\u00A01 RIR", substring = true)[0].assertExists()
        scrollTo("Week 7 · Day C — Bench volume, leg press, pulldown")
        compose.onNodeWithText("Week 7 · Day C — Bench volume, leg press, pulldown").assertIsDisplayed()

        scrollTo("Deload + test")
        compose.onNodeWithText("Deload + test").performClick()
        compose.onNodeWithText("From Intensify").assertIsDisplayed()
        compose.onNodeWithText("A deload week").assertIsDisplayed()
        // Week 12 is written afresh, every slot different, so the comparison folds after three.
        compose.onNodeWithText("Day B").assertDoesNotExist()
        compose.onNodeWithText("20 more", substring = true).performClick()
        compose.onNodeWithText("Day B").assertExists()
        compose.onNodeWithText("Fewer").performScrollTo().performClick()
        scrollTo("Deload")
        compose.onAllNodesWithText("Deload")[0].assertIsDisplayed()
    }

    @Test
    fun aBlockWideTargetRirIsAnOutlinedChip() {
        val program = (ProgramParser.parse(TwoBlocks, AnyId) as ProgramParser.Result.Valid).program
        compose.setContent {
            HyperlogTheme { ProgramScreen(ActiveProgram(1, program, Position(0, 0)), null, onImport = {}, onCloseErrors = {}, onBack = {}) }
        }
        scrollTo("Peak")
        compose.onNodeWithText("Peak").performClick()
        compose.onNodeWithText("From Main").assertIsDisplayed()
        compose.onNodeWithText("Every set:\u00A02 RIR").assertIsDisplayed()
        compose.onAllNodesWithTag(com.hyperlog.app.ui.session.RirChipTargetTag, useUnmergedTree = true).onFirst().assertExists()
        compose.onAllNodesWithText("@", substring = true).assertCountEquals(0)
    }

    @Test
    fun aDoneDayShowsItsTopSetsAndOpensItsSession() {
        val program = program()
        val at = LocalDate.of(2026, 10, 13).atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val session = PastSessionItem(
            7, at, at, "Day A", null,
            listOf(
                ExerciseSummary("Barbell Bench Press", listOf(LoggedSet(80.0, 5, 0, 1), LoggedSet(70.0, 8, 0, 1))),
                // A drop set's top set is the set itself, without its drops.
                ExerciseSummary("Pec Deck", listOf(LoggedSet(50.0, 10, 0, 0, extensions = listOf(Extension(Extension.Kind.Drop, 37.5, 6))))),
            ),
            programId = 1, position = Position(1, 0),
        )
        var opened: Long? = null
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    ActiveProgram(1, program, Position(1, 1)), null, onImport = {}, onCloseErrors = {}, onBack = {},
                    sessions = listOf(session), onOpenSession = { opened = it },
                )
            }
        }
        compose.onNodeWithText("Tue 13 Oct").assertIsDisplayed()
        compose.onNodeWithText(onScreen("80 × 5 @ 1")).assertIsDisplayed()
        // Logged sets: filled RIR chips.
        compose.onAllNodesWithTag(com.hyperlog.app.ui.session.RirChipLoggedTag, useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText(onScreen("50 × 10 @ 0")).assertIsDisplayed()
        compose.onNodeWithText("Tue 13 Oct").performClick()
        assertEquals(7L, opened)
    }

    @Test
    fun theWeekShownIsKeptByTheHostSoItSurvivesLeavingTheScreen() {
        val active = ActiveProgram(1, starter(), Position(6, 0), today = LocalDate.of(2026, 10, 13))
        var view by mutableStateOf(ProgramView(week = 7))
        compose.setContent {
            HyperlogTheme { ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, view = view, onView = { view = it }) }
        }
        compose.onNodeWithText("From Build").assertIsDisplayed()
        compose.onNodeWithContentDescription("Week 8").performClick()
        assertEquals(ProgramView(week = 8), view)
    }
    private fun running() = ActiveProgram(1, program(), Position(1, 0), today = LocalDate.of(2026, 10, 14))

    @Test
    fun aDayMissedAndToComeInTheSameWeekShowsBoth() {
        // Two days on three training days go A B A: Monday's A missed, Saturday's A to come.
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        val today = LocalDate.of(2026, 10, 30)
        val rotation = com.hyperlog.app.week.Rotations.of(program(), com.hyperlog.app.week.RotationAnchor(LocalDate.of(2026, 10, 26), 1, 0), days, emptyList(), today)
        val active = ActiveProgram(1, program(), rotation.next, today = today, rotation = rotation)
        compose.setContent { HyperlogTheme { ProgramScreen(active, null, onImport = {}, onCloseErrors = {}, onBack = {}, weekdayPlan = WeekdayPlan(days.associateWith { null })) } }
        compose.onNodeWithText("Missed · Mon 26 Oct", substring = true).assertExists()
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Week 1 · Sat 31 Oct", substring = true))
    }

    @Test
    fun aDatedDayOverItsTrainingDaysMinutesShowsTheTimeLineAndTappingItStartsACheckIn() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        val today = LocalDate.of(2026, 10, 26)
        val rotation = com.hyperlog.app.week.Rotations.of(program(), com.hyperlog.app.week.RotationAnchor(today, 1, 0), days, emptyList(), today)
        val active = ActiveProgram(1, program(), rotation.next, today = today, rotation = rotation)
        val harbour = Gym(1, "Harbour", emptyList(), missing = setOf("barbell_bench_press"))
        var answer: String? = null
        var asked: com.hyperlog.app.week.FitAsk? = null
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    active, null, onImport = {}, onCloseErrors = {}, onBack = {},
                    weekdayPlan = WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.THURSDAY to null, DayOfWeek.SATURDAY to null), minutes = mapOf(DayOfWeek.MONDAY to 5)),
                    gyms = listOf(harbour), onCheckInFit = { answer = it.words; asked = it },
                )
            }
        }
        compose.onNodeWithText("1 swap at Harbour").assertIsDisplayed()
        compose.onNodeWithText("· Monday has 5", substring = true).assertIsDisplayed().performClick()
        assertTrue(answer, Regex("Day A runs ~\\d+ minutes but Monday has 5\\.").matches(answer.orEmpty()))
        // The Program page's chip hands on the day's last slot and its planned date.
        assertTrue(asked!!.slot != null)
        assertEquals(DayOfWeek.MONDAY, asked!!.date?.dayOfWeek)
    }

    @Test
    fun withNoTrainingDaysProgramAsksForThem() {
        compose.setContent { HyperlogTheme { ProgramScreen(running(), null, onImport = {}, onCloseErrors = {}, onBack = {}) } }
        compose.onNodeWithText("Set your training days").assertIsDisplayed()
    }

    @Test
    fun programShowsYourTrainingDaysAndTheSheetTogglesThem() {
        var plan by mutableStateOf(WeekdayPlan(mapOf(DayOfWeek.MONDAY to null, DayOfWeek.WEDNESDAY to null, DayOfWeek.FRIDAY to null)))
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    running(), null, onImport = {}, onCloseErrors = {}, onBack = {},
                    weekdayPlan = plan, gyms = listOf(Gym(1, "Central", emptyList())), onWeekdayPlan = { plan = it },
                )
            }
        }
        // Fewer training days than the program's week has days: they take turns.
        compose.onNodeWithText("3 days a week · Mon Wed Fri", substring = true).performClick()
        compose.onNodeWithText("Training days").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monday").assertIsOn()
        compose.onNodeWithContentDescription("Tuesday").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Wednesday").performClick()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), plan.days)
        // A day turned on with one gym is at it, so it keeps it when a second gym comes.
        assertEquals(1L, plan.gyms[DayOfWeek.TUESDAY])
        // With one gym every day is at it, so no day picks one.
        compose.onNodeWithContentDescription("Gym on Monday", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("3 days a week · Mon Tue Fri", substring = true).assertIsDisplayed()
    }

    @Test
    fun withSeveralGymsEachTrainingDayPicksItsGym() {
        var plan by mutableStateOf(WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.WEDNESDAY to null)))
        compose.setContent {
            HyperlogTheme {
                ProgramScreen(
                    running(), null, onImport = {}, onCloseErrors = {}, onBack = {},
                    weekdayPlan = plan, gyms = listOf(Gym(1, "Kalliokoski", emptyList()), Gym(2, "Hotel", emptyList())),
                    onWeekdayPlan = { plan = it },
                )
            }
        }
        compose.onNodeWithText("2 days a week · Mon Wed").performClick()
        compose.onNodeWithContentDescription("Gym on Monday: Kalliokoski").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gym on Wednesday: Any gym").performClick()
        compose.onNodeWithText("Hotel").performClick()
        assertEquals(WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.WEDNESDAY to 2L)), plan)
        // A day turned on names no gym until one is picked; one turned off has none to pick.
        compose.onNodeWithContentDescription("Friday").performClick()
        compose.onNodeWithContentDescription("Gym on Friday: Any gym").assertIsDisplayed()
        compose.onNodeWithContentDescription("Monday").performClick()
        compose.onNodeWithContentDescription("Gym on Monday: Kalliokoski").assertDoesNotExist()
        assertEquals(WeekdayPlan(mapOf(DayOfWeek.WEDNESDAY to 2L, DayOfWeek.FRIDAY to null)), plan)
    }
}

private fun program(title: String = "Mine") = (ProgramParser.parse(TestPrograms.twoDays(title), AnyId) as ProgramParser.Result.Valid).program

private fun starter() = (ProgramParser.parse(java.io.File("src/main/assets/${StarterProgram.ASSET}").readText(), AnyId) as ProgramParser.Result.Valid).program

private object AnyId : Set<String> by emptySet() {
    override fun contains(element: String) = true
}

/** A block, then one that sets every set's target RIR. */
private val TwoBlocks = """
    { "format": 1, "title": "Two blocks", "blocks": [
      { "id": "main", "name": "Main", "weekStart": 1, "weekEnd": 1, "days": [
        { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
          "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] } ] } ] },
      { "id": "peak", "name": "Peak", "weekStart": 2, "weekEnd": 2, "weeks": { "2": { "rir": 2 } }, "days": [
        { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
          "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] } ] } ] } ] }
"""
