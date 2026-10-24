package com.hyperlog.app.session

import com.hyperlog.app.domain.countingSets
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PinLoad
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
import com.hyperlog.app.progression.Record
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
    /** Its machine's ladder is an unconfirmed guess and nothing is logged yet: the screen asks for the stack's labels first. */
    val needsLadder: Boolean = false,
    /** The stack the weights typed on a guessed machine show, offered once (`Looks like 10 lb steps`); null when none. */
    val ladderOffer: LadderSpec.PinStack? = null,
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
    /** Its warm-ups typed by hand in this session, by index; the others follow the ramp. */
    val typedWarmups: Map<Int, WarmupSet> = emptyMap(),
    /** The session's gym's plates for the pin, for a [machine] that is a pin stack. */
    val pinPlates: List<Double> = emptyList(),
    /** The record each of [sets] makes, by index (SPEC, Records); null for none. */
    val records: List<Record?> = emptyList(),
) {
    /** Its sets with their records, as summaries show them. */
    val summary: ExerciseSummary get() = ExerciseSummary(exercise.name, sets, records)

    /** How to load [kg] on a pin-stack [machine] (`pin 40 + 2.5 on the pin`); null for any other equipment or a weight off it. */
    fun pinLoad(kg: Double): PinLoad? = (machine?.ladder as? LadderSpec.PinStack)?.load(kg, pinPlates)

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
    /** The program day or workout this session trains; null for an ad-hoc session. */
    val day: PlannedDay? = null,
    /** The workout this session trains, outside the program; null otherwise. */
    val workoutId: Long? = null,
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
    /** The warm-up on the pad, typed like a set; null when the pad is on the set to do. [entry] stays the set to do. */
    val warmupEdit: WarmupEdit? = null,
    /**
     * The set to do's entry as it was when the pad last left its weight: the warm-ups ramp from it
     * while a weight is being typed, so they don't follow each keystroke.
     */
    internal val rampEntry: SetEntry? = null,
    /** The set logged most recently in the session, by session exercise id and set index; null with none. Its check takes it back. */
    val latestSet: Pair<Long, Int>? = null,
) {
    val current: SessionExercise? get() = exercises.firstOrNull { it.id == currentId }

    /** The exercise on screen: a finished one being looked at, else [current]. */
    val shown: SessionExercise? get() = exercises.firstOrNull { it.id == viewingId } ?: current

    /** Logged rows can be tapped to correct them, except while a set is open for its drops, mini-sets or hold. */
    val correctable: Boolean get() = followUp == null

    /**
     * The warm-ups before [current]'s first work set, ramped from the weight typed for it and the reps planned
     * (or, for an ad-hoc exercise, last time's). A warm-up typed by hand stays as typed; the
     * others follow the ramp. They go once the first work set is logged.
     */
    val warmups: List<WarmupSet>
        get() = Warmups.withTyped(ramp, current?.typedWarmups.orEmpty())

    /** [warmups] as the ramp gives them, before any typed by hand. */
    internal val ramp: List<WarmupSet>
        get() {
            val current = current ?: return emptyList()
            if (current.sets.isNotEmpty() || current.needsMachine || current.needsLadder || followUp != null) return emptyList()
            // Not keystroke by keystroke: while the weight is typed, the ramp stays where it was.
            val from = if (entry?.field == com.hyperlog.app.domain.Field.Weight) rampEntry else entry
            val kg = from?.weightKg ?: return emptyList()
            // The reps are the plan's, or last time's for an ad-hoc exercise; typing them changes nothing.
            val reps = current.prescription?.firstOrNull()?.reps?.max ?: current.lastTime.countingSets().firstOrNull()?.fullReps
            return Warmups.ramp(kg, current.exercise, reps, current.ladder, plateLoaded = current.plates != null)
        }

    /**
     * The check on set [index] of [exercise] takes it back: it is the set logged last in the
     * session, nothing is being corrected, and none of its drops, mini-sets or hold is logged yet.
     */
    fun undoable(exercise: SessionExercise, index: Int): Boolean =
        latestSet == (exercise.id to index) && correction == null && warmupEdit == null &&
            exercise.sets.getOrNull(index)?.extensions?.isEmpty() == true

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

/** Warm-up [index] of session exercise [sessionExerciseId] on the pad, as typed so far. */
data class WarmupEdit(val sessionExerciseId: Long, val index: Int, val entry: SetEntry)

/** An exercise's sets in a session, with the record each makes ([SessionExercise.records]). */
data class ExerciseSummary(val name: String, val sets: List<LoggedSet>, val records: List<Record?> = emptyList())

data class SessionSummary(val sessionId: Long, val startedAt: Long, val finishedAt: Long, val exercises: List<ExerciseSummary>)
