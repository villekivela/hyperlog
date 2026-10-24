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
    ): CheckInFrame {
        val context = buildString {
            appendLine(intro(plan != null, gyms, lowest))
            appendLine()
            with(AskClaude) { situation(plan, sessions, library, zone, today, null, false, lastTrained, weekdays, weeklySets) }
        }
        return CheckInFrame(context.trimEnd(), CoachTools.all(plan?.program))
    }

    /** How's it going?: the Change in force, with the check-in that made it. */
    fun howItsGoing(record: ChangeRecord): String = buildString {
        append("How's it going with this Change? \"${record.change.summary}\" (id ${record.id}, accepted ${record.accepted.acceptedOn}).")
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
        val places = gyms.map { it.name } + CheckInCoach.ELSEWHERE
        appendLine(
            "- Ask which gym I'm at only when the equipment matters, with ${list(places)} as the answers. " +
                "The answer comes with that gym's equipment.",
        )
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
                "- End in words when the plan can stand, with no tool. Otherwise reply with one Change to my program (propose_change) " +
                    "or one workout for today outside the program (propose_workout). Use only the slot, day and exercise ids listed here.",
            )
            appendLine(
                "- To change my program for good (swap an exercise, add or drop one, more or fewer sets), ask which exercise and why, " +
                    "then propose a Change with the scope \"rest_of_program\".",
            )
        } else {
            appendLine(
                "- No program is running, so there is no plan to change. End in words, or with one workout for today " +
                    "(propose_workout). Use only the exercise ids listed here.",
            )
        }
        append("- Hyperlog checks a reply before I see it. If the tool's result lists problems, fix them and propose again.")
    }

    private fun list(items: List<String>) = if (items.size == 1) items.single() else items.dropLast(1).joinToString() + " and " + items.last()
}

/** What the scripted coach (debug builds only) builds its canned replies from. */
data class ScriptedWorld(
    val plan: ActiveProgram?,
    val library: List<Exercise>,
    val gyms: List<Gym>,
    val lowest: List<Pair<String, Double>>,
)
