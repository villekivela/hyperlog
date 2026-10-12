package com.hyperlog.app.session

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Technique
import com.hyperlog.app.progression.Suggestion

/** One exercise in a session, with what has been logged and what was done last time. */
data class SessionExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    val lastTime: List<LoggedSet>,
    /** The weights −/+ and the pad's strip move over. */
    val ladder: WeightLadder,
    /** False while [ladder] only stands in for equipment the session doesn't know. */
    val ladderKnown: Boolean,
    /** The machine or cable station it is done on; null for free weights or until it is picked. */
    val machine: EquipmentItem? = null,
    /**
     * The session's gym doesn't know which machine this exercise is done on yet: the screen asks
     * "Which machine?" before any set, and the pad has no entry until it is answered.
     */
    val needsMachine: Boolean = false,
    /** The program slot it fills; null when added ad hoc. */
    val slot: PlannedSlot? = null,
    /** This week's sets for it from the program; null for an ad-hoc exercise, whose sets are open-ended. */
    val plan: List<PlannedSet>? = null,
    /** What progression suggests for the active row of a program exercise on screen, with why. */
    val suggestion: Suggestion? = null,
    /**
     * A machine or cable with no history on this machine yet, before its first set: the old
     * app's weight (`45 kg × 12 @ 1 RIR, 12 Sep`), shown as a hint only while a weight is picked
     * on the ladder.
     */
    val startingHint: String? = null,
    /** Techniques added to or changed on sets in this session, by set index; null takes it off. */
    val techniques: Map<Int, Technique?> = emptyMap(),
) {
    /** Every planned set is logged. An ad-hoc exercise is never done. */
    val done: Boolean get() = plan != null && sets.size >= plan.size

    /**
     * Set [index]'s technique: what a logged set was done with, else the session's change to it,
     * else the program's.
     */
    fun techniqueAt(index: Int): Technique? = when {
        index < sets.size -> sets[index].technique
        index in techniques -> techniques[index]
        else -> plan?.getOrNull(index)?.technique
    }
}

/** The running session, as it is on screen. */
data class ActiveSession(
    val id: Long,
    val startedAt: Long,
    val exercises: List<SessionExercise>,
    val currentId: Long?,
    /** What the pad holds for the active row; null until an exercise is added. */
    val entry: SetEntry?,
    val padVisible: Boolean,
    /** When rest ends (in the past once it has run out); null with no rest running. */
    val restEndsAt: Long?,
    val restTotalMs: Long,
    /** The weight check raised by the last ✓, until the entry changes or a second ✓ keeps it. */
    val weightCheck: WeightCheck? = null,
    /** Where the session is trained, with its equipment; null when no gym is set up. */
    val gym: Gym? = null,
    /** The program day this session trains; null for an ad-hoc session. */
    val day: PlannedDay? = null,
    /** What comes next in the open set (the current exercise's last one); null when no set is open. */
    val followUp: FollowUp? = null,
    /** [restEndsAt] is a myo-reps breathing pause, not rest. */
    val breathing: Boolean = false,
    /** When the running iso hold started; null with no hold timer running. */
    val holdStartedAt: Long? = null,
    /** The open set's row, to add its drops, mini-sets or hold to. */
    internal val openSetId: Long? = null,
) {
    val current: SessionExercise? get() = exercises.firstOrNull { it.id == currentId }
}

data class ExerciseSummary(val name: String, val sets: List<LoggedSet>)

data class SessionSummary(val sessionId: Long, val startedAt: Long, val finishedAt: Long, val exercises: List<ExerciseSummary>)
