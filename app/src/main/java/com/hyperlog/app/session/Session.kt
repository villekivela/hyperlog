package com.hyperlog.app.session

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.WarmupSet
import com.hyperlog.app.domain.Warmups
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.SetPrescription
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
    val prescription: List<SetPrescription>? = null,
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
    /** The barbell plates or plate-loaded machine it is loaded with, for the plate calculator; null otherwise. */
    val plates: LadderSpec.Plates? = null,
    /** How many of its warm-ups are ticked off. */
    val warmupsDone: Int = 0,
) {
    /** The program's exercise for its slot, when it was swapped in the session or by a Change. */
    val inPlaceOf: String? get() = slot?.let { s -> (s.inPlaceOf ?: s.exercise).takeIf { it != exercise.id } }

    /**
     * The [plates] load a plate-loaded machine (a leg press, a sled, a Smith machine), whose
     * starting weight is their base, rather than a bar.
     */
    val platesOnMachine: Boolean get() = plates != null && exercise.equipment !in BarEquipment

        /** Every planned set is logged. An ad-hoc exercise is never done. */
    val done: Boolean get() = prescription != null && sets.size >= prescription.size

    /** Planned set [index] can still change in the session: it is planned and not logged yet. */
    fun editable(index: Int): Boolean = prescription != null && index >= sets.size && index < prescription.size

    /** Planned set [index] can be taken out: one set always stays, and so does one that others back off from. */
    fun removable(index: Int): Boolean = editable(index) && prescription!!.size > 1 && prescription.none { it.backoff?.from == index }

    /**
     * Set [index]'s technique: what a logged set was done with, else the session's change to it,
     * else the program's.
     */
    fun techniqueAt(index: Int): Technique? = when {
        index < sets.size -> sets[index].technique
        index in techniques -> techniques[index]
        else -> prescription?.getOrNull(index)?.technique
    }
}

/** The equipment loaded with plates on a bar: the barbell and the specialty bars. */
private val BarEquipment = setOf(Equipment.Barbell, Equipment.EzBar, Equipment.TrapBar, Equipment.SafetyBar)

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
    /** A finished exercise opened from the session list, shown in place of [current]; null when none. */
    val viewingId: Long? = null,
    /** The logged row loaded into the pad to be corrected; null when none. [entry] stays the set to do. */
    val correction: Correction? = null,
    /** The set logged last (session exercise id and set index), which **Fix** loads; null when none. */
    val lastLogged: Pair<Long, Int>? = null,
) {
    val current: SessionExercise? get() = exercises.firstOrNull { it.id == currentId }

    /** The exercise on screen: a finished one being looked at, else [current]. */
    val shown: SessionExercise? get() = exercises.firstOrNull { it.id == viewingId } ?: current

    /** Logged rows can be tapped to correct them, except while a set is open for its drops, mini-sets or hold. */
    val correctable: Boolean get() = followUp == null

    /**
     * The warm-ups before [current]'s first work set, ramped from the weight and reps planned or
     * typed for it (the plan's top reps while none are typed). They go once the first work set is logged.
     */
    val warmups: List<WarmupSet>
        get() {
            val current = current ?: return emptyList()
            if (current.sets.isNotEmpty() || current.needsMachine || followUp != null) return emptyList()
            val kg = entry?.weightKg ?: return emptyList()
            val reps = entry.full.toIntOrNull()?.takeIf { it > 0 } ?: current.prescription?.firstOrNull()?.reps?.max
            return Warmups.ramp(kg, current.exercise, reps, current.ladder, plateLoaded = current.plates != null)
        }

    /** [current]'s superset partner, or null for straight sets. */
    val partner: SessionExercise? get() = current?.let { SessionOrder.partner(exercises, it) }

    /**
     * What comes up once the exercise on screen (with its superset partner) is done: the first
     * exercise with planned sets left, in session order, so a skipped one comes back first. In an
     * ad-hoc session, the one after it. Null when nothing follows.
     */
    val next: SessionExercise?
        get() {
            val current = current ?: return null
            val group = SessionOrder.group(exercises, current).map { it.id }.toSet()
            val others = exercises.filter { it.id !in group }
            return others.firstOrNull { it.prescription != null && !it.done }
                ?: exercises.drop(exercises.indexOf(current) + 1).firstOrNull { it.id !in group && !it.done }
        }
}

data class ExerciseSummary(val name: String, val sets: List<LoggedSet>)

data class SessionSummary(val sessionId: Long, val startedAt: Long, val finishedAt: Long, val exercises: List<ExerciseSummary>)
