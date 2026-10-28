package com.hyperlog.app.coach

import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.summary
import com.hyperlog.app.gym.EquipmentKind
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.sections
import com.hyperlog.app.history.TimeOff
import com.hyperlog.app.history.WeekSets
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.planChunks
import com.hyperlog.app.session.PastSessionItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Coaching through the user's Claude app (SPEC, Coaching → Words first; ADRs 0001 and 0003).
 * Hyperlog makes no model calls: [prompt] is shared to the Claude app through the share sheet, and
 * the Change or workout it replies with comes back through the clipboard to [read].
 */
object AskClaude {
    /** The sessions the prompt carries: the last four weeks. */
    const val HISTORY_DAYS = 28L

    /** The weeks of weekly sets the prompt carries: this week and the 4 before it. */
    const val SET_WEEKS = 5

    /**
     * The prompt for the Claude app: [words], what Claude may answer with, today and the days off
     * since [lastTrained] (by the [weekdays] plan when there is one), the [gym] picked with its
     * equipment and what's missing there, the program and where you are in it, the sessions of the
     * last [HISTORY_DAYS] days, the [weeklySets] of the last [SET_WEEKS] weeks, every visible
     * library exercise, and the formats of a Change, a workout and a program. Claude may answer in
     * words alone. With no program running ([plan] null) there is no plan to change: words, a
     * workout or a program.
     */
    fun prompt(
        words: String,
        plan: ActiveProgram?,
        sessions: List<PastSessionItem>,
        library: List<Exercise>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = plan?.today ?: LocalDate.now(zone),
        gym: Gym? = null,
        /** Training somewhere not set up in Hyperlog: no gym's equipment goes out, the words say what there is. */
        elsewhere: Boolean = false,
        lastTrained: LocalDate? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
        weeklySets: List<WeekSets> = emptyList(),
        /** Your training days, each with its gym (named by [gymNames]) and minutes. */
        weekdayPlan: com.hyperlog.app.week.WeekdayPlan = com.hyperlog.app.week.WeekdayPlan.None,
        gymNames: Map<Long, String> = emptyMap(),
        /** The check-in's tile, quoted before the [words]: what it is about, even when the words are cleared. */
        tile: Tile? = null,
        /** More for Claude to read with the situation, as a block review. */
        context: String? = null,
    ): String = buildString {
        if (plan == null) {
            appendLine("I train with Hyperlog, an app that logs my strength sessions. Help me with this:")
        } else {
            appendLine("I train with Hyperlog, an app that runs my strength program and logs my sessions. Help me with this:")
        }
        appendLine()
        // The tile is quoted unless the words already begin with its sentence (`Something hurts: …`).
        tile?.takeUnless { startsWithTile(words, it) }?.let { appendLine("> ${it.words}") }
        if (words.isNotBlank()) words.trim().lines().forEach { appendLine("> $it") }
        if (tile != null || words.isNotBlank()) appendLine()
        if (plan == null) {
            appendLine(
                "No program is running in Hyperlog: I train ad hoc, so there is no plan to change. Talk it through with me first if " +
                    "you need to know more. Advice for my next sessions can stay in words. When you build me a workout for today or " +
                    "a new program, reply with exactly one fenced ```json block holding it in its format at the end, and nothing else in " +
                    "that message. I'll paste it into Hyperlog, which checks it and shows it to me before anything changes. " +
                    "Use only the exercise ids listed here.",
            )
        } else {
            appendLine(
                "Talk it through with me first if you need to know more. If the plan can stand as it is, say so in words and " +
                    "send no JSON. Otherwise reply with exactly one fenced ```json block holding a Change, a workout or a program in the " +
                    "formats at the end, and nothing else in that message: a Change edits the program for a while, a workout is " +
                    "one day for today outside the program, and a program replaces the one I run. I'll paste it into Hyperlog, which " +
                    "checks it and shows it to me before anything changes. Use only the slot, day and exercise ids listed here.",
            )
        }
        appendLine()
        situation(plan, sessions, library, zone, today, gym, elsewhere, lastTrained, weekdays.ifEmpty { weekdayPlan.days }, weeklySets, weekdayPlan, gymNames)
        context?.let { appendLine(); appendLine(it) }
        if (plan != null) {
            appendLine()
            append(ChangeFormat.text(plan))
        }
        appendLine()
        append(WorkoutFormat.text(library))
        appendLine()
        append(ProgramFormat.text(library))
    }

    /** Whether [words] already begin with [tile]'s sentence, without its full stop (`Something hurts: …`). */
    private fun startsWithTile(words: String, tile: Tile): Boolean {
        fun plain(text: String) = text.trim().replace('’', '\'').lowercase()
        return plain(words).startsWith(plain(tile.words).removeSuffix("."))
    }

    /**
     * What both ways of asking Claude tell it, built once (SPEC, What Claude is told): today and the
     * days off, the [gym] (or [elsewhere]), the program with its Changes in force, the sessions, the
     * weekly sets and the library.
     */
    internal fun StringBuilder.situation(
        plan: ActiveProgram?,
        sessions: List<PastSessionItem>,
        library: List<Exercise>,
        zone: ZoneId,
        today: LocalDate,
        gym: Gym?,
        elsewhere: Boolean,
        lastTrained: LocalDate?,
        weekdays: Set<DayOfWeek>,
        weeklySets: List<WeekSets>,
        weekdayPlan: com.hyperlog.app.week.WeekdayPlan = com.hyperlog.app.week.WeekdayPlan.None,
        gymNames: Map<Long, String> = emptyMap(),
    ) {
        val names = library.associate { it.id to it.name }
        appendLine("Today is $today (${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}).")
        appendLine(daysOff(lastTrained, today, weekdays))
        if (weekdayPlan.days.isNotEmpty()) {
            appendLine()
            weekdayPlan(weekdayPlan, gymNames)
        }
        if (elsewhere) {
            appendLine()
            appendLine("## Gym: somewhere not set up in Hyperlog")
            appendLine("I'm not at any of my usual gyms. The equipment there is in my words above; plan only with that.")
        } else gym?.let {
            appendLine()
            gym(it, names)
        }
        if (plan != null) {
            appendLine()
            program(plan, names)
        }
        appendLine()
        sessions(sessions, today, zone)
        if (weeklySets.isNotEmpty()) {
            appendLine()
            weeklySets(weeklySets)
        }
        appendLine()
        library(plan?.program, library)
    }

    /** Your training days in weekday order, each with its gym and the minutes you have. */
    private fun StringBuilder.weekdayPlan(plan: com.hyperlog.app.week.WeekdayPlan, gymNames: Map<Long, String>) {
        appendLine("## Training days")
        plan.days.sorted().forEach { day ->
            val gym = plan.gyms[day]?.let(gymNames::get) ?: "any gym"
            val minutes = plan.minutes[day]?.let { "$it minutes" } ?: "no time limit"
            appendLine("- ${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}: $gym, $minutes")
        }
    }

    /** The program's days as the rotation puts them on the training days, and the training days missed lately. */
    private fun StringBuilder.rotation(plan: ActiveProgram, rotation: com.hyperlog.app.week.Rotation) {
        appendLine(
            "The program runs on my training days: each training day takes the next day of the program, trained or missed. A missed " +
                "day is skipped, never moved. A program week is a calendar week, Monday to Sunday.",
        )
        fun line(turn: com.hyperlog.app.program.Turn) =
            "- ${turn.date} (${turn.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}): week ${turn.position.week}, " +
                (plan.program.blockAt(turn.position.week)?.days?.getOrNull(turn.position.day)?.name ?: "")
        when {
            rotation.easy -> appendLine(
                "This week is an easy week, after ${rotation.emptyWeeks} weeks with no session: each slot one set fewer, target RIR one " +
                    "higher, no techniques, a deload outside the program. The program week after it is the one I stopped at.",
            )
            rotation.again -> appendLine("Last week had no session, so this week trains its program week again.")
            rotation.repeated -> appendLine("A repeat_week accepted last week has this week train its program week again.")
        }
        appendLine("Coming days:")
        plan.turns.filter { it.status == com.hyperlog.app.program.Turn.Status.Ahead }.take(UPCOMING_TURNS).forEach { appendLine(line(it)) }
        val missed = plan.turns.filter { it.status == com.hyperlog.app.program.Turn.Status.Missed && it.date >= plan.today.minusDays(HISTORY_DAYS) && it.date < plan.today }
        if (missed.isNotEmpty()) {
            appendLine("Missed training days in the last $HISTORY_DAYS days:")
            missed.forEach { appendLine(line(it)) }
        }
    }

    /** How many of the rotation's coming days the context lists. */
    private const val UPCOMING_TURNS = 6

    private fun daysOff(lastTrained: LocalDate?, today: LocalDate, weekdays: Set<DayOfWeek>): String {
        val since = TimeOff.daysSince(lastTrained, today) ?: return "I haven't finished a session in Hyperlog yet."
        val ago = when (since) {
            0 -> "today"
            1 -> "1 day ago"
            else -> "$since days ago"
        }
        val line = "My last finished session was on $lastTrained, $ago."
        if (weekdays.isEmpty()) return line
        val off = TimeOff.daysOff(lastTrained, today, weekdays) ?: return line
        val days = weekdays.sorted().map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
        val plan = if (days.size == 1) days.single() else days.dropLast(1).joinToString() + " and " + days.last()
        return "$line I plan to train on $plan, so that's $off ${if (off == 1) "day" else "days"} off."
    }

    /** The gym picked: its equipment by kind, its dumbbells and plates, and the exercises it has nothing for. */
    internal fun StringBuilder.gym(gym: Gym, names: Map<String, String>) {
        appendLine("## Gym: ${gym.name}")
        appendLine("Where I'm training. Its equipment:")
        gym.sections().forEach { (kind, items) ->
            appendLine("${kind.heading}:")
            items.forEach { item ->
                val ladder = if (kind == EquipmentKind.RackBench) "" else " (${item.ladder.summary(gym.pinPlates)})"
                appendLine("- ${item.name}$ladder")
            }
        }
        appendLine("- Dumbbells: ${gym.dumbbells.summary()}")
        val plates = gym.plates.groupingBy { it }.eachCount().toSortedMap(compareByDescending { it })
            .map { (kg, pairs) -> "$pairs × ${formatKg(kg)}" }
        if (plates.isNotEmpty()) appendLine("- Plates, in pairs: ${plates.joinToString()} kg")
        if (gym.missing.isNotEmpty()) {
            appendLine("Not here, so swap them out: ${gym.missing.sorted().joinToString { "$it (${names[it] ?: it})" }}")
        }
    }

    private val EquipmentKind.heading: String
        get() = when (this) {
            EquipmentKind.Stack -> "Stack machines"
            EquipmentKind.PlateLoaded -> "Plate-loaded"
            EquipmentKind.Bar -> "Bars"
            EquipmentKind.RackBench -> "Racks and benches"
        }

    /** Sets per muscle for this week and the [SET_WEEKS] − 1 before it, as History counts them. */
    private fun StringBuilder.weeklySets(weeks: List<WeekSets>) {
        appendLine("## Weekly sets per muscle")
        appendLine("Work sets, Monday to Sunday: 1 for each primary muscle of the exercise, 0.5 for each secondary one.")
        weeks.sortedByDescending { it.start }.take(SET_WEEKS).forEachIndexed { i, week ->
            val sets = week.sets.filterValues { it > 0 }.entries.joinToString { (muscle, n) -> "$muscle ${formatKg(n)}" }.ifEmpty { "none" }
            appendLine("Week of ${week.start}${if (i == 0) " (this week)" else ""}: $sets")
        }
    }

    /** What a paste holds: a Change, a workout, or the problems that keep it out. */
    sealed interface Reply {
        data class ChangeReply(val change: Change) : Reply
        data class WorkoutReply(val workout: Workout) : Reply
        data class ProgramReply(val proposal: ProgramProposal) : Reply
        data class Invalid(val errors: List<String>) : Reply
    }

    /**
     * Reads a pasted reply: Claude's whole reply or just its JSON. The fenced block is taken when
     * there is one, else the outermost braces. An object with `program` is a new program, checked as
     * program import checks it, with its swaps and new exercises (new by id and by [names]); one with
     * `workout` is a workout, checked as program import checks a day; anything else is a Change for
     * [program], which must be running.
     */
    fun read(
        pasted: String,
        program: Program?,
        exercises: Set<String>,
        inForce: Set<Long> = emptySet(),
        merged: Map<String, String> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ): Reply {
        val json = extract(pasted) ?: return Reply.Invalid(listOf(if ('{' in pasted) CUT_OFF else NOTHING_TO_READ))
        val top = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        val workout = top?.containsKey("workout") == true
        val result = when {
            top?.containsKey("program") == true -> when (val read = ProgramProposal.read(top, exercises, names, merged)) {
                is ProgramProposal.Result.Valid -> Reply.ProgramReply(read.proposal)
                is ProgramProposal.Result.Invalid -> Reply.Invalid(read.errors)
            }
            workout -> when (val read = com.hyperlog.app.program.ProgramParser.workout(json, exercises, merged)) {
                is com.hyperlog.app.program.ProgramParser.WorkoutResult.Valid -> Reply.WorkoutReply(read.workout)
                is com.hyperlog.app.program.ProgramParser.WorkoutResult.Invalid -> Reply.Invalid(read.errors)
            }
            program == null -> Reply.Invalid(listOf(NO_PROGRAM))
            else -> when (val read = ChangeValidator.validate(json, program, exercises, inForce)) {
                is ChangeValidator.Result.Valid -> Reply.ChangeReply(read.change)
                is ChangeValidator.Result.Invalid -> Reply.Invalid(read.errors)
            }
        }
        // More braces opened than closed: the copy stopped short, so the problems are the copy's, not Claude's.
        val rest = pasted.substring(pasted.indexOf('{'))
        if (result is Reply.Invalid && rest.count { it == '{' } > rest.count { it == '}' }) {
            return Reply.Invalid(listOf(CUT_OFF))
        }
        return result
    }

    /** The message when the paste holds no JSON at all. */
    const val NOTHING_TO_READ = "No Change, workout or program found in what you copied. Copy Claude's whole reply, with its JSON block, then paste again."

    /** The message when the paste's JSON stops before its end. */
    const val CUT_OFF = "Claude's reply is cut off before its JSON ends. Copy the whole reply, then paste again."

    /** The message for a Change pasted with no program running. */
    const val NO_PROGRAM = "No program is running, so there’s nothing for a Change to change."

    /** Whether [errors] are about the copy, not the reply: then there's nothing to take back to Claude. */
    fun copyProblem(errors: List<String>): Boolean = errors == listOf(NOTHING_TO_READ) || errors == listOf(CUT_OFF)

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
        val next = plan.next?.day
        if (next == null) {
            appendLine("Finished: every day has been trained.")
        } else {
            appendLine(
                "Next: week ${next.position.week} of ${program.weeks}, ${next.day.name} (day \"${next.day.id}\", block \"${next.block.name}\", " +
                    "weeks ${next.block.weekStart}–${next.block.weekEnd}). ${program.daysLeft(plan.position)} days left.",
            )
            plan.rotation?.let { rotation(plan, it) }
                ?: appendLine("The program runs as a sequence: the next day is whatever comes after the last one trained, whenever I train.")
        }
        val changes = plan.inForce
        if (changes.isNotEmpty()) {
            appendLine()
            appendLine("Changes in force (accepted earlier, newest first), each with its id, its operations as JSON and the check-in that made it:")
            changes.forEach {
                appendLine("- id ${it.id}: ${it.change.summary} (${scopeText(it.change.scope)}, accepted ${it.accepted.acceptedOn})")
                appendLine("  operations: ${ChangeJson.format.encodeToString(ListSerializer(Operation.serializer()), it.change.operations)}")
                it.checkIn?.let { made -> appendLine("  check-in: ${made.text().lines().joinToString(" / ")}") }
            }
        }
        val current = plan.week
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
                    c.techniques.map { it.kind.key }.distinct().takeIf { it.isNotEmpty() }?.let { append(" (${it.joinToString()} on the last)") }
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

    /** Every visible library exercise, and any hidden one the program uses, by movement pattern. */
    private fun StringBuilder.library(program: Program?, library: List<Exercise>) {
        val used = program?.blocks.orEmpty().flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots.flatMap { s -> listOf(s.exercise) + s.alternatives.map { it.exercise } } } } }.toSet()
        val listed = library.filter { !it.hidden || it.id in used }
        appendLine("## Exercise library")
        appendLine("id: name (equipment, grip demand; primary muscles)")
        listed.groupBy { it.pattern }.toSortedMap(compareBy { it.ordinal }).forEach { (pattern, exercises) ->
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
    internal fun description(op: String, program: Program): String = with(ChangeValidator) {
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
            "skip_day" -> "With training days, the training day on \"date\" (the next one when left out) is missed ahead of time: its day " +
                "is used up and the rotation moves on. Without them, the next day is passed over."
            "deload_week" -> "Program week \"week\" (1 to ${program.weeks}; the one I'm in when left out) is a deload: half its sets per slot " +
                "(rounded up), RIR + 2, no techniques."
            "shift_program" -> "The rest of the plan starts \"weeks\" weeks later (not 0, at most ${program.weeks}): the position goes back that " +
                "many weeks, so they are trained again. Negative moves it earlier, skipping weeks."
            "repeat_week" -> "The week of the last day trained runs again from its first day; with training days, the next calendar week " +
                "trains this program week again."
            else -> error("Ask Claude has no description of operation \"$op\"")
        }
    }

    fun text(plan: ActiveProgram): String = buildString {
        val program = plan.program
        appendLine("## Change format")
        appendLine("Reply with one fenced json code block holding one JSON object:")
        appendLine("- \"summary\": one short sentence saying what changes and why.")
        appendLine("- \"scope\": which sessions the plan edits cover. One of:")
        appendLine("  - ${scope(Scope.Dates(plan.today, plan.today.plusDays(6)))}: sessions trained on those dates, both included (temporary);")
        appendLine("  - ${scope(Scope.RestOfBlock)}: from today to the end of the block I'm in;")
        appendLine("  - ${scope(Scope.RestOfProgram)}: from today on.")
        appendLine(
            "- \"replaces\"?: a list of ids of Changes in force that this one ends. Accepting it reverts them first, then applies its " +
                "own operations. With \"replaces\", \"operations\" may be empty, to only end them.",
        )
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
        plan.inForce.firstOrNull()?.let { record ->
            appendLine()
            appendLine("When a Change in force should end, for example \"${record.change.summary}\" (id ${record.id}). To only end it:")
            appendLine("```json")
            appendLine(pretty.encodeToString(Change.serializer(), ending(plan, record.id)))
            appendLine("```")
            appendLine("To replace it with other operations:")
            appendLine("```json")
            appendLine(pretty.encodeToString(Change.serializer(), example(plan).copy(summary = "In place of an earlier Change: an alternative for one exercise and one more rep in reserve, for a week.", replaces = listOf(record.id))))
            appendLine("```")
        }
    }

    /** Each operation's JSON name with its fields (name, optional), `reason` left out, as the serializer reads them. */
    fun operations(): List<Pair<String, List<Pair<String, Boolean>>>> {
        val subclasses = Operation.serializer().descriptor.getElementDescriptor(1).elementDescriptors
        return subclasses.map { d ->
            d.serialName to (0 until d.elementsCount).map { d.getElementName(it) to d.isElementOptional(it) }.filter { it.first != "reason" }
        }.sortedBy { (op, _) -> Order.indexOf(op).takeIf { it >= 0 } ?: Order.size }
    }

    private fun scope(scope: Scope) = ChangeJson.format.encodeToString(ScopeSerializer, scope)

    /** A Change that only ends the Change in force [id]. */
    fun ending(plan: ActiveProgram, id: Long): Change =
        Change("The earlier Change is no longer needed.", Scope.RestOfProgram, emptyList(), replaces = listOf(id))

    /**
     * A Change that fits the running program, so the example names real ids: the next day's first
     * slot with an alternative swapped for it, and one more rep in reserve everywhere, for a week.
     */
    fun example(plan: ActiveProgram): Change {
        val day = plan.next?.day ?: plan.program.plannedDay(Position(1, 0))
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
