package com.hyperlog.app.coach

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** What a check-in through the API tells Claude (SPEC, What Claude is told). */
class CheckInContextTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val made = SavedCheckIn("Something hurts.", listOf(SavedCheckIn.Step("Which hand?", "Left, only gripping")))
    private val record = ChangeRecord(
        AcceptedChange(3, Change("No grip work for a week", Scope.RestOfBlock, listOf(Operation.AdjustRir(null, 1, "hands"))), oct13.minusDays(2), Position(1, 1), Position(1, 1), program.blockAt(1)!!.id),
        acceptedAt = 3, revertedAt = null, checkIn = made,
    )
    private val plan = ActiveProgram(1, program, Position(1, 1), history = listOf(record), today = oct13)
    private val gyms = listOf(Gym(1, "Harbour", emptyList()), Gym(2, "Itis", emptyList()))

    private fun frame(plan: ActiveProgram? = this.plan) = CheckInContext.frame(
        plan, sessions = emptyList(), library = library.exercises, gyms = gyms,
        lowest = listOf("Back" to 4.0, "Legs" to 0.0, "Core" to 0.0), today = oct13,
    )

    @Test
    fun claudeIsToldHowACheckInGoesAndWhatItCanUse() {
        val context = frame().context
        assertTrue(context, context.contains("ask tool"))
        assertTrue(context, context.contains("at most four"))
        // The gym only when the equipment matters, with my gyms and Elsewhere.
        assertTrue(context, context.contains("Harbour, Itis and Elsewhere"))
        // The workout's options, with the groups behind this week.
        assertTrue(context, context.contains("Easy (3 or more reps in reserve, no techniques)"))
        assertTrue(context, context.contains("45 min to 1 h 30"))
        assertTrue(context, context.contains("Back (4 sets), Legs (0 sets), Core (0 sets)"))
        // Change my program proposes a lasting Change.
        assertTrue(context, context.contains("rest_of_program"))
        // A new program: from answers or a file, with swaps for exercises the library lacks.
        assertTrue(context, context.contains("propose_program"))
        assertTrue(context, context.contains("Attach a spreadsheet, PDF or photo"))
        assertTrue(context, context.contains("closest library exercise"))
        // The same situation as Ask Claude's, and the formats are the tools, not text.
        assertTrue(context, context.contains("Today is 2026-10-13 (Tuesday)."))
        assertTrue(context, context.contains("## Program: "))
        assertTrue(context, context.contains("## Exercise library"))
        assertFalse(context, context.contains("## Change format"))
        assertEquals(listOf(CoachTools.ASK, CoachTools.PROPOSE_CHANGE, CoachTools.PROPOSE_WORKOUT, CoachTools.PROPOSE_PROGRAM), frame().tools.map { it.name })
    }

    @Test
    fun withMoreThanThreeGymsClaudeOffersTheLikeliestThreeAndElsewhere() {
        val five = (1L..5L).map { Gym(it, "Gym $it", emptyList()) }
        val context = CheckInContext.frame(plan, emptyList(), library.exercises, five, today = oct13).context
        assertTrue(context, context.contains("My gyms: Gym 1, Gym 2, Gym 3, Gym 4 and Gym 5."))
        assertTrue(context, context.contains("offer the 3 likeliest and Elsewhere, and let me type my own for the rest"))
        // With three or fewer, all of them.
        assertTrue(frame().context, frame().context.contains("Harbour, Itis and Elsewhere as the answers"))
        assertTrue(CoachTools.ask().description, CoachTools.ask().description.contains("at most 3 gyms and Elsewhere"))
    }

    @Test
    fun aChangeInForceGoesWithTheCheckInThatMadeIt() {
        val context = frame().context
        assertTrue(context, context.contains("- id 3: No grip work for a week"))
        assertTrue(context, context.contains("  check-in: > Something hurts. / Claude: Which hand? / > Left, only gripping"))
    }

    @Test
    fun howItsGoingOpensWithTheChangeAndItsCheckIn() {
        val opening = CheckInContext.howItsGoing(record)
        assertEquals(
            "How's it going with this Change? \"No grip work for a week\" (id 3, accepted 2026-10-11).\n" +
                "The check-in that made it:\n> Something hurts.\nClaude: Which hand?\n> Left, only gripping",
            opening,
        )
    }

    @Test
    fun withNoProgramThereIsNoChangeToPropose() {
        val frame = frame(plan = null)
        assertEquals(listOf(CoachTools.ASK, CoachTools.PROPOSE_WORKOUT, CoachTools.PROPOSE_PROGRAM), frame.tools.map { it.name })
        assertTrue(frame.context, frame.context.contains("No program is running"))
    }

    @Test
    fun theApiContextAndAskClaudesPromptHoldOneSituationBuiltOnce() {
        val sessions = emptyList<com.hyperlog.app.session.PastSessionItem>()
        val context = CheckInContext.frame(plan, sessions, library.exercises, today = oct13, lastTrained = oct13.minusDays(3)).context
        val prompt = AskClaude.prompt("x", plan, sessions, library.exercises, today = oct13, lastTrained = oct13.minusDays(3))
        val fromContext = context.substring(context.indexOf("Today is ")).trimEnd()
        val fromPrompt = prompt.substring(prompt.indexOf("Today is "), prompt.indexOf("## Change format")).trimEnd()
        assertTrue(fromContext.contains("## Program: "))
        assertEquals(fromContext, fromPrompt)
    }

    @Test
    fun aNewProgramIsWrittenForTheTrainingDaysTheirGymsEquipmentMinutesAndTheGoals() {
        val bench = library.exercises.first { it.id == "barbell_bench_press" }
        val harbour = Gym(1, "Harbour", listOf(com.hyperlog.app.gym.EquipmentItem(gymId = 1, name = "Smith machine", ladder = com.hyperlog.app.domain.LadderSpec.DefaultDumbbells)))
        val days = com.hyperlog.app.week.WeekdayPlan(
            gyms = mapOf(java.time.DayOfWeek.MONDAY to 1L, java.time.DayOfWeek.THURSDAY to null, java.time.DayOfWeek.SATURDAY to 1L),
            minutes = mapOf(java.time.DayOfWeek.SATURDAY to 50),
        )
        val goals = listOf(com.hyperlog.app.history.LiftGoal(bench.id, 100.0, null))
        val context = CheckInContext.frame(plan, emptyList(), library.exercises, listOf(harbour), today = oct13, weekdayPlan = days, goals = goals).context
        assertTrue(context, "- Saturday: Harbour, 50 minutes" in context)
        assertTrue(context, "Smith machine" in context)
        assertTrue(context, "${bench.name}: 100 kg" in context)
        assertTrue(context, "as many days as I have training days (3)" in context)
        assertTrue(context, "Day A goes on the first training day from today, Thursday 2026-10-15" in context)
        assertTrue(context, "Day A — Mon · Heavy bench, squat, pulldown" in context)
    }
}
