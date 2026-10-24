package com.hyperlog.app.coach

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** The first step's tiles (SPEC, Coaching → Check-in), each with the sentence Claude is told. */
enum class Tile(val words: String) {
    Hurts("Something hurts."),
    ShortOnTime("I'm short on time."),
    Gym("I'm at a different gym, or none."),
    Tired("I'm tired or slept badly."),
    ChangeProgram("I want to change my program for good: swap an exercise, add or drop one, or more or fewer sets."),
    Workout("Build me a workout for today."),
    NewProgram("I want a new program."),
    SomethingElse("Something else."),
}

/** What opened a check-in. */
@Serializable
enum class Opening { Tile, EaseBackIn, HowsItGoing }

/**
 * What a check-in sends on every call, fixed when it starts so each call reads it from the cache:
 * the stable context and the tools.
 */
@Serializable
data class CheckInFrame(val context: String, val tools: List<CoachTool>)

/** What a reply is checked against, read afresh for each step: the program running and the library's ids. */
data class CheckInWorld(
    val program: Program?,
    val exercises: Set<String>,
    val inForce: Set<Long> = emptySet(),
    val gyms: List<Gym> = emptyList(),
    /** Exercise id to name, for a gym's missing exercises. */
    val names: Map<String, String> = emptyMap(),
    /** Legacy exercise id to the id kept, for hints in a workout's problems. */
    val merged: Map<String, String> = emptyMap(),
)

/**
 * One check-in (GLOSSARY): what opened it, then each of Claude's replies with what went back. It
 * is kept step by step while it runs, so it survives a tab switch and leaving the app.
 */
@Serializable
data class CheckIn(
    /** Your first answer: the tile's sentence, or Ease back in's or How's it going's. */
    val opening: String,
    val tile: Tile? = null,
    val frame: CheckInFrame,
    /** What opened it, for its title: a tile, Ease back in or How's it going?. */
    val from: Opening = Opening.Tile,
    val turns: List<Turn> = emptyList(),
    /** Why the last call has no reply; Try again calls again. */
    val failure: CheckInFailure? = null,
    /** Replies sent back with their problems since you last answered: Claude fixes them at most [MAX_FIXES] times. */
    val fixes: Int = 0,
    /** The gym answered (null: none, or [elsewhere]), where a workout from this check-in starts. */
    val gym: Long? = null,
    val elsewhere: Boolean = false,
) {
    /** The conversation so far, as Claude is sent it. */
    val messages: List<CoachMessage>
        get() = listOf(CoachMessage.User(opening)) + turns.flatMap { turn ->
            listOfNotNull(CoachMessage.Assistant(turn.reply), turn.answer?.message)
        }

    /** The last reply, answered with [answer]. */
    fun answering(answer: Answer?): CheckIn = copy(turns = turns.dropLast(1) + turns.last().copy(answer = answer))

    /** A call is due: nothing has come back since the last answer, and it didn't fail. */
    val waiting: Boolean get() = failure == null && (turns.isEmpty() || turns.last().answer != null)

    companion object {
        const val MAX_FIXES = 2
    }
}

/** One of Claude's replies, and what went back to it: your answer, or its problems. */
@Serializable
data class Turn(val reply: CoachReply, val answer: Answer? = null)

/**
 * What went back to a reply. [shown] is what you answered, as the screen and the saved check-in show
 * it; null for problems sent back by the app.
 */
@Serializable
data class Answer(val results: List<ToolResult> = emptyList(), val text: String? = null, val shown: String? = null) {
    val byYou: Boolean get() = shown != null

    val message: CoachMessage
        get() = if (results.isNotEmpty()) CoachMessage.Results(results) else CoachMessage.User(text.orEmpty())
}

/**
 * A check-in as an accepted Change keeps it: what opened it, then each question with your answer.
 * How's it going? sends it with the Change, and the Changes page shows it.
 */
@Serializable
data class SavedCheckIn(val opening: String, val steps: List<Step>) {
    @Serializable
    data class Step(val question: String, val answer: String)

    /** As Claude is told it: the opening, then each question and answer, quoted. */
    fun text(): String = buildString {
        appendLine("> ${opening.trim()}")
        steps.forEach { appendLine("Claude: ${it.question}"); appendLine("> ${it.answer}") }
    }.trimEnd()

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun encode(saved: SavedCheckIn): String = json.encodeToString(serializer(), saved)

        /** Null if it no longer reads, which it always should. */
        fun decode(text: String): SavedCheckIn? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

@Serializable
enum class CheckInFailure { NoConnection, KeyRefused, SpendLimit, Refused }

/** What a check-in's screen shows. */
sealed interface CheckInStep {
    data object Thinking : CheckInStep
    data class Question(val question: String, val answers: List<String>, val own: Boolean) : CheckInStep
    data class Words(val text: String) : CheckInStep
    data class ChangeReview(val change: Change) : CheckInStep
    data class WorkoutReview(val workout: Workout) : CheckInStep
    /** A reply still wrong after Claude's fixes: its problems, with Try again. */
    data class Problems(val errors: List<String>) : CheckInStep
    data class Failed(val failure: CheckInFailure) : CheckInStep
}

/**
 * Runs check-ins against [model] (ADR 0005): each call sends the frame and the conversation; a
 * reply is read and checked against [world]; one with problems goes back to Claude as the tool's
 * error, at most [CheckIn.MAX_FIXES] times, before you see them.
 */
class CheckInCoach(private val model: CoachModel, private val world: CheckInWorld) {
    suspend fun start(tile: Tile, frame: CheckInFrame): CheckIn = run(CheckIn(tile.words, tile, frame))

    suspend fun start(opening: String, frame: CheckInFrame, from: Opening = Opening.Tile): CheckIn = run(CheckIn(opening, null, frame, from))

    /** Answers the question showing with [text], one of its answers or your own words, and asks Claude on. */
    suspend fun answer(checkIn: CheckIn, text: String): CheckIn = run(answered(checkIn, text))

    /** [checkIn] with [text] as the answer to the question showing, before the call. */
    fun answered(checkIn: CheckIn, text: String): CheckIn {
        val turn = checkIn.turns.lastOrNull() ?: return checkIn
        val call = turn.reply.toolCalls.firstOrNull { it.name == CoachTools.ASK } ?: return checkIn
        // A gym answered goes with its equipment, and is where a workout from this check-in starts.
        val gym = world.gyms.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
        val elsewhere = gym == null && text.trim().equals(ELSEWHERE, ignoreCase = true)
        val content = when {
            gym != null -> text + "\n\n" + buildString { with(AskClaude) { gym(gym, world.names) } }.trimEnd()
            elsewhere -> "$text\n\n$ELSEWHERE_TOLD"
            else -> text
        }
        val results = turn.reply.toolCalls.map { if (it === call) ToolResult(it.id, content) else ToolResult(it.id, ONE_AT_A_TIME) }
        val answered = checkIn.answering(Answer(results, shown = text)).copy(fixes = 0)
        return if (gym != null || elsewhere) answered.copy(gym = gym?.id, elsewhere = elsewhere) else answered
    }

    /** Tell me more, after an answer in words: Claude goes on. */
    suspend fun tellMeMore(checkIn: CheckIn): CheckIn = run(toldMore(checkIn))

    /** [checkIn] with Tell me more as the answer to its words, before the call. */
    fun toldMore(checkIn: CheckIn): CheckIn {
        val turn = checkIn.turns.lastOrNull()?.takeIf { it.answer == null && it.reply.toolCalls.isEmpty() } ?: return checkIn
        val answer = Answer(text = TELL_ME_MORE, shown = TELL_ME_MORE)
        return checkIn.answering(answer).copy(fixes = 0)
    }

    /**
     * Back: the step you last answered shows again, to change its answer, and anything after it is
     * dropped, a call under way or failed included. Null from the first step: back to the tiles.
     */
    fun back(checkIn: CheckIn): CheckIn? {
        val last = checkIn.turns.indexOfLast { it.answer?.byYou == true }
        if (last < 0) return null
        val turns = checkIn.turns.take(last + 1)
        return checkIn.copy(turns = turns.dropLast(1) + turns.last().copy(answer = null), failure = null, fixes = 0)
    }

    /** Try again: a failed call is made again; a reply's problems go back to Claude once more, with its fixes counted afresh. */
    suspend fun tryAgain(checkIn: CheckIn): CheckIn = run(retried(checkIn))

    /** [checkIn] ready for Try again's call. */
    fun retried(checkIn: CheckIn): CheckIn {
        if (checkIn.failure != null) return checkIn.copy(failure = null)
        val read = checkIn.turns.lastOrNull()?.takeIf { it.answer == null }?.let { read(it.reply) } as? Read.Invalid ?: return checkIn
        return sendBack(checkIn, read.errors).copy(fixes = 0)
    }

    /** What the screen shows for [checkIn]. */
    fun step(checkIn: CheckIn): CheckInStep {
        checkIn.failure?.let { return CheckInStep.Failed(it) }
        if (checkIn.waiting) return CheckInStep.Thinking
        return when (val read = read(checkIn.turns.last().reply)) {
            is Read.Valid -> read.step
            is Read.Invalid -> CheckInStep.Problems(read.errors)
        }
    }

    /** Calls Claude while the check-in waits for a reply, sending a reply's problems back until it's right or out of fixes. */
    suspend fun run(checkIn: CheckIn): CheckIn {
        var current = checkIn
        while (true) {
            if (current.waiting) {
                val reply = try {
                    model.reply(CoachRequest(current.frame.context, current.frame.tools, current.messages))
                } catch (e: CoachError) {
                    return current.copy(failure = failure(e))
                }
                current = current.copy(turns = current.turns + Turn(reply))
            }
            val read = read(current.turns.last().reply)
            if (read !is Read.Invalid || current.fixes >= CheckIn.MAX_FIXES) return current
            current = sendBack(current, read.errors).copy(fixes = current.fixes + 1)
        }
    }

    /** The last reply's problems go back to Claude as its tool's error. */
    private fun sendBack(checkIn: CheckIn, errors: List<String>): CheckIn {
        val turn = checkIn.turns.last()
        val problems = errors.joinToString("\n")
        val answer = if (turn.reply.toolCalls.isEmpty()) {
            Answer(text = problems)
        } else {
            // The call that was read gets the problems; any other, that only one is used.
            val read = used(turn.reply)
            Answer(turn.reply.toolCalls.map { if (it === read) ToolResult(it.id, problems, isError = true) else ToolResult(it.id, ONE_AT_A_TIME) })
        }
        return checkIn.answering(answer)
    }

    private fun failure(e: CoachError) = when (e) {
        is CoachError.NoConnection -> CheckInFailure.NoConnection
        is CoachError.KeyRefused -> CheckInFailure.KeyRefused
        is CoachError.SpendLimit -> CheckInFailure.SpendLimit
        is CoachError.Refused -> CheckInFailure.Refused
    }

    private sealed interface Read {
        data class Valid(val step: CheckInStep) : Read
        data class Invalid(val errors: List<String>) : Read
    }

    /** A reply as a step: a proposal first, then a question; words when there is no call. */
    /** The call a reply is read by: a proposal before a question. */
    private fun used(reply: CoachReply): ToolCall? = reply.toolCalls.firstOrNull { it.name != CoachTools.ASK } ?: reply.toolCalls.firstOrNull()

    private fun read(reply: CoachReply): Read {
        val call = used(reply)
            ?: return if (reply.text.isBlank()) Read.Invalid(listOf(EMPTY)) else Read.Valid(CheckInStep.Words(reply.text.trim()))
        return when (call.name) {
            CoachTools.ASK -> ask(call.input)
            CoachTools.PROPOSE_CHANGE -> change(call.input)
            CoachTools.PROPOSE_WORKOUT -> workout(call.input)
            else -> Read.Invalid(listOf("${call.name}: no such tool"))
        }
    }

    /** A Change, checked as a paste is. */
    private fun change(input: JsonObject): Read {
        val program = world.program ?: return Read.Invalid(listOf(AskClaude.NO_PROGRAM))
        return when (val result = ChangeValidator.validate(input.toString(), program, world.exercises, world.inForce)) {
            is ChangeValidator.Result.Valid -> Read.Valid(CheckInStep.ChangeReview(result.change))
            is ChangeValidator.Result.Invalid -> Read.Invalid(result.errors)
        }
    }

    /** A workout, checked as a paste is: the tool's input is what `workout` holds. */
    private fun workout(input: JsonObject): Read {
        val json = buildJsonObject { put("workout", input) }.toString()
        return when (val result = ProgramParser.workout(json, world.exercises, world.merged)) {
            is ProgramParser.WorkoutResult.Valid -> Read.Valid(CheckInStep.WorkoutReview(result.workout))
            is ProgramParser.WorkoutResult.Invalid -> Read.Invalid(result.errors)
        }
    }

    companion object {
        /**
         * The check-in as an accepted Change keeps it: the opening, then each of Claude's questions
         * with your answer. Claude's words and Tell me more aren't questions, so they stay out.
         */
        fun saved(checkIn: CheckIn) = SavedCheckIn(
            checkIn.opening,
            checkIn.turns.mapNotNull { turn ->
                val shown = turn.answer?.shown ?: return@mapNotNull null
                val call = turn.reply.toolCalls.firstOrNull { it.name == CoachTools.ASK } ?: return@mapNotNull null
                ((ask(call.input) as? Read.Valid)?.step as? CheckInStep.Question)?.let { SavedCheckIn.Step(it.question, shown) }
            },
        )

        private fun ask(input: JsonObject): Read {
            val question = (input["question"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val answers = (input["answers"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            val own = (input["own"] as? JsonPrimitive)?.booleanOrNull ?: true
            val errors = buildList {
                if (question.isEmpty()) add("question: a question is required")
                if (answers.size !in 2..4) add("answers: give 2 to 4 answers")
                if (answers.any { it.isEmpty() }) add("answers: an answer is blank")
            }
            return if (errors.isEmpty()) Read.Valid(CheckInStep.Question(question, answers, own)) else Read.Invalid(errors)
        }

        /** What goes back to a call that wasn't the one used: one question or reply at a time. */
        const val ONE_AT_A_TIME = "Not shown: Hyperlog uses one question or reply at a time. Ask again if it still matters."

        /** The gym answer for somewhere not set up in Hyperlog. */
        const val ELSEWHERE = "Elsewhere"

        const val ELSEWHERE_TOLD = "I'm not at any of my gyms. Ask what equipment is there if it matters, and plan only with that."

        /** What Tell me more says to Claude. */
        const val TELL_ME_MORE = "Tell me more."

        const val EMPTY = "an empty reply: ask with the ask tool, answer in words, or propose a reply"
    }
}
