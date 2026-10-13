package com.hyperlog.app.coach

import com.hyperlog.app.program.Program
import kotlinx.serialization.SerializationException

/**
 * Checks a Change against the program it edits, before its diff is shown (SPEC, Ask Claude):
 * it must be a Change, and name slots, days and exercises the program and library know, with
 * values in range. Every problem is reported as `path: message`, like program import.
 */
object ChangeValidator {
    sealed interface Result {
        data class Valid(val change: Change) : Result
        data class Invalid(val errors: List<String>) : Result
    }

    const val MAX_SETS = 10
    const val MAX_LOAD_PERCENT = 50
    const val MAX_RIR_STEP = 5
    const val MAX_RIR = 10
    const val MAX_REST = 900

    /** The technique kinds `remove_technique` names, as in a program file. */
    val TechniqueKinds = com.hyperlog.app.program.TechniqueKind.keys

    fun validate(json: String, program: Program, exercises: Set<String>): Result {
        val change = try {
            ChangeJson.decode(json)
        } catch (e: SerializationException) {
            return Result.Invalid(listOf("not a Change: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}"))
        } catch (e: IllegalArgumentException) {
            return Result.Invalid(listOf("not a Change: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}"))
        }
        val errors = validate(change, program, exercises)
        return if (errors.isEmpty()) Result.Valid(change) else Result.Invalid(errors)
    }

    /** The problems of a Change already read, such as one built by a coach: none when it's valid. */
    fun validate(change: Change, program: Program, exercises: Set<String>): List<String> = buildList {
        if (change.summary.isBlank()) add("summary: a summary is required")
        (change.scope as? Scope.Dates)?.let { if (it.to < it.from) add("scope: \"to\" is before \"from\"") }
        if (change.operations.isEmpty()) add("operations: at least one operation is required")
        val days = program.blocks.flatMap { it.days }
        val slots = days.flatMap { d -> d.groups.flatMap { g -> g.slots.map { it.id } } }.toSet()
        change.operations.forEachIndexed { i, op ->
            val path = "operations[$i]"
            fun slot(id: String?) { if (id != null && id !in slots) add("""$path.slot: unknown slot "$id"""") }
            fun exercise(id: String) { if (id !in exercises) add("""$path.exercise: unknown exercise "$id"""") }
            fun range(key: String, value: Int, min: Int, max: Int, zero: Boolean = true) {
                if (value !in min..max || (!zero && value == 0)) {
                    add("$path.$key: must be ${signed(min)} to $max" + if (!zero && value == 0) ", not 0" else "")
                }
            }
            when (op) {
                is Operation.Swap -> { slot(op.slot); exercise(op.exercise) }
                is Operation.Remove -> slot(op.slot)
                is Operation.Add -> {
                    val day = days.filter { it.id == op.day }
                    if (day.isEmpty()) add("""$path.day: unknown day "${op.day}"""")
                    exercise(op.exercise)
                    if (op.after != null && day.isNotEmpty() && day.none { d -> d.groups.any { g -> g.slots.any { it.id == op.after } } }) {
                        add("""$path.after: no slot "${op.after}" on day "${op.day}"""")
                    }
                    if (op.sets.isEmpty()) add("$path.sets: at least one set is required")
                    op.sets.forEachIndexed { j, s ->
                        if (s.count !in 1..MAX_SETS) add("$path.sets[$j].count: must be 1 to $MAX_SETS")
                        s.rir?.let { if (it !in 0..MAX_RIR) add("$path.sets[$j].rir: must be 0 to $MAX_RIR") }
                        if (s.rest !in 0..MAX_REST) add("$path.sets[$j].rest: must be 0 to $MAX_REST seconds")
                    }
                }
                is Operation.AdjustSets -> { slot(op.slot); range("sets", op.sets, 1, MAX_SETS) }
                is Operation.AdjustLoad -> { slot(op.slot); range("percent", op.percent, -MAX_LOAD_PERCENT, MAX_LOAD_PERCENT, zero = false) }
                is Operation.AdjustRir -> { slot(op.slot); range("by", op.by, -MAX_RIR_STEP, MAX_RIR_STEP, zero = false) }
                is Operation.RemoveTechnique -> {
                    slot(op.slot)
                    op.technique?.let { if (it !in TechniqueKinds) add("""$path.technique: unknown technique "$it": use ${TechniqueKinds.joinToString()}""") }
                }
                is Operation.DeloadWeek -> op.week?.let { range("week", it, 1, program.weeks) }
                is Operation.ShiftProgram -> when {
                    op.weeks == 0 -> add("$path.weeks: must not be 0")
                    kotlin.math.abs(op.weeks) > program.weeks -> add("$path.weeks: the program is ${program.weeks} weeks long")
                }
                is Operation.SkipDay, is Operation.RepeatWeek -> Unit
            }
        }
    }

    /** A real minus sign, as the app writes numbers. */
    fun signed(n: Int) = if (n < 0) "−${-n}" else "$n"
}
