package com.hyperlog.app.coach

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.TechniqueKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where it hurts, for the situation "pain in an area". */
enum class Area(val label: String) {
    Hands("Hands"),
    Shoulder("Shoulder"),
    Elbow("Elbow"),
    Knee("Knee"),
    LowerBack("Lower back"),
}

/** Why the plan can't be followed as written (GLOSSARY, Situation). */
sealed interface Situation {
    /** Missed days or a week; how many comes from the sessions. */
    data object TimeOff : Situation

    /**
     * Pain in [area]. [worseAfter] names the slot whose exercise left the hands worse the next
     * morning, which then moves to its no-grip swap (the pain rule); null for the pain itself.
     */
    data class Pain(val area: Area, val worseAfter: String? = null) : Situation

    /** Training at home: dumbbells and bodyweight. */
    data object NoGym : Situation

    data class ShortOnTime(val minutes: Int) : Situation

    /** Bad sleep or stress: today's volume and effort come down. */
    data object PoorRecovery : Situation

    /** The constraints the situation brings with it; you can add or drop any. */
    fun constraints(): Constraints = when (this) {
        is Pain -> when (area) {
            Area.Hands -> Constraints(noGrip = true)
            Area.Shoulder -> Constraints(noOverhead = true)
            Area.LowerBack -> Constraints(noAxialLoad = true)
            Area.Elbow, Area.Knee -> Constraints()
        }
        NoGym -> Constraints(home = true)
        is ShortOnTime -> Constraints(maxMinutes = minutes)
        TimeOff, PoorRecovery -> Constraints()
    }
}

/** Limits on what an exercise may demand (GLOSSARY, Constraint). Swaps respect every one set. */
data class Constraints(
    val noGrip: Boolean = false,
    val noAxialLoad: Boolean = false,
    val noOverhead: Boolean = false,
    /** Dumbbells and bodyweight only. */
    val home: Boolean = false,
    /** The next session is cut to fit this many minutes. */
    val maxMinutes: Int? = null,
) {
    /** Whether [exercise] meets every constraint. */
    fun allow(exercise: Exercise): Boolean =
        !(noGrip && exercise.grip == GripDemand.High) &&
            !(noAxialLoad && exercise.axial) &&
            !(noOverhead && exercise.overhead) &&
            !(home && exercise.equipment !in HomeEquipment)

    companion object {
        val HomeEquipment = setOf(Equipment.Dumbbell, Equipment.Bodyweight)
    }
}

/** What the coach says: a Change to review, or that the plan can stand. */
sealed interface Advice {
    data class Propose(val change: Change) : Advice

    /** Time off of [daysOff] days (null: no session yet) or less than a week: resume as planned. */
    data class Resume(val daysOff: Int?) : Advice

    /** Nothing in the plan breaks the constraints. */
    data object NothingToChange : Advice

    /** The program is finished: no day is left for a rule to change. */
    data object Finished : Advice
}

/** A swap for one session: the slot's [exercise] isn't at the gym; [replacement] null when nothing fits. */
data class SessionSwap(val slot: String, val exercise: String, val replacement: String?)

/**
 * The offline coach (SPEC, Coaching → Rules): a situation and constraints, with the plan, the
 * library and when you last trained, make a Change. Pure, so its answers are the same wherever
 * it is asked; the same swap logic proposes swaps for equipment missing at a gym.
 */
object RuleCoach {
    /** How long a pain Change lasts: two weeks, or until reverted ("hands OK"). */
    const val PAIN_DAYS = 14L
    const val LOAD_PERCENT = -10
    const val RESUME_DAYS = 7
    const val REENTRY_DAYS = 14

    /**
     * [weekdays] is the weekday plan (every gym's usual days); with one, time off counts from the
     * first planned day missed rather than from the last session ([daysOff]).
     */
    fun advise(
        situation: Situation,
        constraints: Constraints,
        plan: ActiveProgram,
        library: List<Exercise>,
        lastTrained: LocalDate?,
        weekdays: Set<DayOfWeek> = emptySet(),
    ): Advice {
        if (plan.finished) return Advice.Finished
        if (situation is Situation.Pain && situation.area == Area.Hands && situation.worseAfter != null) {
            return worseAfter(situation.worseAfter, plan, library)
        }
        val today = plan.today
        val byId = library.associateBy { it.id }
        val days = daysSince(lastTrained, today)
        val off = daysOff(lastTrained, today, weekdays)
        val scopeDays = if (situation is Situation.Pain) PAIN_DAYS else 1L
        val scope = Scope.Dates(today, today.plusDays(scopeDays - 1))
        val window = window(plan, scopeDays)
        val slots = window.flatMap { it.slots }.distinctBy { it.id }

        val ops = mutableListOf<Operation>()
        // 1. Swaps for what breaks a constraint: the slot's alternatives first, then the library.
        val kept = mutableListOf<Pair<PlannedSlot, Exercise>>()
        slots.forEach { slot ->
            val exercise = byId[slot.exercise] ?: return@forEach
            when {
                constraints.allow(exercise) && !(constraints.noGrip && exercise.unsupportedRow) -> kept += slot to exercise
                // Pulls stay, with straps and an open hand; a row only chest-supported.
                constraints.noGrip && exercise.pull && constraints.copy(noGrip = false).allow(exercise) -> {
                    val supported = if (exercise.unsupportedRow) candidates(slot, byId, library) { constraints.copy(noGrip = false).allow(it) && it.chestSupported }.firstOrNull() else null
                    if (supported != null) {
                        ops += Operation.Swap(slot.id, supported.id, "chest-supported, straps")
                        kept += slot to supported
                    } else kept += slot to exercise
                }
                else -> {
                    val swap = candidates(slot, byId, library) { constraints.allow(it) }.firstOrNull()
                    if (swap != null) {
                        ops += Operation.Swap(slot.id, swap.id, constraints.reason(exercise))
                        kept += slot to swap
                    } else ops += Operation.Remove(slot.id, constraints.reason(exercise))
                }
            }
        }

        // 2. What the situation itself asks for.
        val next = window.firstOrNull()
        val summary: String
        when (situation) {
            is Situation.Pain -> {
                val loads = kept.filter { (_, e) -> situation.area.loads(e) }
                loads.forEach { (slot, e) -> ops += Operation.AdjustLoad(slot.id, LOAD_PERCENT, if (e.pull && situation.area == Area.Hands) "straps, open hand" else situation.area.label.lowercase()) }
                ops += Operation.AdjustRir(null, 1, "pain")
                loads.filter { (slot, _) -> slot.id in plan.dropsetSlots() }
                    .forEach { (slot, _) -> ops += Operation.RemoveTechnique(slot.id, TechniqueKind.Dropset.key, "no drop sets on ${situation.area.label.lowercase()}") }
                summary = painSummary(situation.area)
            }
            Situation.TimeOff -> when {
                days == null || off == null || off <= RESUME_DAYS -> {
                    if (ops.isEmpty() && constraints.maxMinutes == null) return Advice.Resume(days)
                    summary = "Today's limits"
                }
                off <= REENTRY_DAYS -> {
                    next?.slots?.filter { it.sets.size > 1 }?.forEach { ops += Operation.AdjustSets(it.id, (it.sets.size + 1) / 2, "back after $days days") }
                    ops += Operation.AdjustRir(null, 1, "back after $days days")
                    summary = "Back after $days days: an easier first session"
                }
                else -> {
                    ops += Operation.RepeatWeek("back after $days days")
                    summary = "Back after $days days: repeat the last week"
                }
            }
            Situation.NoGym -> summary = "Home today: dumbbells and bodyweight"
            is Situation.ShortOnTime -> summary = "${constraints.maxMinutes ?: situation.minutes} minutes today"
            Situation.PoorRecovery -> {
                next?.slots?.filter { it.sets.size > 1 }?.forEach { ops += Operation.AdjustSets(it.id, it.sets.size - 1, "poor recovery") }
                ops += Operation.AdjustRir(null, 1, "poor recovery")
                summary = "Poor recovery: a lighter session today"
            }
        }

        // 3. The next session cut to fit the minutes, after everything else.
        constraints.maxMinutes?.let { max ->
            if (next != null) ops += fit(plan, Change(summary, scope, ops.toList()), max)
        }
        if (ops.isEmpty()) return Advice.NothingToChange
        return Advice.Propose(Change(summary, scope, ops))
    }

    /** What a pain Change for [area] is called; [worseAfter] finds the hands' one by it. */
    fun painSummary(area: Area) = if (area == Area.Hands) "Hands: less grip for two weeks" else "${area.label}: train around it for two weeks"

    /**
     * The slots of the next two weeks whose exercise keeps a grip (as the plan is now, the hand
     * Change included): the ones the hands can be worse after.
     */
    fun keptGrip(plan: ActiveProgram, library: List<Exercise>): List<PlannedSlot> {
        if (plan.finished) return emptyList()
        val byId = library.associateBy { it.id }
        return window(plan, PAIN_DAYS).flatMap { it.slots }.distinctBy { it.id }
            .filter { slot -> byId[slot.exercise]?.let(Area.Hands::loads) == true }
    }

    /**
     * The pain rule (docs/programs/full-body-3-day.md): hands worse the next morning after [slotId]'s
     * exercise move it to its no-grip swap (grip demand none: the slot's alternatives, then the
     * movement pattern), or leave it out when there is none, for the rest of the hand Change in
     * force (two weeks with none).
     */
    private fun worseAfter(slotId: String, plan: ActiveProgram, library: List<Exercise>): Advice {
        val byId = library.associateBy { it.id }
        val slot = window(plan, PAIN_DAYS).flatMap { it.slots }.firstOrNull { it.id == slotId } ?: return Advice.NothingToChange
        val exercise = byId[slot.exercise] ?: return Advice.NothingToChange
        val today = plan.today
        val until = plan.changes.filter { it.change.summary == painSummary(Area.Hands) }
            .mapNotNull { (it.change.scope as? Scope.Dates)?.to }.filter { it >= today }.maxOrNull()
            ?: today.plusDays(PAIN_DAYS - 1)
        val swap = candidates(slot, byId, library) { it.grip == GripDemand.None }.firstOrNull()
        val op = if (swap != null) Operation.Swap(slot.id, swap.id, WORSE) else Operation.Remove(slot.id, WORSE)
        return Advice.Propose(Change("Hands worse after ${exercise.name}: no grip on it", Scope.Dates(today, until), listOf(op)))
    }

    private const val WORSE = "hands worse"

    /** Whole days since [lastTrained]; null with no session yet. */
    fun daysSince(lastTrained: LocalDate?, today: LocalDate): Int? = lastTrained?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) }

    /**
     * The days of time off the 7- and 14-day steps read. With a weekday plan ([weekdays]), from
     * the first planned day after [lastTrained] that went untrained (today isn't missed yet), so
     * the plan's own rest days don't count; 0 when none was missed. Without one, the days since
     * the last session. Null with no session yet.
     */
    fun daysOff(lastTrained: LocalDate?, today: LocalDate, weekdays: Set<DayOfWeek> = emptySet()): Int? {
        val since = daysSince(lastTrained, today) ?: return null
        if (weekdays.isEmpty()) return since
        val missed = generateSequence(lastTrained!!.plusDays(1)) { it.plusDays(1) }.takeWhile { it < today }
            .firstOrNull { it.dayOfWeek in weekdays } ?: return 0
        return ChronoUnit.DAYS.between(missed, today).toInt()
    }

    /** Whether [days] off asks for a Change (more than a week). */
    fun timeOff(days: Int?): Boolean = days != null && days > RESUME_DAYS

    /**
     * Swaps for the slots of [day] whose exercise is [missing] at the gym: the slot's alternatives
     * first, then library exercises with the same movement pattern, none of them missing either.
     */
    fun missingEquipment(day: PlannedDay, library: List<Exercise>, missing: Set<String>): List<SessionSwap> {
        val byId = library.associateBy { it.id }
        return day.slots.filter { it.exercise in missing }.map { slot ->
            SessionSwap(slot.id, slot.exercise, candidates(slot, byId, library) { it.id !in missing }.firstOrNull()?.id)
        }
    }

    /** The swap for an exercise not at the gym, as [missingEquipment] picks it; null when nothing fits. */
    fun replacement(slot: PlannedSlot?, exercise: String, library: List<Exercise>, missing: Set<String>): String? {
        val byId = library.associateBy { it.id }
        val s = slot?.copy(exercise = exercise, inPlaceOf = slot.inPlaceOf ?: slot.exercise.takeIf { it != exercise }) ?: PlannedSlot("", "", 0, exercise, emptyList(), null, emptyList())
        return candidates(s, byId, library) { it.id !in missing }.firstOrNull()?.id
    }

    /**
     * About how long [day] takes, in minutes: 2 minutes per exercise to set up and warm up, a
     * minute per set and the rest after it (a superset rests once a round), no rest after the last.
     */
    fun minutes(day: PlannedDay): Int = minutes(day.slots.map { Trim(it.id, it.group, it.sets) })

    private data class Trim(val id: String, val group: Int, val sets: List<SetPrescription>)

    private fun minutes(slots: List<Trim>): Int {
        var seconds = 0
        var lastRest = 0
        slots.groupBy { it.group }.values.forEach { group ->
            seconds += SETUP_SECONDS * group.size
            val rounds = group.maxOf { it.sets.size }
            for (r in 0 until rounds) {
                val sets = group.mapNotNull { it.sets.getOrNull(r) }
                seconds += WORK_SECONDS * sets.size
                lastRest = sets.maxOfOrNull { it.rest } ?: 0
                seconds += lastRest
            }
        }
        return (seconds - lastRest + 59) / 60
    }

    private const val SETUP_SECONDS = 120
    private const val WORK_SECONDS = 60

    /**
     * Cuts the next session (with [change]'s edits) to [max] minutes: second and later sets go
     * first, from the last exercise back; then exercises from the end, never the first.
     */
    private fun fit(plan: ActiveProgram, change: Change, max: Int): List<Operation> {
        val accepted = Plan.accept(plan.program, plan.position, change, plan.today).copy(id = Long.MAX_VALUE)
        val day = Plan.day(plan.program, accepted.after, plan.today, plan.changes + accepted) ?: return emptyList()
        val slots = day.slots.map { Trim(it.id, it.group, it.sets) }.toMutableList()
        val cut = mutableMapOf<String, Int>()
        val removed = mutableListOf<String>()
        while (minutes(slots) > max) {
            val i = slots.indexOfLast { it.sets.size > 1 }
            if (i >= 0) {
                slots[i] = slots[i].copy(sets = slots[i].sets.dropLast(1))
                cut[slots[i].id] = slots[i].sets.size
            } else if (slots.size > 1) {
                removed += slots.removeAt(slots.lastIndex).id
            } else break
        }
        val reason = "$max minutes"
        return cut.filterKeys { it !in removed }.map { (id, n) -> Operation.AdjustSets(id, n, reason) } + removed.map { Operation.Remove(it, reason) }
    }

    /** The days a Change of [days] days from today covers: one week of program days per week of dates. */
    private fun window(plan: ActiveProgram, days: Long): List<PlannedDay> {
        val program = plan.program
        val perWeek = program.blockAt(plan.position.week)?.days?.size ?: 0
        val count = if (days <= 1) 1 else (((days + 6) / 7) * perWeek).toInt()
        return generateSequence(plan.position) { program.next(it) }.take(count)
            .mapNotNull { Plan.day(program, it, plan.today, plan.changes) }
            .toList()
    }

    /**
     * Exercises that can stand in for [slot]'s, best first: its alternatives in the program's
     * order, then library exercises with the same movement pattern (most shared primary muscles,
     * then the least grip, then by name). Hidden exercises and the slot's own are left out.
     */
    private fun candidates(slot: PlannedSlot, byId: Map<String, Exercise>, library: List<Exercise>, allow: (Exercise) -> Boolean): List<Exercise> {
        val current = byId[slot.exercise] ?: return emptyList()
        val alternatives = (listOfNotNull(slot.inPlaceOf) + slot.alternatives).distinct().mapNotNull { byId[it] }
            .filter { it.id != current.id && !it.hidden && allow(it) }
        val others = library.filter { it.pattern == current.pattern && it.id != current.id && !it.hidden && allow(it) && it !in alternatives }
            .sortedWith(compareByDescending<Exercise> { e -> e.muscles.count { it in current.muscles } }.thenBy { it.grip.ordinal }.thenBy { it.name })
        return alternatives + others
    }

    private fun Constraints.reason(exercise: Exercise): String = listOfNotNull(
        "no grip".takeIf { noGrip && exercise.grip == GripDemand.High },
        "no axial load".takeIf { noAxialLoad && exercise.axial },
        "no overhead".takeIf { noOverhead && exercise.overhead },
        "home".takeIf { home && exercise.equipment !in Constraints.HomeEquipment },
    ).joinToString(", ")

    /** Slots with a drop set anywhere in the program, by id. */
    private fun ActiveProgram.dropsetSlots(): Set<String> = program.blocks.flatMap { b ->
        b.days.flatMap { d ->
            d.groups.flatMap { g -> g.slots }.filter { s ->
                (s.sets + s.alternatives.flatMap { it.sets.orEmpty() }).any { spec ->
                    spec.technique?.kind == TechniqueKind.Dropset || spec.weeks.any { it.setsTechnique && it.technique?.kind == TechniqueKind.Dropset }
                }
            }.map { it.id }
        }
    }.toSet()
}

/**
 * Whether pain in this area is loaded by [exercise]: those get lighter and lose their drop sets.
 * Hands: whatever keeps a grip, but lower body and machines (the Smith included) are untouched
 * (docs/programs/full-body-3-day.md, First coaching case), so training stays near full intensity.
 */
fun Area.loads(exercise: Exercise): Boolean = when (this) {
    Area.Hands -> exercise.grip != GripDemand.None && !exercise.lowerBody &&
        exercise.equipment != Equipment.Machine && exercise.equipment != Equipment.SmithMachine
    Area.Shoulder -> exercise.pattern in setOf(MovementPattern.HorizontalPush, MovementPattern.VerticalPush, MovementPattern.ChestFly, MovementPattern.LateralRaise)
    Area.Elbow -> exercise.pattern in setOf(MovementPattern.ElbowFlexion, MovementPattern.ElbowExtension)
    Area.Knee -> exercise.pattern in setOf(MovementPattern.Squat, MovementPattern.Lunge, MovementPattern.KneeExtension)
    Area.LowerBack -> exercise.pattern in setOf(MovementPattern.Hinge, MovementPattern.Squat, MovementPattern.Lunge)
}

/** Legs and hips do the work. */
val Exercise.lowerBody: Boolean
    get() = pattern in setOf(
        MovementPattern.Squat, MovementPattern.Lunge, MovementPattern.Hinge, MovementPattern.HipThrust, MovementPattern.KneeExtension,
        MovementPattern.KneeFlexion, MovementPattern.CalfRaise, MovementPattern.HipAbduction, MovementPattern.HipAdduction,
    )

/** The load sits on the spine: a bar or dumbbells carried through a squat, hinge, lunge, shrug, calf raise or standing press. */
val Exercise.axial: Boolean
    get() = when (equipment) {
        Equipment.Barbell, Equipment.SmithMachine, Equipment.SafetyBar, Equipment.TrapBar ->
            pattern in setOf(MovementPattern.Squat, MovementPattern.Hinge, MovementPattern.Lunge, MovementPattern.Shrug, MovementPattern.CalfRaise, MovementPattern.VerticalPush)
        Equipment.Dumbbell -> pattern in setOf(MovementPattern.Squat, MovementPattern.Hinge, MovementPattern.Lunge, MovementPattern.Shrug)
        else -> false
    }

/**
 * The coach's small per-exercise tag table (SPEC, Coaching → Rules), for what the movement pattern
 * doesn't say: which triceps extensions go overhead and which rows hold the chest on a pad. Keyed
 * by library id, so renaming an exercise on the phone leaves it as it was.
 */
private object CoachTags {
    val Overhead = setOf("overhead_cable_triceps_extension", "overhead_db_triceps_extension")
    val ChestSupported = setOf(
        "chest_supported_db_row", "chest_supported_machine_row", "chest_supported_t_bar_row", "incline_chest_supported_db_row",
    )
}

/** Arms overhead under load: presses and overhead triceps extensions. */
val Exercise.overhead: Boolean
    get() = pattern == MovementPattern.VerticalPush || (pattern == MovementPattern.ElbowExtension && id in CoachTags.Overhead)

private val Exercise.pull: Boolean get() = pattern == MovementPattern.VerticalPull || pattern == MovementPattern.HorizontalPull
private val Exercise.chestSupported: Boolean get() = pattern == MovementPattern.HorizontalPull && id in CoachTags.ChestSupported
private val Exercise.unsupportedRow: Boolean get() = pattern == MovementPattern.HorizontalPull && !chestSupported
