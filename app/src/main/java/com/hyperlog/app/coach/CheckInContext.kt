package com.hyperlog.app.coach

import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.MuscleGroup
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.session.PastSessionItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a check-in through the API tells Claude (SPEC, What Claude is told), as its frame: how a
 * check-in goes, then the situation Ask Claude's prompt also carries, built by the same code. The
 * formats are the tools' schemas. Your answers, and a gym's equipment once it is answered, follow
 * as the conversation.
 */
object CheckInContext {
    fun frame(
        plan: ActiveProgram?,
        sessions: List<PastSessionItem>,
        library: List<Exercise>,
        gyms: List<Gym> = emptyList(),
        /** The focus's marks: the groups behind this week, with their sets. */
        lowest: List<Pair<String, Double>> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = plan?.today ?: LocalDate.now(zone),
        lastTrained: LocalDate? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
        weeklySets: List<WeekSets> = emptyList(),
        /** Your training days with their gyms and minutes. */
        training: com.hyperlog.app.week.WeekdayPlan = com.hyperlog.app.week.WeekdayPlan.None,
    ): CheckInFrame {
        val context = buildString {
            appendLine(intro(plan != null, gyms, lowest))
            appendLine()
            with(AskClaude) {
                situation(plan, sessions, library, zone, today, null, false, lastTrained, weekdays.ifEmpty { training.days }, weeklySets, training, gyms.associate { it.id to it.name })
            }
        }
        return CheckInFrame(context.trimEnd(), CoachTools.all(plan?.program))
    }

    /** How's it going?'s opening starts with this, so the screen can title it. */
    const val HOWS_IT_GOING = "How's it going with this Change?"

    /** How's it going?: the Change in force, with the check-in that made it. */
    fun howItsGoing(record: ChangeRecord): String = buildString {
        append("$HOWS_IT_GOING \"${record.change.summary}\" (id ${record.id}, accepted ${record.accepted.acceptedOn}).")
        record.checkIn?.let { append("\nThe check-in that made it:\n${it.text()}") }
    }

    private fun intro(program: Boolean, gyms: List<Gym>, lowest: List<Pair<String, Double>>): String = buildString {
        appendLine(
            if (program) "I train with Hyperlog, an app that runs my strength program and logs my sessions. You are my coach in a check-in."
            else "I train with Hyperlog, an app that logs my strength sessions. You are my coach in a check-in.",
        )
        appendLine("I tapped what's going on; my first message says it. Find out what the situation needs, then answer.")
        appendLine()
        appendLine("## How a check-in goes")
        appendLine(
            "- Ask through the ask tool: one question a screen, with 2 to 4 short answers to tap, and whether I may type my own. " +
                "Ask only what the situation needs and what the information below doesn't already say, at most four questions in all.",
        )
        // Four answers at most: past three gyms, the likeliest three, and the rest typed (owner, 2026-10-24).
        if (gyms.size <= MAX_GYM_ANSWERS) {
            val places = gyms.map { it.name } + CheckInCoach.ELSEWHERE
            appendLine(
                "- Ask which gym I'm at only when the equipment matters, with ${list(places)} as the answers. " +
                    "The answer comes with that gym's equipment.",
            )
        } else {
            appendLine("- My gyms: ${list(gyms.map { it.name })}.")
            appendLine(
                "- Ask which gym I'm at only when the equipment matters: offer the $MAX_GYM_ANSWERS likeliest and Elsewhere, and let me " +
                    "type my own for the rest (today's planned gym and those in my recent sessions are likeliest). Name gyms as written " +
                    "here. The answer comes with that gym's equipment.",
            )
        }
        val marks = if (lowest.isEmpty()) "" else
            " Offer the groups behind this week first: ${lowest.joinToString { (g, n) -> "$g (${formatKg(n)} ${if (n == 1.0) "set" else "sets"})" }}."
        appendLine(
            "- For a workout, ask the effort, time and focus that matter. Effort is Easy (3 or more reps in reserve, no techniques), " +
                "Normal (1–2 reps in reserve) or Hard (0–1 reps in reserve, techniques allowed). Time is the whole session with " +
                "warm-ups and rest, 45 min to 1 h 30. Focus is any of the muscle groups (${MuscleGroup.entries.joinToString { it.label }}), " +
                "named as they are here, or none for a balanced full-body day.$marks",
        )
        if (program) {
            appendLine(
                "- End in words when the plan can stand, with no tool. Otherwise reply with one Change to my program (propose_change), " +
                    "one workout for today outside the program (propose_workout) or a new program in its place (propose_program). " +
                    "Use only the slot, day and exercise ids listed here.",
            )
            appendLine(
                "- To change my program for good (swap an exercise, add or drop one, more or fewer sets), ask which exercise and why, " +
                    "then propose a Change with the scope \"rest_of_program\".",
            )
        } else {
            appendLine(
                "- No program is running, so there is no plan to change. End in words, with one workout for today " +
                    "(propose_workout), or with a new program (propose_program). Use only the exercise ids listed here.",
            )
        }
        appendLine(
            "- For a new program, offer Attach a spreadsheet, PDF or photo on a step (ask's attach) when I may have it as a file: " +
                "a program's spreadsheet (it comes as each sheet's CSV) or PDF, a printed sheet, a whiteboard, a screenshot. " +
                "Read the blocks, days, exercises, sets, rep ranges, reps in reserve and " +
                "techniques from it, and ask only what it doesn't settle; without a file, build from my answers. For an exercise " +
                "the library lacks, use the closest library exercise and list the swap; add it as a new exercise only when nothing comes close.",
        )
        append("- Hyperlog checks a reply before I see it. If the tool's result lists problems, fix them and propose again.")
    }

    /** Gyms an `ask` offers besides Elsewhere, within its four answers. */
    const val MAX_GYM_ANSWERS = 3

    private fun list(items: List<String>) = if (items.size == 1) items.single() else items.dropLast(1).joinToString() + " and " + items.last()
}

/** What the scripted coach (debug builds only) builds its canned replies from. */
data class ScriptedWorld(
    val plan: ActiveProgram?,
    val library: List<Exercise>,
    val gyms: List<Gym>,
    val lowest: List<Pair<String, Double>>,
)
