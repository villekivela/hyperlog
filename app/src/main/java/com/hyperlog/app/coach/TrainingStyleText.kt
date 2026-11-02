package com.hyperlog.app.coach

import com.hyperlog.app.library.MuscleGroup
import com.hyperlog.app.program.Program

/**
 * The training style as Claude reads it (SPEC, What Claude is told → The training style): the
 * training targets and About me as structured lines, How I train as its text, and how to write by
 * them. Built once, into the situation both the API and Ask Claude's prompt carry.
 */
internal object TrainingStyleText {
    fun StringBuilder.trainingStyle(style: TrainingStyle, names: Map<String, String>, program: Program? = null) {
        val t = style.targets
        appendLine("## My training style")
        appendLine("- Goal: ${goal(t.goal)}, with my lift goals on top")
        appendLine("- Volume: ${t.volume} work sets per muscle a week, ${acrossBlock(t.acrossBlock, t.volume)}")
        appendLine("- Frequency: each muscle ${frequency(t.frequency)} a week")
        appendLine("- How hard: last set 0–1 RIR, earlier sets 1–2 RIR")
        appendLine("- Failure: ${failure(t.failure, t.calibration)}")
        appendLine("- Calibration: ${calibration(t.calibration)}")
        appendLine("- Rep ranges: compounds ${t.compound}, isolation ${t.isolation}")
        aboutMe(style.aboutMe, names)
        howITrain(style.howITrain, style.sentences)
        // The running program's own volume, when it has one, sets the ranges.
        val volume = program?.targets?.volume ?: t.volume
        appendLine("## Writing by my training style")
        appendLine(
            "- Write new programs, Changes and workouts by my training style. The training targets are standing: a new program " +
                "starts from them. Only when I ask for other ones for one program (\"more volume this time\"), write them into that " +
                "program's \"targets\"; my standing ones stay as they are.",
        )
        appendLine(
            "- For a new program, ask \"Bring anything up this program?\": offer None and up to three of the muscle groups " +
                "(${MuscleGroup.entries.joinToString { it.label }}) likeliest for me, and let me type my own for another group or for two. " +
                "Write the answer, at most two groups, into the program's \"focus\". A focused group's muscles get weekly sets in the " +
                "raised range (${volume.raised()}), go first in their sessions and are trained more often; the other muscles drop to " +
                "the maintenance range (${volume.maintenance()}). The raised range runs from the top of the volume range to half as " +
                "much again, the maintenance range from half the volume range's bottom to its bottom.",
        )
        appendLine("- Changes and workouts keep the how hard, failure and calibration rules.")
        val example = if (program?.focus.isNullOrEmpty()) {
            "Quads drop to ${volume.low - 2} sets a week, under your $volume."
        } else {
            val maintenance = volume.maintenance()
            "Quads drop to ${maintenance.low - 1} sets a week, under the maintenance range of $maintenance."
        }
        appendLine(
            "- When a Change takes a muscle's weekly sets out of its range (in a program with a focus, the raised range or the " +
                "maintenance range), say so in its summary: \"$example\"",
        )
    }

    /** The running program's focus and own training targets, with the program. */
    fun StringBuilder.programStyle(program: Program, standing: TrainingTargets) {
        val volume = program.targets.volume ?: standing.volume
        if (program.focus.isNotEmpty()) {
            appendLine(
                "Program focus: ${program.focus.joinToString { it.label }} (their muscles ${volume.raised()} sets a week, " +
                    "the rest ${volume.maintenance()})",
            )
        }
        val own = program.targets
        own.goal?.let { appendLine("Goal for this program: ${goal(it)}") }
        own.volume?.let { appendLine("Volume for this program: $it") }
        own.acrossBlock?.let { appendLine("Across a block for this program: ${acrossBlock(it, volume)}") }
        own.frequency?.let { appendLine("Frequency for this program: each muscle ${frequency(it)} a week") }
        own.failure?.let { appendLine("Failure for this program: ${failure(it, own.calibration ?: standing.calibration)}") }
        own.calibration?.let { appendLine("Calibration for this program: ${if (it) "on" else "off"}") }
        own.compound?.let { appendLine("Compound reps for this program: $it") }
        own.isolation?.let { appendLine("Isolation reps for this program: $it") }
    }

    private fun StringBuilder.aboutMe(me: AboutMe, names: Map<String, String>) {
        val lines = buildList {
            me.age?.let { add("Age: ${it.label}") }
            val where = listOfNotNull(me.progress?.label?.lowercase(), "coming back from a break".takeIf { me.comingBack })
            if (where.isNotEmpty()) add("Where I am now: ${where.joinToString("; ")}")
            me.aches.forEach { (area, note) -> add("Aches: ${area.label}${if (note.isBlank()) "" else ": ${note.trim()}"}") }
            if (me.avoid.isNotEmpty()) add("Avoid: ${exercises(me.avoid, names)}")
            if (me.favour.isNotEmpty()) add("Favour: ${exercises(me.favour, names)}")
            me.anythingElse?.takeIf { it.isNotBlank() }?.let { add("Anything else: ${it.trim()}") }
        }
        if (lines.isEmpty()) return
        appendLine("## About me")
        lines.forEach { appendLine("- $it") }
    }

    /** Each principle Claude gets (the fixed ones always), as its full sentence, then your own line. */
    private fun StringBuilder.howITrain(how: HowITrain, sentences: Map<Principle, String>) {
        val lines = Principle.entries.filter(how::on).mapNotNull(sentences::get) +
            listOfNotNull(how.anythingElse?.takeIf { it.isNotBlank() }?.let { "Anything else: ${it.trim()}" })
        if (lines.isEmpty()) return
        appendLine("## How I train")
        lines.forEach { appendLine("- $it") }
    }

    private fun exercises(ids: List<String>, names: Map<String, String>) = ids.joinToString { id -> names[id]?.let { "$id ($it)" } ?: id }

    private fun goal(goal: TrainingGoal) = when (goal) {
        TrainingGoal.Hypertrophy -> "hypertrophy"
        TrainingGoal.Strength -> "strength"
        TrainingGoal.Both -> "hypertrophy and strength"
    }

    private fun acrossBlock(across: AcrossBlock, volume: NumberRange) = when (across) {
        AcrossBlock.Steady -> "steady across a block"
        AcrossBlock.Rising ->
            "rising across a block: near ${volume.low} in its first week, adding sets each week toward ${volume.high}, then a deload"
    }

    private fun frequency(frequency: Frequency) = when (frequency) {
        Frequency.Once -> "once"
        Frequency.Twice -> "twice"
        Frequency.TwoToThree -> "2–3 times"
        Frequency.ThreeOrMore -> "3 or more times"
    }

    /** Which lifts go to failure; with [calibration] on, Never still leaves room for the calibration sets. */
    private fun failure(failure: FailureRule, calibration: Boolean) = when (failure) {
        FailureRule.IsolationAndMachines -> "isolation exercises and machine, cable and Smith machine compounds only"
        FailureRule.Never ->
            if (calibration) "never, apart from calibration sets; every other set stops at 1 RIR or more"
            else "never; every set stops at 1 RIR or more"
        FailureRule.AnyLift -> "any lift's last set may go to 0 RIR"
    }

    private fun calibration(on: Boolean) =
        if (on) "in each block's last week, unless it is a deload, the last set of each exercise safe to fail goes to 0 RIR; never a barbell squat, bench or deadlift"
        else "off"
}
