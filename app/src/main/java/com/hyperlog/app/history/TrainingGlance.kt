package com.hyperlog.app.history

import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.VolumeStatus
import com.hyperlog.app.library.Exercise
import java.time.LocalDate
import kotlin.math.abs

/** One muscle's [sets] in the week a glance shows, against its [range]. */
data class MuscleWeek(val muscle: String, val sets: Double, val range: NumberRange, val status: VolumeStatus)

/** A week's [workSets], for the trend. */
data class WeekTotal(val start: LocalDate, val workSets: Int)

/** A lift whose estimate went [fromKg] to [toKg] over the last few weeks. */
data class MovingLift(val exercise: Exercise, val fromKg: Double, val toKg: Double) {
    val percent: Double get() = (toKg - fromKg) / fromKg * 100
}

/**
 * Training at a glance, as Progress opens on it (SPEC, Screens → History): the muscles of the
 * week shown ([week]; null with nothing in the last 8 weeks), the [weeks] oldest first and the
 * lifts [moving] most.
 */
data class TrainingGlance(val week: LocalDate?, val muscles: List<MuscleWeek>, val weeks: List<WeekTotal>, val moving: List<MovingLift>) {
    /** How many muscles are within their range. */
    val onTarget: Int get() = muscles.count { it.status == VolumeStatus.Within }

    /** The muscles trained in the week, the one furthest short of its range first. */
    val shortFirst: List<MuscleWeek> get() = muscles.filter { it.sets > 0 }.sortedBy { it.sets / it.range.low.coerceAtLeast(1) }

    /** The muscles with no sets in the week. */
    val untrained: List<MuscleWeek> get() = muscles.filter { it.sets == 0.0 }
}

/**
 * The glance on [today] from [weeks] (newest first) and [trends], each muscle measured against
 * [range]. It shows the week [picked], else the latest with any sets.
 */
fun trainingGlance(
    weeks: List<WeekSets>,
    trends: List<ExerciseTrend>,
    today: LocalDate,
    picked: LocalDate? = null,
    /** The muscles to measure: the program's own, without those it only touches as a secondary. Null for all. */
    only: Set<String>? = null,
    range: (String) -> NumberRange,
): TrainingGlance {
    val shown = weeks.firstOrNull { it.start == picked } ?: weeks.firstOrNull { it.sets.isNotEmpty() }
    // In the order the shown week has them, then any it missed, newest week first.
    val trained = (listOfNotNull(shown) + weeks).flatMap { it.sets.keys }.distinct()
    val muscles = if (only == null) trained else trained.filter { it in only } + (only - trained.toSet())
    return TrainingGlance(
        shown?.start,
        muscles.map { muscle ->
            val sets = shown?.sets?.get(muscle) ?: 0.0
            val measured = range(muscle)
            MuscleWeek(muscle, sets, measured, measured.statusOf(sets))
        },
        weeks.reversed().map { WeekTotal(it.start, it.workSets) },
        trends.mapNotNull { it.moving(today) }.sortedByDescending { abs(it.percent) }.take(MovingShown),
    )
}

/**
 * How this lift moved, from its estimate about [MovingDays] before the latest (its first, with
 * less history) to the latest; null when it hasn't moved, or was last done before that window.
 * Only one line can be read: two machines' estimates aren't comparable (ADR 0002).
 */
private fun ExerciseTrend.moving(today: LocalDate): MovingLift? {
    val points = lines.singleOrNull()?.points.orEmpty().filter { it.estimateKg != null }
    val latest = points.lastOrNull() ?: return null
    if (latest.date.isBefore(today.minusDays(MovingDays))) return null
    val from = points.lastOrNull { !it.date.isAfter(latest.date.minusDays(MovingDays)) } ?: points.first()
    if (from === latest) return null
    return MovingLift(exercise, from.estimateKg!!, latest.estimateKg!!).takeIf { abs(it.percent) >= MovingPercent }
}

private const val MovingDays = 28L
private const val MovingPercent = 1.0
private const val MovingShown = 3

private fun NumberRange.statusOf(sets: Double) = when {
    sets < low -> VolumeStatus.Under
    sets > high -> VolumeStatus.Over
    else -> VolumeStatus.Within
}
