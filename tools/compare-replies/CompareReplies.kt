package com.hyperlog.app.coach

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.week.WeekdayPlan
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

/**
 * Not a test of the app. `tools/compare-replies/run.sh` copies this file (and one `Shim.kt`) into a
 * checkout's test sources and runs it, so the check-in's real code builds the context and drives the
 * conversation. It does nothing unless `COMPARE_MODE` is set, and `ANTHROPIC_API_KEY` is read only in
 * the `run` mode; the repo's own test run never sets either.
 *
 * - `frames`: writes each scenario's frame (context and tools) to `$COMPARE_DIR/<scenario>.frame.json`.
 * - `run`: reads those frames from `$COMPARE_FRAMES`, plays each scenario against the API, and
 *   writes `$COMPARE_DIR/<scenario>.run.json`.
 */
@RunWith(RobolectricTestRunner::class)
class CompareReplies {
    private val mode = System.getenv("COMPARE_MODE")
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val today = LocalDate.of(2026, 10, 13)
    private val plan = ActiveProgram(1, program, Position(1, 1), today = today)

    /** The owner's gyms stand in as a full commercial gym and a hotel gym with dumbbells only. */
    private val gyms = listOf(
        Gym(
            1, "Harbour",
            listOf("Lat pulldown", "Cable crossover", "Leg press", "Hack squat", "Pec deck", "Leg extension", "Seated leg curl", "Smith machine", "Seated calf raise")
                .mapIndexed { i, name -> EquipmentItem(i + 1L, 1, name, LadderSpec.Explicit(listOf(10.0, 20.0, 30.0)), kind = EquipmentItem.MACHINE) } +
                EquipmentItem(20, 1, "Squat rack", LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK),
        ),
        Gym(
            2, "Itis", emptyList(),
            missing = library.exercises.filter { it.equipment !in setOf(Equipment.Dumbbell, Equipment.Bodyweight) }.map { it.id }.toSet(),
        ),
    )

    private val weekdayPlan = WeekdayPlan(
        gyms = mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.WEDNESDAY to 1L, DayOfWeek.FRIDAY to 1L),
        minutes = mapOf(DayOfWeek.MONDAY to 60, DayOfWeek.WEDNESDAY to 45, DayOfWeek.FRIDAY to 60),
    )

    /** Six sessions of the starter's first block, the weights fixed so every run reads the same history. */
    private val sessions: List<PastSessionItem> = run {
        val day = 86_400_000L
        val noon = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 17 * 3_600_000
        val days = program.blocks.first().days
        val names = library.exercises.associate { it.id to it.name }
        listOf(2, 4, 6, 9, 11, 13).mapIndexed { i, ago ->
            val d = days[(days.size - 1 - i % days.size)]
            PastSessionItem(
                id = ago.toLong(), startedAt = noon - ago * day, finishedAt = noon - ago * day + 3_600_000, title = d.name, gym = "Harbour",
                exercises = d.groups.flatMap { it.slots }.mapIndexed { n, slot ->
                    ExerciseSummary(names.getValue(slot.exercise), listOf(LoggedSet(30.0 + 5.0 * n, 8, 0, 1), LoggedSet(30.0 + 5.0 * n, 7, 0, 1)))
                },
            )
        }
    }

    /** What each scenario says first, and how its questions are answered: the first offered answer holding a keyword, else the first answer. */
    private class Scenario(val key: String, val tile: Tile, val line: String, val keywords: List<String>)

    private val scenarios = listOf(
        Scenario("new-program", Tile.NewProgram, "I train 3 days a week, 45 to 60 minutes a session, at Harbour.", listOf("Harbour")),
        Scenario("new-program-chest", Tile.NewProgram, "I train 3 days a week, 45 to 60 minutes a session, at Harbour. Bring up my chest.", listOf("Harbour", "Chest")),
        Scenario("hurts-elbow", Tile.Hurts, "My left elbow hurts when I press.", listOf("Harbour", "Left", "Press", "Elbow")),
        Scenario("workout-45", Tile.Workout, "I have 45 minutes today, at Harbour.", listOf("Harbour", "45", "Normal")),
    )

    private fun frame(): CheckInFrame = compareFrame(plan, sessions, library.exercises, gyms, weekdayPlan, today)

    @Test
    fun compare() = runTest(timeout = 40.minutes) {
        assumeTrue("COMPARE_MODE isn't set", mode != null)
        val out = File(System.getenv("COMPARE_DIR")!!).also { it.mkdirs() }
        when (mode) {
            "frames" -> {
                val frame = frame()
                scenarios.forEach { File(out, "${it.key}.frame.json").writeText(Json.encodeToString(CheckInFrame.serializer(), frame)) }
            }
            "run" -> {
                val key = checkNotNull(System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }) { "ANTHROPIC_API_KEY isn't set" }
                val frames = File(System.getenv("COMPARE_FRAMES")!!)
                val only = System.getenv("COMPARE_ONLY")?.takeIf { it.isNotBlank() }?.split(",")
                scenarios.filter { only == null || it.key in only }.forEach { s ->
                    val frame = Json.decodeFromString(CheckInFrame.serializer(), File(frames, "${s.key}.frame.json").readText())
                    File(out, "${s.key}.run.json").writeText(play(ClaudeClient(key), s, frame).toString())
                }
            }
            else -> error("COMPARE_MODE is frames or run")
        }
    }

    private suspend fun play(model: CoachModel, s: Scenario, frame: CheckInFrame): JsonObject {
        val world = CheckInWorld(plan.program, ids, gyms = gyms, names = library.exercises.associate { it.id to it.name }, merged = library.merged)
        val coach = CheckInCoach(model, world)
        val asked = mutableListOf<JsonObject>()
        var checkIn = coach.start("${s.tile.words} ${s.line}", frame)
        var step = coach.step(checkIn)
        while (step is CheckInStep.Question && asked.size < MAX_ASKS) {
            val q = step as CheckInStep.Question
            val pick = s.keywords.firstNotNullOfOrNull { k -> q.answers.firstOrNull { it.contains(k, ignoreCase = true) } } ?: q.answers.first()
            asked += buildJsonObject {
                put("question", q.question)
                put("answers", JsonArray(q.answers.map(::JsonPrimitive)))
                put("picked", pick)
            }
            checkIn = coach.answer(checkIn, pick)
            step = coach.step(checkIn)
        }
        val replies = checkIn.turns.map { it.reply.copy(original = null) }
        return buildJsonObject {
            put("scenario", s.key)
            put("opening", checkIn.opening)
            put("ended", step::class.simpleName ?: "?")
            if (step is CheckInStep.Problems) put("problems", JsonArray(step.errors.map(::JsonPrimitive)))
            if (step is CheckInStep.Failed) put("failure", step.failure.name)
            put("asked", JsonArray(asked))
            put("replies", Json.encodeToJsonElement(ListSerializer(CoachReply.serializer()), replies))
        }
    }

    private companion object {
        const val MAX_ASKS = 8
    }
}
