package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.history.WeekSets
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/** The prompt Ask Claude shares with the Claude app, and the Change pasted back (SPEC, Ask Claude). */
class AskClaudeTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val plan = ActiveProgram(1, program, Position(1, 1), today = oct13)

    private fun prompt(words: String = "My left elbow aches on curls.") =
        AskClaude.prompt(words, plan, sessions = emptyList(), library = library.exercises)

    @Test
    fun withNoProgramThePromptAsksForAdviceNotAChange() {
        val text = AskClaude.prompt("My knee hurts on lunges.", plan = null, sessions = emptyList(), library = library.exercises, today = oct13)
        assertTrue(text.contains("> My knee hurts on lunges."))
        assertTrue(text, text.contains("No program is running in Hyperlog"))
        assertTrue(text.contains("Today is 2026-10-13 (Tuesday)."))
        assertFalse(text.contains("## Change format"))
        assertFalse(text.contains("```json"))
        // The library still helps Claude name exercises.
        assertTrue(text, text.contains("  machine_chest_press: Machine Chest Press (machine, grip "))
    }

    @Test
    fun claudeMayAnswerInWordsAlone() {
        val text = prompt()
        assertTrue(text, text.contains("If the plan can stand as it is, say so in words and send no JSON."))
    }

    @Test
    fun thePromptHoldsTheDaysOff() {
        val tuesdays = AskClaude.prompt("x", plan, emptyList(), library.exercises, lastTrained = oct13.minusDays(14), weekdays = setOf(DayOfWeek.TUESDAY))
        assertTrue(tuesdays, tuesdays.contains("My last finished session was on 2026-09-29, 14 days ago. I plan to train on Tuesday, so that's 7 days off."))
        val none = AskClaude.prompt("x", plan, emptyList(), library.exercises, lastTrained = null)
        assertTrue(none, none.contains("I haven't finished a session in Hyperlog yet."))
        val noPlan = AskClaude.prompt("x", plan, emptyList(), library.exercises, lastTrained = oct13.minusDays(11))
        assertTrue(noPlan, noPlan.contains("My last finished session was on 2026-10-02, 11 days ago."))
        assertFalse(noPlan, noPlan.contains("days off"))
    }

    @Test
    fun thePromptHoldsTheGymItsEquipmentAndWhatsMissingThere() {
        val gym = Gym(
            1, "Harbour",
            items = listOf(EquipmentItem(gymId = 1, name = "Seated leg curl", ladder = LadderSpec.Explicit(listOf(5.0, 10.0, 15.0)))),
            missing = setOf("leg_extension"),
        )
        val text = AskClaude.prompt("x", plan, emptyList(), library.exercises, gym = gym)
        assertTrue(text, text.contains("## Gym: Harbour"))
        assertTrue(text, text.contains("- Seated leg curl (5–15 kg"))
        assertTrue(text, text.contains("- Dumbbells: "))
        assertTrue(text, text.contains("Not here, so swap them out: leg_extension (Leg Extension)"))
        // No gym picked, no section.
        assertFalse(prompt().contains("## Gym"))
    }

    @Test
    fun thePromptHoldsTheWeeklySetsPerMuscleThisWeekAndTheFourBefore() {
        val weeks = (0 until 8).map { WeekSets(LocalDate.of(2026, 10, 12).minusWeeks(it.toLong()), mapOf("chest" to 6.0 - it, "triceps" to 1.5)) }
        val text = AskClaude.prompt("x", plan, emptyList(), library.exercises, weeklySets = weeks)
        assertTrue(text, text.contains("## Weekly sets per muscle"))
        assertTrue(text, text.contains("Week of 2026-10-12 (this week): chest 6, triceps 1.5"))
        assertTrue(text, text.contains("Week of 2026-09-14: chest 2, triceps 1.5"))
        // Five weeks, no more.
        assertFalse(text, text.contains("Week of 2026-09-07"))
    }

    @Test
    fun theLibraryListsEveryVisibleExercise() {
        val programSection = prompt().substringBefore("## Exercise library")
        // Two far from anything the program uses: one visible, one hidden.
        val (visible, hidden) = library.exercises.filter { !it.hidden && it.id !in programSection }.take(2)
        val exercises = library.exercises.map { if (it.id == hidden.id) it.copy(hidden = true) else it }
        val text = AskClaude.prompt("x", plan, emptyList(), exercises)
        assertTrue(visible.id, text.contains("  ${visible.id}: ${visible.name} ("))
        assertFalse(hidden.id, text.contains("  ${hidden.id}: "))
    }

    @Test
    fun thePromptHoldsTheWordsThePositionTheLastFourWeeksAndTheLibrary() {
        val day = 86_400_000L
        val now = oct13.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 17 * 3_600_000
        fun session(daysAgo: Int, title: String) = PastSessionItem(
            id = daysAgo.toLong(), startedAt = now - daysAgo * day, finishedAt = now - daysAgo * day + 3_600_000,
            title = title, gym = "Harbour",
            exercises = listOf(ExerciseSummary("Barbell Bench Press", listOf(LoggedSet(80.0, 5, 0, 1), LoggedSet(70.0, 8, 2, 1)))),
        )
        val text = AskClaude.prompt(
            "Elbow aches on curls.\nGym closes early on Friday.",
            plan,
            sessions = listOf(session(2, "Day A — recent"), session(28, "Day C — four weeks ago"), session(29, "Day B — too old")),
            library = library.exercises,
            zone = ZoneOffset.UTC,
        )
        assertTrue(text.contains("> Elbow aches on curls.\n> Gym closes early on Friday."))
        assertTrue(text.contains("Today is 2026-10-13 (Tuesday)."))
        assertTrue(text, text.contains("Next: week 1 of 12, "))
        assertTrue(text, text.contains("slot \"close_grip_bench\": close_grip_bench_press"))
        assertTrue(text, text.contains("2026-10-11 Sun: Day A — recent at Harbour"))
        assertTrue(text.contains("  Barbell Bench Press: 80 × 5 @ 1, 70 × 8 + 2p @ 1"))
        assertTrue(text.contains("Day C — four weeks ago"))
        assertFalse(text.contains("too old"))
        // A swap candidate the program doesn't use, found by movement pattern.
        assertTrue(text, text.contains("  machine_chest_press: Machine Chest Press (machine, grip "))
    }

    @Test
    fun aPastedReplyIsReadFromItsFencedBlock() {
        val reply = """
            Here's the change we discussed:

            ```json
            {"summary": "Rest the elbow", "scope": "rest_of_block", "operations": [{"op": "remove_technique", "slot": "curl"}]}
            ```

            Paste it into Hyperlog to review.
        """.trimIndent()
        val change = (AskClaude.read(reply, program, ids) as ChangeValidator.Result.Valid).change
        assertEquals(Change("Rest the elbow", Scope.RestOfBlock, listOf(Operation.RemoveTechnique("curl"))), change)
        // Just the JSON works too.
        val bare = """{"summary": "Skip", "scope": "rest_of_program", "operations": [{"op": "skip_day"}]}"""
        assertTrue(AskClaude.read(bare, program, ids) is ChangeValidator.Result.Valid)
    }

    @Test
    fun aBadPasteSaysWhatsWrong() {
        val unknownSlot = "```json\n{\"summary\": \"x\", \"scope\": \"rest_of_program\", \"operations\": [{\"op\": \"remove\", \"slot\": \"rows\"}]}\n```"
        assertEquals(ChangeValidator.Result.Invalid(listOf("operations[0].slot: unknown slot \"rows\"")), AskClaude.read(unknownSlot, program, ids))
        assertEquals(ChangeValidator.Result.Invalid(listOf(AskClaude.NOTHING_TO_READ)), AskClaude.read("Sounds good, rest up!", program, ids))
        assertTrue((AskClaude.read("{\"summary\": }", program, ids) as ChangeValidator.Result.Invalid).errors.single().startsWith("not a Change"))
    }
}
