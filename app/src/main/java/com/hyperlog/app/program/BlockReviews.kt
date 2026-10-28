package com.hyperlog.app.program

import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.LiftGoal
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.progression.Record
import com.hyperlog.app.week.Rotations
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A session of an exercise as the review reads it: in program [week] of this run (null for a
 * session outside it), [eased] in a week the plan eases (as Ramp-in), [deload] in a deload or an
 * easy week, with the estimate after it.
 */
data class ReviewPoint(
    val date: LocalDate,
    val week: Int?,
    val eased: Boolean,
    val estimateKg: Double?,
    val record: ReviewRecord? = null,
    val deload: Boolean = false,
)

/** The top set of a session that made a record (SPEC, Records): [weightKg] × [reps]. */
data class ReviewRecord(val kind: Record, val weightKg: Double, val reps: Int)

/** A Change accepted in block [blockId]: its [summary]. */
data class ReviewChange(val blockId: String, val summary: String)

/** An exercise's sessions oldest first: on [machine] for machines (`Cable left · Harbour`, ADR 0002). */
data class ReviewLine(val exercise: String, val machine: String? = null, val points: List<ReviewPoint>)

/** A training day's turn of program day [day] (`Day A`) in program [week], trained or [missed]; [easy] in an easy week. */
data class ReviewTurn(val date: LocalDate, val week: Int, val day: String, val missed: Boolean = false, val easy: Boolean = false)

/** A finished session of the run in program [week], with the work sets it gave each muscle (SPEC, Weekly sets). */
data class ReviewSession(val date: LocalDate, val week: Int, val sets: Map<String, Double> = emptyMap())

/**
 * What a block review reads: the run's [program], every exercise's sessions, the run's own
 * [sessions] and, on training days, its [turns], oldest first. [muscles] is what a set of each
 * exercise counts for each muscle (1 primary, 0.5 secondary).
 */
data class ReviewLog(
    val program: Program,
    val lines: List<ReviewLine> = emptyList(),
    val turns: List<ReviewTurn> = emptyList(),
    val sessions: List<ReviewSession> = emptyList(),
    val muscles: Map<String, Map<String, Double>> = emptyMap(),
    /** The accepted Changes, oldest first. */
    val changes: List<ReviewChange> = emptyList(),
    val bodyweights: List<Bodyweight> = emptyList(),
    val goals: List<LiftGoal> = emptyList(),
)

/** A lift's estimate from [startKg] to [endKg] over a block, on [machine] for machines. */
data class LiftChange(val exercise: String, val machine: String?, val startKg: Double, val endKg: Double) {
    val gainKg: Double get() = endKg - startKg
}

/** How the sessions came (SPEC, Program → Block review → Turning up). */
sealed interface TurningUp {
    /**
     * [done] of the [of] training days trained, the days [missed] by day (most first), and the
     * calendar [weeks] taken for the [written] ones: the program weeks trained [again], and the
     * [easyWeeks].
     */
    data class OnTrainingDays(
        val done: Int,
        val of: Int,
        val missed: List<Pair<String, Int>>,
        val weeks: Int,
        val written: Int,
        val again: List<Int>,
        val easyWeeks: Int,
    ) : TurningUp

    /** Without training days: the [days] done, [from] the first to the last. */
    data class InSequence(val days: Int, val from: LocalDate, val to: LocalDate) : TurningUp
}

/** A muscle's average weekly sets as [done] and as [written]. */
data class MuscleVolume(val muscle: String, val done: Double, val written: Double)

/** A record [exercise] set on [date], on [machine] for machines. */
data class SetRecord(val exercise: String, val machine: String?, val date: LocalDate, val record: ReviewRecord)

/** Where a lift goal stands: the [estimateKg] against it, and with a date the [pace]. */
data class GoalStanding(val goal: LiftGoal, val estimateKg: Double, val pace: GoalPace?)

/** The gain carried on in a straight line to the goal's date, an estimate. */
sealed interface GoalPace {
    /** The goal reached on [date], by its own. */
    data class OnPace(val date: LocalDate) : GoalPace

    /** [kg] short of it on its date. */
    data class Short(val kg: Double) : GoalPace
}

/** How a block went (SPEC, Program → Block review). */
data class Review(
    val lifts: List<LiftChange>,
    val turningUp: TurningUp?,
    val volume: List<MuscleVolume>,
    /** The summaries of the Changes accepted in it. */
    val changes: List<String>,
    val records: List<SetRecord>,
    /** The first bodyweight logged in it → the last; null with none. */
    val bodyweight: Pair<Double, Double>?,
    /** The lift goals of exercises with an estimate by its end. */
    val goals: List<GoalStanding>,
    /** From its first turn or session to its last; null with none. */
    val dates: ClosedRange<LocalDate>? = null,
)

/** Block reviews, worked out from the log with no Claude call. */
object BlockReviews {
    /** How [block] went. */
    fun of(log: ReviewLog, block: Block): Review = review(log, block.weekStart..block.weekEnd)

    /**
     * How the program went up to the end of [block], as far as it ran: the program review's column
     * beside it. Its written volume counts only the weeks it reached.
     */
    fun program(log: ReviewLog, block: Block): Review {
        val reached = (log.turns.map { it.week } + log.sessions.map { it.week }).filter { it <= block.weekEnd }.maxOrNull() ?: block.weekEnd
        return review(log, 1..reached)
    }

    /**
     * [program]'s blocks finished with the next day at [position]: those it is past. On training
     * days the rotation moves it on once the last planned day is trained or its week is over;
     * without, once the last day is.
     */
    fun finished(program: Program, position: Position): List<Block> = program.blocks.filter { it.weekEnd < position.week }

    private fun review(log: ReviewLog, weeks: IntRange): Review {
        val blocks = log.program.blocks.filter { it.weekStart in weeks }.map { it.id }.toSet()
        val dates = dates(log, weeks)
        val lifts = lifts(log.lines, weeks, easedThroughout(log.program))
        return Review(
            lifts = lifts.map { it.change },
            turningUp = turningUp(log, weeks),
            volume = volume(log, weeks),
            changes = log.changes.filter { it.blockId in blocks }.map { it.summary },
            records = log.lines.flatMap { line -> line.points.filter { it.week in weeks }.mapNotNull { p -> p.record?.let { SetRecord(line.exercise, line.machine, p.date, it) } } }
                .sortedBy { it.date },
            bodyweight = dates?.let { span -> log.bodyweights.filter { it.date in span }.sortedBy { it.date }.takeIf { it.isNotEmpty() } }
                ?.let { it.first().kg to it.last().kg },
            goals = log.goals.mapNotNull { goal -> standing(goal, log.lines, lifts, dates) },
            dates = dates,
        )
    }

    /**
     * [goal] against its exercise's estimate at the end of [dates]: its lift's in [lifts], else the
     * latest before. With a date, the lift's gain over its sessions carried on to it.
     */
    private fun standing(goal: LiftGoal, lines: List<ReviewLine>, lifts: List<Lift>, dates: ClosedRange<LocalDate>?): GoalStanding? {
        val lift = lifts.firstOrNull { it.change.exercise == goal.exerciseId }
        val estimate = lift?.change?.endKg
            ?: lines.filter { it.exercise == goal.exerciseId }.flatMap { it.points }
                .filter { it.estimateKg != null && (dates == null || it.date <= dates.endInclusive) }.maxByOrNull { it.date }?.estimateKg
            ?: return null
        val by = goal.by
        if (by == null || lift == null || estimate >= goal.kg) return GoalStanding(goal, estimate, null)
        val days = ChronoUnit.DAYS.between(lift.from, lift.to)
        val perDay = if (days > 0) lift.change.gainKg / days else 0.0
        if (perDay <= 0 || by <= lift.to) return GoalStanding(goal, estimate, GoalPace.Short(goal.kg - estimate))
        val needed = kotlin.math.ceil((goal.kg - estimate) / perDay - SAME_KG).toLong()
        val reached = lift.to.plusDays(needed)
        val pace = if (reached <= by) GoalPace.OnPace(reached) else GoalPace.Short(goal.kg - (estimate + perDay * ChronoUnit.DAYS.between(lift.to, by)))
        return GoalStanding(goal, estimate, pace)
    }

    /** A lift's [change] over its counted sessions [from] the first [to] the last. */
    private data class Lift(val change: LiftChange, val from: LocalDate, val to: LocalDate)

    /** Kilos this close are the same: rounding is never a gain, and never moves a date. */
    const val SAME_KG = 0.001

    /** From the first turn or session in [weeks] to the last; null with none. */
    private fun dates(log: ReviewLog, weeks: IntRange): ClosedRange<LocalDate>? {
        val dates = log.turns.filter { it.week in weeks }.map { it.date } + log.sessions.filter { it.week in weeks }.map { it.date }
        return if (dates.isEmpty()) null else dates.min()..dates.max()
    }

    /**
     * Each muscle's sets: those done in [weeks] over the calendar weeks they took, against the
     * program's [weeks] as written (with their week overrides, not Changes), the one furthest under
     * as written first, then in body order.
     */
    private fun volume(log: ReviewLog, weeks: IntRange): List<MuscleVolume> {
        val taken = weeksTaken(log, weeks)
        val done = log.sessions.filter { it.week in weeks }.flatMap { it.sets.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val written = mutableMapOf<String, Double>()
        weeks.forEach { week ->
            val block = log.program.blockAt(week) ?: return@forEach
            block.days.indices.mapNotNull { log.program.plannedDay(Position(week, it)) }.flatMap { it.slots }.forEach { slot ->
                log.muscles[slot.exercise].orEmpty().forEach { (muscle, per) -> written[muscle] = (written[muscle] ?: 0.0) + per * slot.sets.size }
            }
        }
        return (done.keys + written.keys).sortedBy { m -> Muscles.indexOf(m).let { if (it < 0) Int.MAX_VALUE else it } }.map { m ->
            MuscleVolume(m, if (taken == 0) 0.0 else (done[m] ?: 0.0) / taken, (written[m] ?: 0.0) / weeks.count())
        }.sortedBy { it.done - it.written }
    }

    /** The calendar weeks [weeks] took: from the first turn or session in them to the last. */
    private fun weeksTaken(log: ReviewLog, weeks: IntRange): Int {
        val dates = dates(log, weeks) ?: return 0
        return (ChronoUnit.WEEKS.between(mondayOf(dates.start), mondayOf(dates.endInclusive)) + 1).toInt()
    }

    /** The turns in [weeks]: each counted once, its calendar week a week taken. Without training days, the sessions. */
    private fun turningUp(log: ReviewLog, weeks: IntRange): TurningUp? {
        val turns = log.turns.filter { it.week in weeks }
        if (turns.isEmpty()) {
            val sessions = log.sessions.filter { it.week in weeks }.takeIf { it.isNotEmpty() } ?: return null
            return TurningUp.InSequence(sessions.size, sessions.minOf { it.date }, sessions.maxOf { it.date })
        }
        val easy = turns.filter { it.easy }.map { mondayOf(it.date) }.toSet()
        // A program week trained in more than one calendar week, an easy week aside, came again.
        val again = turns.filter { !it.easy }.groupBy { it.week }.filter { (_, own) -> own.map { mondayOf(it.date) }.distinct().size > 1 }.keys.sorted()
        return TurningUp.OnTrainingDays(
            done = turns.count { !it.missed },
            of = turns.size,
            missed = turns.filter { it.missed }.groupingBy { it.day }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value },
            weeks = weeksTaken(log, weeks),
            written = weeks.last - weeks.first + 1,
            again = again,
            easyWeeks = easy.size,
        )
    }

    private fun mondayOf(date: LocalDate) = Rotations.mondayOf(date)

    /**
     * The weeks of blocks the plan eases in every week (as Ramp-in): their eased sessions count,
     * or the block would have no lifts.
     */
    private fun easedThroughout(program: Program): Set<Int> = program.blocks.filter { block ->
        (block.weekStart..block.weekEnd).all { week -> block.days.indices.all { program.plannedDay(Position(week, it))?.eased == true } }
    }.flatMap { it.weekStart..it.weekEnd }.toSet()

    /**
     * Each exercise trained at least twice in [weeks], deload and easy-week sessions left out, and
     * eased ones outside the [eased] blocks' weeks: its estimate before the first (after it, for a
     * lift new there) → after the last. A machine exercise reads the machine with the most of them,
     * the one used last on a tie. Biggest gain first.
     */
    private fun lifts(lines: List<ReviewLine>, weeks: IntRange, eased: Set<Int>): List<Lift> =
        lines.groupBy { it.exercise }.values.mapNotNull { machines ->
            val (line, counted) = machines.map { it to it.counted(weeks, eased) }
                .maxWithOrNull(compareBy({ it.second.size }, { it.second.lastOrNull()?.date })) ?: return@mapNotNull null
            if (counted.size < 2) return@mapNotNull null
            val start = line.points.lastOrNull { it.estimateKg != null && it.date < counted.first().date }?.estimateKg ?: counted.first().estimateKg!!
            Lift(LiftChange(line.exercise, line.machine, start, counted.last().estimateKg!!), counted.first().date, counted.last().date)
        }.sortedByDescending { it.change.gainKg }

    /** The sessions in [weeks] that count for a lift: with an estimate, no deload, eased only in an [eased] block's week. */
    private fun ReviewLine.counted(weeks: IntRange, eased: Set<Int>) =
        points.filter { it.estimateKg != null && it.week in weeks && !it.deload && (!it.eased || it.week in eased) }

    /**
     * The first answer of Check in about the next block, or with [newProgram] of New program. The
     * review goes in the check-in's context ([context]), not in the answer.
     */
    fun opening(program: Program, block: Block, newProgram: Boolean = false): String =
        if (newProgram) "${program.title} is done. I want a new program for what comes next."
        else "${block.name} is done. Here's how it went: look at it and the next block."

    /**
     * [review] of [block] as text for the check-in's context, the [whole] program after it on the
     * program review, then the next block. Exercises are named from [names].
     */
    fun context(program: Program, block: Block, review: Review, whole: Review?, names: Map<String, String>): String = buildString {
        section("How ${block.name} went", block.weekStart..block.weekEnd, review, names)
        whole?.let { appendLine(); section("How the whole program went", 1..block.weekEnd, it, names) }
        program.blocks.getOrNull(program.blocks.indexOf(block) + 1)?.let { appendLine("Next block: ${it.name}") }
    }.trimEnd()

    private fun StringBuilder.section(title: String, weeks: IntRange, review: Review, names: Map<String, String>) {
        fun name(id: String) = names[id] ?: id
        appendLine("## $title (weeks ${if (weeks.first == weeks.last) "${weeks.first}" else "${weeks.first}–${weeks.last}"})")
        if (review.lifts.isNotEmpty()) {
            appendLine("Lifts, estimate before → after:")
            review.lifts.forEach { l ->
                appendLine("- ${name(l.exercise)}${l.machine?.let { " ($it)" }.orEmpty()}: ${formatKg(l.startKg)} → ${formatKg(l.endKg)} kg (${signedKg(l.gainKg)})")
            }
        }
        when (val t = review.turningUp) {
            is TurningUp.OnTrainingDays -> {
                val missed = t.missed.joinToString(", ") { (day, n) -> "$day missed ${if (n == 1) "once" else "$n times"}" }
                val extra = t.again.map { "week $it again" } + List(t.easyWeeks) { "an easy week" }
                appendLine(
                    "Turning up: ${t.done} of ${t.of} training days" + (if (missed.isEmpty()) "" else "; $missed") +
                        (if (t.weeks == t.written && extra.isEmpty()) "" else "; took ${t.weeks} weeks, planned ${t.written}" + (if (extra.isEmpty()) "" else ": ${extra.joinToString(", ")}")),
                )
            }
            is TurningUp.InSequence -> appendLine("Turning up: ${t.days} days done, ${t.from} to ${t.to}")
            null -> {}
        }
        if (review.volume.isNotEmpty()) {
            appendLine("Weekly sets, done → as written:")
            review.volume.forEach { appendLine("- ${it.muscle}: ${formatSets(it.done)} → ${formatSets(it.written)}") }
        }
        if (review.changes.isNotEmpty()) appendLine("Changes accepted: ${review.changes.joinToString("; ")}")
        if (review.records.isNotEmpty()) {
            appendLine("Records: " + review.records.joinToString("; ") { r -> "${name(r.exercise)}${r.machine?.let { " ($it)" }.orEmpty()} ${formatKg(r.record.weightKg)} kg × ${r.record.reps} on ${r.date}" })
        }
        review.bodyweight?.let { (from, to) -> appendLine("Bodyweight: ${formatKg(from)} → ${formatKg(to)} kg") }
        if (review.goals.isNotEmpty()) {
            appendLine("Lift goals:")
            review.goals.forEach { g ->
                val pace = when (val p = g.pace) {
                    is GoalPace.OnPace -> "; on pace for ${p.date} at this block's rate (an estimate)"
                    is GoalPace.Short -> "; ${formatKg(roundHalf(p.kg))} kg short at this block's rate (an estimate)"
                    null -> ""
                }
                appendLine("- ${name(g.goal.exerciseId)}: ${formatKg(g.estimateKg)} of ${formatKg(g.goal.kg)} kg${g.goal.by?.let { " by $it" }.orEmpty()}$pace")
            }
        }
    }

    /** A gain in kilos: `+6`, `±0`, `−2.5`. */
    fun signedKg(kg: Double): String = when {
        kotlin.math.abs(kg) < SAME_KG -> "±0"
        kg > 0 -> "+${formatKg(kg)}"
        else -> "−${formatKg(-kg)}"
    }

    /** Average sets to the half: `2.5`, `3`. */
    fun formatSets(sets: Double): String = formatKg(roundHalf(sets))

    /** [x] to the nearest half. */
    fun roundHalf(x: Double): Double = kotlin.math.round(x * 2) / 2
}
