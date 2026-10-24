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
    fun withNoProgramThePromptAsksForWordsOrAWorkoutNotAChange() {
        val text = AskClaude.prompt("My knee hurts on lunges.", plan = null, sessions = emptyList(), library = library.exercises, today = oct13)
        assertTrue(text.contains("> My knee hurts on lunges."))
        assertTrue(text, text.contains("No program is running in Hyperlog"))
        assertTrue(text.contains("Today is 2026-10-13 (Tuesday)."))
        assertFalse(text.contains("## Change format"))
        assertTrue(text, text.contains("## Workout format"))
        // Its example is a workout the paste takes, with no program running.
        val example = text.substringAfter("## Workout format")
        assertTrue(AskClaude.read(example, null, ids) is AskClaude.Reply.WorkoutReply)
        // The library still helps Claude name exercises.
        assertTrue(text, text.contains("  machine_chest_press: Machine Chest Press (machine, grip "))
    }

    @Test
    fun eachChangeInForceGoesWithItsOperations() {
        val record = com.hyperlog.app.program.ChangeRecord(
            AcceptedChange(3, Change("Lighter close-grip bench", Scope.RestOfBlock, listOf(Operation.AdjustLoad("close_grip_bench", -10, "elbow"))), oct13.minusDays(2), Position(1, 1), Position(1, 1), program.blockAt(1)!!.id),
            acceptedAt = 3, revertedAt = null,
        )
        val text = AskClaude.prompt("x", plan.copy(history = listOf(record)), emptyList(), library.exercises)
        assertTrue(text, text.contains("- id 3: Lighter close-grip bench (rest of the block, accepted 2026-10-11)"))
        assertTrue(text, text.contains("""  operations: [{"op":"adjust_load","slot":"close_grip_bench","percent":-10,"reason":"elbow"}]"""))
    }

    @Test
    fun theFormatShowsHowToEndOrReplaceAChangeInForce() {
        val record = com.hyperlog.app.program.ChangeRecord(
            AcceptedChange(3, Change("Lighter close-grip bench", Scope.RestOfBlock, listOf(Operation.AdjustLoad("close_grip_bench", -10))), oct13.minusDays(2), Position(1, 1), Position(1, 1), program.blockAt(1)!!.id),
            acceptedAt = 3, revertedAt = null,
        )
        val withChange = plan.copy(history = listOf(record))
        val text = AskClaude.prompt("Elbow's fine now.", withChange, emptyList(), library.exercises)
        assertTrue(text, text.contains("\"replaces\""))
        // Both examples read back as valid replies.
        val ends = text.substringAfter("To only end it:").substringBefore("To replace it")
        val endsChange = (AskClaude.read(ends, program, ids, inForce = setOf(3L)) as AskClaude.Reply.ChangeReply).change
        assertEquals(listOf(3L), endsChange.replaces)
        assertTrue(endsChange.operations.isEmpty())
        val replaces = text.substringAfter("To replace it")
        val replacesChange = (AskClaude.read(replaces, program, ids, inForce = setOf(3L)) as AskClaude.Reply.ChangeReply).change
        assertEquals(listOf(3L), replacesChange.replaces)
        assertTrue(replacesChange.operations.isNotEmpty())
        // Without a Change in force, no such example.
        assertFalse(prompt().contains("To only end it:"))
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
        // Elsewhere: no gym's equipment, the words say what there is.
        val away = AskClaude.prompt("Hotel gym, dumbbells to 30 kg.", plan, emptyList(), library.exercises, gym = null, elsewhere = true)
        assertTrue(away, away.contains("## Gym: somewhere not set up in Hyperlog"))
        assertTrue(away, away.contains("The equipment there is in my words above"))
        assertFalse(away.contains("Dumbbells: "))
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
        val change = (AskClaude.read(reply, program, ids) as AskClaude.Reply.ChangeReply).change
        assertEquals(Change("Rest the elbow", Scope.RestOfBlock, listOf(Operation.RemoveTechnique("curl"))), change)
        // Just the JSON works too.
        val bare = """{"summary": "Skip", "scope": "rest_of_program", "operations": [{"op": "skip_day"}]}"""
        assertTrue(AskClaude.read(bare, program, ids) is AskClaude.Reply.ChangeReply)
    }

    @Test
    fun aBadPasteSaysWhatsWrong() {
        val unknownSlot = "```json\n{\"summary\": \"x\", \"scope\": \"rest_of_program\", \"operations\": [{\"op\": \"remove\", \"slot\": \"rows\"}]}\n```"
        assertEquals(AskClaude.Reply.Invalid(listOf("operations[0].slot: unknown slot \"rows\"")), AskClaude.read(unknownSlot, program, ids))
        assertEquals(AskClaude.Reply.Invalid(listOf(AskClaude.NOTHING_TO_READ)), AskClaude.read("Sounds good, rest up!", program, ids))
        assertTrue(AskClaude.NOTHING_TO_READ.startsWith("No Change, workout or program found"))
        // A reply copied short says so, once, and not to ask Claude.
        assertEquals(AskClaude.Reply.Invalid(listOf(AskClaude.CUT_OFF)), AskClaude.read("Here you go: {\"summary\":\"Hands\",\"scope\":{\"from\":", program, ids))
        assertEquals(AskClaude.Reply.Invalid(listOf(AskClaude.CUT_OFF)), AskClaude.read("```json\n{\"summary\": \"Hands\", \"scope\": {\"from\": \"2026-10-13\", \"to\": \"2026-10-19\"}, \"operations\": [", program, ids))
        assertTrue((AskClaude.read("{\"summary\": }", program, ids) as AskClaude.Reply.Invalid).errors.single().startsWith("not a Change"))
    }

    @Test
    fun withAProgramThePromptDescribesBothRepliesAndBothExamplesPaste() {
        val text = prompt()
        assertTrue(text, text.contains("a Change or a workout"))
        assertTrue(text.contains("## Change format"))
        assertTrue(text.contains("## Workout format"))
        val change = text.substringAfter("## Change format").substringBefore("## Workout format")
        assertTrue(AskClaude.read(change, program, ids) is AskClaude.Reply.ChangeReply)
        val workout = text.substringAfter("## Workout format")
        assertTrue(AskClaude.read(workout, program, ids) is AskClaude.Reply.WorkoutReply)
    }

    @Test
    fun aPastedWorkoutReadsAsOneAndItsProblemsHavePaths() {
        val reply = """
            Here's the hotel day:
            ```json
            { "workout": { "name": "Hotel, 45 min", "summary": "Dumbbells only.", "groups": [
              { "slots": [ { "id": "row", "exercise": "chest_supported_db_row", "sets": [ { "count": 3, "reps": "8-12", "rir": 1 } ] } ] } ] } }
            ```
        """.trimIndent()
        val workout = (AskClaude.read(reply, program, ids) as AskClaude.Reply.WorkoutReply).workout
        assertEquals("Hotel, 45 min", workout.name)
        // With no program a workout still pastes.
        assertTrue(AskClaude.read(reply, null, ids) is AskClaude.Reply.WorkoutReply)
        val bad = reply.replace("chest_supported_db_row", "bb_row")
        assertEquals(
            AskClaude.Reply.Invalid(listOf("workout.groups[0].slots[0].exercise: unknown exercise \"bb_row\"")),
            AskClaude.read(bad, program, ids),
        )
    }

    @Test
    fun aChangePastedWithNoProgramRunningSaysThereIsNothingToChange() {
        val change = """{"summary": "Skip", "scope": "rest_of_program", "operations": [{"op": "skip_day"}]}"""
        assertEquals(AskClaude.Reply.Invalid(listOf(AskClaude.NO_PROGRAM)), AskClaude.read(change, null, ids))
        assertEquals("No program is running, so there’s nothing for a Change to change.", AskClaude.NO_PROGRAM)
    }

    @Test
    fun theFocusMarksTheThreeGroupsTrainedInTheLastFourWeeksWithTheFewestSetsThisWeek() {
        val thisWeek = LocalDate.of(2026, 10, 12)
        val weeks = listOf(
            WeekSets(thisWeek, mapOf("Chest" to 18.0, "Back" to 4.0, "Shoulders" to 3.0, "Arms" to 6.0)),
            WeekSets(thisWeek.minusWeeks(1), mapOf("Legs" to 9.0, "Chest" to 12.0)),
            WeekSets(thisWeek.minusWeeks(3), mapOf("Core" to 2.0)),
            // Four weeks back: outside the last four weeks.
            WeekSets(thisWeek.minusWeeks(4), mapOf("Glutes" to 1.0)),
        )
        // Legs and Core had none this week; then Shoulders with 3. Glutes isn't trained lately.
        assertEquals(listOf("Legs" to 0.0, "Core" to 0.0, "Shoulders" to 3.0), WorkoutOptions.lowest(weeks))
        assertEquals(emptyList<Pair<String, Double>>(), WorkoutOptions.lowest(emptyList()))
    }

    @Test
    fun theTimesReadAsTheChipsDo() {
        assertEquals(listOf("45 min", "1 h", "1 h 15", "1 h 30"), WorkoutOptions.Times.map { WorkoutOptions.timeText(it) })
    }

    @Test
    fun aPastedProgramReadsAsOneAndItsProblemsHavePaths() {
        val file = File("src/main/assets/${StarterProgram.ASSET}").readText()
        val reply = "Here is the program:\n```json\n{ \"program\": $file }\n```"
        val read = AskClaude.read(reply, null, ids, merged = library.merged) as AskClaude.Reply.ProgramReply
        assertEquals(program.title, read.program.title)
        // It reads with a program running too, and a Change is still a Change.
        assertTrue(AskClaude.read(reply, program, ids, merged = library.merged) is AskClaude.Reply.ProgramReply)
        val bad = reply.replace("close_grip_bench_press", "close_grip_bench_pres")
        val invalid = AskClaude.read(bad, program, ids, merged = library.merged) as AskClaude.Reply.Invalid
        assertTrue(invalid.errors.toString(), invalid.errors.all { it.startsWith("program.") })
    }

    @Test
    fun theTileIsQuotedUnlessTheWordsAlreadyStartWithItsSentence() {
        fun quoted(tile: Tile, words: String) =
            AskClaude.prompt(words, plan, emptyList(), library.exercises, tile = tile).contains("> ${tile.words}\n")
        // The prefilled sentence says it already.
        assertFalse(quoted(Tile.Hurts, "Something hurts: left elbow."))
        assertFalse(quoted(Tile.ShortOnTime, "I’m short on time today: I have 45 minutes."))
        assertFalse(quoted(Tile.Workout, "Build me a workout for today: "))
        // Edited away, or a sentence that isn't the tile's, the tile says what the check-in is about.
        assertTrue(quoted(Tile.Hurts, "Left elbow."))
        assertTrue(quoted(Tile.ShortOnTime, ""))
        assertTrue(quoted(Tile.Gym, "No gym today, only dumbbells at home."))
        assertTrue(quoted(Tile.SomethingElse, ""))
        val text = AskClaude.prompt("Something hurts: left elbow.", plan, emptyList(), library.exercises, tile = Tile.Hurts)
        assertTrue(text, text.contains("> Something hurts: left elbow.\n"))
    }
}
