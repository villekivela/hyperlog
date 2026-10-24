package com.hyperlog.app.coach

import com.hyperlog.app.program.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A debug build offers the scripted coach in Settings. */
internal object BuildCoach {
    const val SCRIPTED = true

    fun scripted(world: suspend () -> ScriptedWorld): CoachModel? = ScriptedCoach(world)
}

/**
 * The scripted coach (GLOSSARY; SPEC, With a Claude key → Testable without a key): canned
 * check-ins for every tile, with no key and no network, so the wizard can be tried on the phone
 * and by the tester. It answers like Claude through the same tools, from the program, library and
 * gyms of [world], so its Changes, workouts and programs pass the validators. Change my program
 * proposes a wrong slot first, to show the fix; New program offers a file, then proposes a program
 * with a swap and a new exercise.
 */
class ScriptedCoach(private val world: suspend () -> ScriptedWorld) : CoachModel {
    private var calls = 0

    override suspend fun reply(request: CoachRequest, progress: (CoachProgress) -> Unit): CoachReply {
        // Long enough to see Thinking…, timed off the main thread as a call would be; then what it writes, as a stream says.
        progress(CoachProgress.Thinking)
        withContext(Dispatchers.Default) { delay(THINKING_MS) }
        val reply = scripted(request)
        val call = reply.toolCalls.firstOrNull { it.name != CoachTools.ASK }
        if (call != null || reply.toolCalls.isEmpty()) {
            progress(if (call != null) CoachProgress.Writing(call.name) else CoachProgress.Answering)
            withContext(Dispatchers.Default) { delay(WRITING_MS) }
        }
        return reply
    }

    private suspend fun scripted(request: CoachRequest): CoachReply {
        val world = world()
        val opening = (request.messages.first() as CoachMessage.User).text
        val tile = Tile.entries.firstOrNull { it.words == opening } ?: Tile.SomethingElse
        val step = request.messages.count { it is CoachMessage.Assistant }
        val last = request.messages.last()
        if (last is CoachMessage.Results && last.results.any { it.isError }) return fixed(tile, world)
        if (last is CoachMessage.User && last.text == CheckInCoach.TELL_ME_MORE) return words(MORE)
        val answers = request.messages.filterIsInstance<CoachMessage.Results>().map { it.results.first().content.lineSequence().first() }
        return script(tile, world, answers).getOrElse(step) { words("That's all I have. Train as planned.") }
    }

    private fun script(tile: Tile, world: ScriptedWorld, answers: List<String>): List<CoachReply> {
        val plan = world.plan
        val next = plan?.next ?: plan?.program?.plannedDay(Position(1, 0))
        return when (tile) {
            Tile.Hurts -> listOf(
                ask("Where does it hurt?", "Hands or wrists", "Elbow", "Shoulder", "Knee"),
                ask("Does it hurt when you press, or only when you grip?", "Only gripping", "Pressing too"),
                plan?.let { change(ChangeFormat.example(it).copy(summary = "For a week: an easier-on-the-joints alternative and one more rep in reserve.")) }
                    ?: words("Train around it this week: skip what hurts, keep the rest easy, and see how it feels next session."),
            )
            Tile.ShortOnTime -> listOf(
                ask("How long do you have, warm-ups and rest included?", "30 min", "45 min", "1 h"),
                next?.slots?.lastOrNull()?.let { last ->
                    change(
                        Change(
                            "Today only: leave out the last exercise to fit ${answers.firstOrNull() ?: "the time"}.",
                            Scope.Dates(plan!!.today, plan.today),
                            listOf(Operation.Remove(last.id, reason = "short on time")),
                        ),
                    )
                } ?: words("Do the first two exercises and stop there. Short sessions still count."),
            )
            Tile.Gym -> listOfNotNull(
                // With no gyms set up there is nothing to pick from.
                if (world.gyms.isEmpty()) null
                else ask("Where are you training today?", *(world.gyms.map { it.name }.take(3) + CheckInCoach.ELSEWHERE).toTypedArray(), own = false),
                if (answers.firstOrNull() == CheckInCoach.ELSEWHERE || world.gyms.isEmpty()) {
                    ask("What's there?", "Dumbbells only", "Machines and cables", "Nothing: bodyweight")
                } else null,
                workout(world, "A day for the equipment there."),
            )
            Tile.Tired -> listOf(
                ask("How did you sleep?", "Under 5 hours", "5 to 7 hours", "Fine, just tired"),
                words(
                    "Train as planned, but stop each set a rep earlier than written. If the first work set feels heavy, keep the " +
                        "weight and drop the last set of each exercise.",
                ),
            )
            Tile.ChangeProgram -> {
                val names = world.library.associate { it.id to it.name }
                val slots = next?.slots.orEmpty().take(3).map { names[it.exercise] ?: it.exercise }
                listOf(
                    ask("Which exercise do you want to change?", *(slots.ifEmpty { listOf("Bench press", "Rows") }).toTypedArray()),
                    ask("Why change it?", "It hurts", "No machine for it", "I don't like it"),
                    // A wrong slot first, so the fix shows; [fixed] sends the right one.
                    plan?.let { call(CoachTools.PROPOSE_CHANGE, json(Change("Swap for good", Scope.RestOfProgram, listOf(Operation.Swap("no_such_slot", "no_such_exercise"))))) }
                        ?: words("No program is running, so there's nothing to change. Build a workout, or import a program from Program."),
                )
            }
            Tile.Workout -> listOf(
                ask("How hard?", "Easy", "Normal", "Hard", own = false),
                ask("How long, warm-ups and rest included?", "45 min", "1 h", "1 h 15", "1 h 30", own = false),
                ask("What to focus on?", *(world.lowest.map { it.first }.ifEmpty { listOf("Back", "Legs") }.take(3) + "Balanced full body").toTypedArray(), own = true),
                workout(world, "Built from your answers, with what's behind this week first."),
            )
            Tile.NewProgram -> listOf(
                ask("Do you have the program as a file?", "Build it from my answers", "I'll describe it", attach = true),
                ask("How many days a week can you train?", "2", "3", "4"),
                program(world),
            )
            Tile.SomethingElse -> listOf(
                ask("What's on your mind?", "My sessions run long", "I'm losing motivation"),
                words("Keep sessions to the plan's exercises and time the rests. Short, regular sessions beat long, rare ones."),
            )
        }
    }

    /** The reply after its problems came back: Change my program's swap, the right way. */
    private fun fixed(tile: Tile, world: ScriptedWorld): CoachReply {
        val plan = world.plan ?: return words("Sorry, that didn't work.")
        val day = plan.next ?: plan.program.plannedDay(Position(1, 0))
        val slot = day?.slots?.firstOrNull { it.alternatives.isNotEmpty() }
            ?: return words("Nothing in this program has an alternative to swap to. Keep it, and tell me more if it hurts.")
        val summary = if (tile == Tile.ChangeProgram) "For good: ${slot.alternatives.first()} in place of ${slot.exercise}." else "Swap for good."
        return change(Change(summary, Scope.RestOfProgram, listOf(Operation.Swap(slot.id, slot.alternatives.first(), reason = "your call"))))
    }

    private fun workout(world: ScriptedWorld, summary: String): CoachReply {
        val example = WorkoutFormat.example(world.library).copy(summary = summary)
        return call(CoachTools.PROPOSE_WORKOUT, Json.parseToJsonElement(WorkoutJson.encode(example)).jsonObject.getValue("workout").jsonObject)
    }

    /**
     * The format's example program from the library, with one swap (a seal row done as the row the
     * library has) and one new exercise, a drag curl, as the curl's alternative.
     */
    private fun program(world: ScriptedWorld): CoachReply {
        val file = ProgramFormat.example(world.library)
        // A program started before added the drag curl: this one uses it.
        val known = world.library.firstOrNull { it.name.equals(DRAG_CURL, ignoreCase = true) }
        val taken = world.library.map { it.id }.toSet()
        val drag = known?.id ?: generateSequence(1) { it + 1 }.map { if (it == 1) "barbell_drag_curl" else "barbell_drag_curl_$it" }.first { it !in taken }
        val curl = (file.toString().substringAfter("\"id\":\"curl\",\"exercise\":\"").substringBefore('"'))
        val program = file.toString().replace(
            "\"id\":\"curl\",\"exercise\":\"$curl\"",
            "\"id\":\"curl\",\"exercise\":\"$curl\",\"alternatives\":[\"$drag\"]",
        )
        val row = file.toString().substringAfter("\"id\":\"row\",\"exercise\":\"").substringBefore('"')
        return call(
            CoachTools.PROPOSE_PROGRAM,
            buildJsonObject {
                val head = mapOf(
                    "title" to JsonPrimitive("Scripted full body, 2 days"),
                    "author" to JsonPrimitive("Claude"),
                    "description" to JsonPrimitive("Two full-body days a week: a squat or hinge, a press and a row, then arms."),
                )
                put("program", JsonObject(Json.parseToJsonElement(program).jsonObject + head))
                putJsonArray("swaps") { add(buildJsonObject { put("from", "Seal Row"); put("to", row) }) }
                putJsonArray("newExercises") {
                    if (known == null) add(
                        buildJsonObject {
                            put("id", drag)
                            put("name", DRAG_CURL)
                            put("equipment", "barbell")
                            put("pattern", "elbow_flexion")
                            put("grip", "high")
                            putJsonArray("muscles") { add(JsonPrimitive("Biceps")) }
                            putJsonArray("secondary") { add(JsonPrimitive("Brachialis")) }
                        },
                    )
                }
            },
        )
    }

    private fun change(change: Change) = call(CoachTools.PROPOSE_CHANGE, json(change))

    private fun json(change: Change) = Json.parseToJsonElement(ChangeJson.encode(change)).jsonObject

    private fun ask(question: String, vararg answers: String, own: Boolean = true, attach: Boolean = false) = call(
        CoachTools.ASK,
        buildJsonObject {
            put("question", question)
            putJsonArray("answers") { answers.forEach { add(JsonPrimitive(it)) } }
            put("own", own)
            put("attach", attach)
        },
    )

    private fun call(name: String, input: JsonObject) = CoachReply("", listOf(ToolCall("scripted-${++calls}", name, input)))

    private fun words(text: String) = CoachReply(text)

    private companion object {
        const val THINKING_MS = 900L
        const val WRITING_MS = 900L
        const val DRAG_CURL = "Barbell Drag Curl"
        const val MORE = "A short night mostly costs you reps at the end of a set, not strength at the start. Keep the weights, cut a set, and sleep in tonight."
    }
}
