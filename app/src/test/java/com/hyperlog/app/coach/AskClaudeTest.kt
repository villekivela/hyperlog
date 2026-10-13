package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
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
    fun theFormatListsEveryOperationAndScopeTheValidatorReads() {
        val text = prompt()
        // SPEC, Coaching: the eleven operations and three scopes.
        listOf(
            "swap", "remove", "add", "adjust_sets", "adjust_load", "adjust_rir", "remove_technique",
            "skip_day", "deload_week", "shift_program", "repeat_week",
        ).forEach { assertTrue("op $it", text.contains("\"op\": \"$it\"")) }
        listOf("\"rest_of_program\"", "\"rest_of_block\"", "\"from\"", "\"to\"").forEach { assertTrue("scope $it", text.contains(it)) }
        listOf("dropset", "myo_reps", "lengthened_partials", "iso_hold", "failure").forEach { assertTrue("technique $it", text.contains(it)) }
        // The validator's ranges.
        assertTrue(text, text.contains("−50 to 50"))
        assertTrue(text, text.contains("1 to 10"))
    }

    @Test
    fun theExampleIsAChangeThePasteAccepts() {
        val text = prompt()
        val change = (AskClaude.read(text.substringAfterLast("For example:"), program, ids) as ChangeValidator.Result.Valid).change
        // It swaps a slot of the next day (Day B) for one of that slot's alternatives.
        val swap = change.operations.filterIsInstance<Operation.Swap>().single()
        val slot = plan.next!!.slot(swap.slot)!!
        assertTrue(swap.exercise in slot.alternatives)
    }

    @Test
    fun thePromptHoldsTheWordsThePositionTheLastFourWeeksAndTheLibrary() {
        val day = 86_400_000L
        val now = oct13.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 17 * 3_600_000
        fun session(daysAgo: Int, title: String) = PastSessionItem(
            id = daysAgo.toLong(), startedAt = now - daysAgo * day, finishedAt = now - daysAgo * day + 3_600_000,
            title = title, gym = "Kamppi",
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
        assertTrue(text, text.contains("2026-10-11 Sun: Day A — recent at Kamppi"))
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
