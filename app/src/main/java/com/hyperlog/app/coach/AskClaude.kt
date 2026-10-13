package com.hyperlog.app.coach

import com.hyperlog.app.domain.summary
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.planChunks
import com.hyperlog.app.session.PastSessionItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Free-text coaching through the user's Claude app (SPEC, Ask Claude; ADR 0001). Hyperlog makes no
 * model calls: [prompt] is shared to the Claude app through the share sheet, and the Change it
 * replies with comes back through the clipboard to [read].
 */
object AskClaude {
    /** The sessions the prompt carries: the last four weeks. */
    const val HISTORY_DAYS = 28L

    /**
     * The prompt for the Claude app: [words], the program and where you are in it, the sessions of
     * the last [HISTORY_DAYS] days, the library entries a swap could use, and the Change format
     * with the instruction to reply with one fenced JSON block.
     */
    fun prompt(
        words: String,
        plan: ActiveProgram,
        sessions: List<PastSessionItem>,
        library: List<Exercise>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        val program = plan.program
        val names = library.associate { it.id to it.name }
        appendLine("I train with Hyperlog, an app that runs my strength program and logs my sessions. Help me adjust the plan around this:")
        appendLine()
        words.trim().lines().forEach { appendLine("> $it") }
        appendLine()
        appendLine(
            "Talk it through with me first if you need to know more. When we agree on a change, reply with exactly one " +
                "fenced ```json block holding a Change in the format at the end, and nothing else in that message. I'll paste it " +
                "into Hyperlog, which checks it and shows me the diff before anything changes. Use only the slot, day and " +
                "exercise ids listed here.",
        )
        appendLine()
        appendLine("Today is ${plan.today} (${plan.today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)}).")
        appendLine()
        program(plan, names)
        appendLine()
        sessions(sessions, plan.today, zone)
        appendLine()
        library(program, library)
        appendLine()
        append(ChangeFormat.text(plan))
    }

    /**
     * Reads a pasted Change: Claude's whole reply or just its JSON. The fenced block is taken when
     * there is one, else the outermost braces; then it's checked like any Change.
     */
    fun read(pasted: String, program: Program, exercises: Set<String>): ChangeValidator.Result {
        val json = extract(pasted) ?: return ChangeValidator.Result.Invalid(listOf(NOTHING_TO_READ))
        return ChangeValidator.validate(json, program, exercises)
    }

    /** The message when the paste holds no JSON at all. */
    const val NOTHING_TO_READ = "no Change found: copy Claude's reply with its JSON block"

    private val Fence = Regex("```[a-zA-Z]*\\s*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)

    private fun extract(text: String): String? {
        Fence.findAll(text).map { it.groupValues[1].trim() }.firstOrNull { it.startsWith("{") }?.let { return it }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun StringBuilder.program(plan: ActiveProgram, names: Map<String, String>) {
        val program = plan.program
        appendLine("## Program: ${program.title}")
        val next = plan.next
        if (next == null) {
            appendLine("Finished: every day has been trained.")
        } else {
            appendLine(
                "Next: week ${next.position.week} of ${program.weeks}, ${next.day.name} (day \"${next.day.id}\", block \"${next.block.name}\", " +
                    "weeks ${next.block.weekStart}–${next.block.weekEnd}). ${program.daysLeft(plan.position)} days left.",
            )
            appendLine("The program runs as a sequence: the next day is whatever comes after the last one trained, whenever I train.")
        }
        val changes = plan.changes
        if (changes.isNotEmpty()) {
            appendLine()
            appendLine("Changes in force (accepted earlier):")
            changes.forEach { appendLine("- ${it.change.summary} (${scopeText(it.change.scope)}, accepted ${it.acceptedOn})") }
        }
        val current = plan.position.week.coerceAtMost(program.weeks)
        program.blocks.forEach { block ->
            val week = if (current in block.weekStart..block.weekEnd) current else block.weekStart
            appendLine()
            appendLine("### Block \"${block.name}\" (id \"${block.id}\"), ${weeksText(block.weekStart, block.weekEnd)}; sets as planned in week $week, before any Change")
            block.days.indices.forEach { d ->
                val day = program.plannedDay(Position(week, d)) ?: return@forEach
                day(day, names)
            }
        }
    }

    private fun StringBuilder.day(day: PlannedDay, names: Map<String, String>) {
        appendLine("Day \"${day.day.id}\": ${day.day.name}${if (day.deload) " (deload)" else ""}")
        day.slots.forEach { slot ->
            val sets = planChunks(slot.sets).joinToString(" · ") { c ->
                buildString {
                    if (c.backoff) append("back-off ")
                    append("${c.count} × ${c.reps}")
                    c.rir?.let { append(" @ $it RIR") }
                    c.techniques.map { it.kind }.distinct().takeIf { it.isNotEmpty() }?.let { append(" (${it.joinToString()} on the last)") }
                }
            }
            append("  ${slot.label}. slot \"${slot.id}\": ${slot.exercise} (${names[slot.exercise] ?: slot.exercise}), $sets")
            if (slot.alternatives.isNotEmpty()) append("; alternatives: ${slot.alternatives.joinToString()}")
            slot.note?.let { append("; note: ${it.replace('\n', ' ')}") }
            appendLine()
        }
    }

    private fun StringBuilder.sessions(sessions: List<PastSessionItem>, today: LocalDate, zone: ZoneId) {
        val from = today.minusDays(HISTORY_DAYS)
        val recent = sessions
            .map { it to Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .filter { (_, date) -> date >= from && date <= today }
            .sortedBy { (it, _) -> it.startedAt }
        appendLine("## Sessions of the last ${HISTORY_DAYS / 7} weeks")
        appendLine("Sets read weight kg × reps @ RIR (reps in reserve); + Np = partial reps; dumbbells are per dumbbell; drops follow a comma, myo mini-sets and holds a +.")
        if (recent.isEmpty()) appendLine("None.")
        recent.forEach { (session, date) ->
            appendLine("$date ${date.format(Weekday)}: ${session.title ?: "ad-hoc session"}${session.gym?.let { " at $it" } ?: ""}")
            session.exercises.forEach { e -> appendLine("  ${e.name}: ${e.sets.joinToString(", ") { it.summary() }}") }
        }
    }

    /** The program's exercises and every exercise sharing a movement pattern with one, by pattern. */
    private fun StringBuilder.library(program: Program, library: List<Exercise>) {
        val used = program.blocks.flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots.flatMap { s -> listOf(s.exercise) + s.alternatives.map { it.exercise } } } } }.toSet()
        val patterns = library.filter { it.id in used }.map { it.pattern }.toSet()
        val relevant = library.filter { it.id in used || (!it.hidden && it.pattern in patterns) }
        appendLine("## Exercise library (the ones a swap or add can use)")
        appendLine("id: name (equipment, grip demand; primary muscles)")
        relevant.groupBy { it.pattern }.toSortedMap(compareBy { it.ordinal }).forEach { (pattern, exercises) ->
            appendLine("${pattern.label}:")
            exercises.sortedBy { it.name }.forEach { e ->
                appendLine("  ${e.id}: ${e.name} (${e.equipment.key}, grip ${e.grip.key}; ${e.muscles.joinToString()})")
            }
        }
    }

    private val Weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    private fun weeksText(from: Int, to: Int) = if (from == to) "week $from" else "weeks $from–$to"

    private fun scopeText(scope: Scope) = when (scope) {
        is Scope.Dates -> "${scope.from} to ${scope.to}"
        Scope.RestOfProgram -> "rest of the program"
        Scope.RestOfBlock -> "rest of the block"
    }
}

/**
 * The Change format as the prompt states it, generated from the code that reads and checks a
 * Change: the operation names and fields come from [Operation]'s serializer, the scopes from
 * [ScopeSerializer], the ranges from [ChangeValidator], and the example is a Change built for the
 * running program and written by [ChangeJson]. A new operation fails here until it is described.
 */
object ChangeFormat {
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json(from = ChangeJson.format) { prettyPrint = true; prettyPrintIndent = "  " }

    /** SPEC's order: plan edits, then the week and position operations. Any other goes last. */
    private val Order = listOf(
        "swap", "remove", "add", "adjust_sets", "adjust_load", "adjust_rir", "remove_technique",
        "skip_day", "deload_week", "shift_program", "repeat_week",
    )

    /** What each operation does, by its JSON name. Ranges come from the validator. */
    private fun description(op: String, program: Program): String = with(ChangeValidator) {
        when (op) {
            "swap" -> "The slot does that exercise instead (an alternative's own sets, else the slot's). Prefer the slot's alternatives."
            "remove" -> "The slot is left out."
            "add" -> "The exercise is added to the day (a day id) after slot \"after\" (else at the end). \"sets\" are set lines: " +
                "{\"count\": 1 to $MAX_SETS, \"reps\": 8 or \"8-10\", \"rir\"?: 0 to $MAX_RIR, \"rest\"?: seconds, 0 to $MAX_REST}."
            "adjust_sets" -> "The slot does that many sets, 1 to $MAX_SETS: its first ones, or its last set repeated without its technique."
            "adjust_load" -> "Suggested weights move by \"percent\", ${signed(-MAX_LOAD_PERCENT)} to $MAX_LOAD_PERCENT and not 0 (−10 = 10% lighter), " +
                "to the nearest weight the equipment allows, on top of normal progression. No slot: every slot."
            "adjust_rir" -> "Target RIR moves by \"by\", ${signed(-MAX_RIR_STEP)} to $MAX_RIR_STEP and not 0 (+1 = one more rep in reserve). No slot: every slot."
            "remove_technique" -> "The slot's sets lose that intensity technique (${TechniqueKinds.joinToString()}), or any technique when " +
                "\"technique\" is left out. No slot: every slot."
            "skip_day" -> "The next day is passed over."
            "deload_week" -> "Program week \"week\" (1 to ${program.weeks}; the one I'm in when left out) is a deload: half its sets per slot " +
                "(rounded up), RIR + 2, no techniques."
            "shift_program" -> "The rest of the plan starts \"weeks\" weeks later (not 0, at most ${program.weeks}): the position goes back that " +
                "many weeks, so they are trained again. Negative moves it earlier, skipping weeks."
            "repeat_week" -> "The week of the last day trained runs again from its first day."
            else -> error("Ask Claude has no description of operation \"$op\"")
        }
    }

    fun text(plan: ActiveProgram): String = buildString {
        val program = plan.program
        appendLine("## Change format")
        appendLine("Reply with one fenced ```json block holding one JSON object:")
        appendLine("- \"summary\": one short sentence saying what changes and why.")
        appendLine("- \"scope\": which sessions the plan edits cover. One of:")
        appendLine("  - ${scope(Scope.Dates(plan.today, plan.today.plusDays(6)))}: sessions trained on those dates, both included (temporary);")
        appendLine("  - ${scope(Scope.RestOfBlock)}: from today to the end of the block I'm in;")
        appendLine("  - ${scope(Scope.RestOfProgram)}: from today on.")
        appendLine("- \"operations\": a list, applied in order. Slot edits follow a slot id into later blocks that reuse it. Position operations " +
            "(skip_day, shift_program, repeat_week) run once, when I accept. Every operation may carry a \"reason\". Fields with ? are optional:")
        operations().forEach { (op, fields) ->
            val shape = (listOf("\"op\": \"$op\"") + fields.map { (name, optional) -> "\"$name\"" + if (optional) "?" else "" }).joinToString(", ")
            appendLine("  - {$shape}: ${description(op, program)}")
        }
        appendLine("No operation reorders exercises. Unknown fields, slots, days or exercises are rejected.")
        appendLine()
        appendLine("For example:")
        appendLine("```json")
        appendLine(pretty.encodeToString(Change.serializer(), example(plan)))
        appendLine("```")
    }

    /** Each operation's JSON name with its fields (name, optional), `reason` left out, as the serializer reads them. */
    fun operations(): List<Pair<String, List<Pair<String, Boolean>>>> {
        val subclasses = Operation.serializer().descriptor.getElementDescriptor(1).elementDescriptors
        return subclasses.map { d ->
            d.serialName to (0 until d.elementsCount).map { d.getElementName(it) to d.isElementOptional(it) }.filter { it.first != "reason" }
        }.sortedBy { (op, _) -> Order.indexOf(op).takeIf { it >= 0 } ?: Order.size }
    }

    private fun scope(scope: Scope) = ChangeJson.format.encodeToString(ScopeSerializer, scope)

    /**
     * A Change that fits the running program, so the example names real ids: the next day's first
     * slot with an alternative swapped for it, and one more rep in reserve everywhere, for a week.
     */
    fun example(plan: ActiveProgram): Change {
        val day = plan.next ?: plan.program.plannedDay(Position(1, 0))
        val swap = day?.slots?.firstOrNull { it.alternatives.isNotEmpty() }
        val operations = listOfNotNull(
            swap?.let { Operation.Swap(it.id, it.alternatives.first(), reason = "example") },
            Operation.AdjustRir(null, 1, reason = "example"),
        )
        return Change(
            summary = "For a week: an alternative for one exercise and one more rep in reserve on everything.",
            scope = Scope.Dates(plan.today, plan.today.plusDays(6)),
            operations = operations,
        )
    }
}
