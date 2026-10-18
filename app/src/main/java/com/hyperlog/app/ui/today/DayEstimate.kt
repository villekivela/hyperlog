package com.hyperlog.app.ui.today

import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.PastSessionItem

/** The seconds the estimate counts, in one place to tune after real sessions (SPEC, Screens → Today). */
internal object EstimateSeconds {
    const val WorkSet = 45
    const val WarmupSet = 60
    const val Changeover = 90
    const val PerDrop = 30
    const val MyoReps = 45
    const val LengthenedPartials = 15

    /** Warm-up sets counted with no weight to ramp from: a main lift, and anything else. */
    const val MainLiftWarmups = 3
    const val OtherWarmups = 1

    /** How many of a day's latest finished sessions its estimate comes from. */
    const val PastSessions = 3
}

/** Bench press, and the squat, hinge (RDL) and horizontal pull (row) patterns: the lifts that get a full warm-up ramp. */
private fun Exercise.isMainLift() =
    pattern == MovementPattern.Squat || pattern == MovementPattern.Hinge || pattern == MovementPattern.HorizontalPull ||
        (pattern == MovementPattern.HorizontalPush && name.contains("bench press", ignoreCase = true))

private fun Technique?.seconds(): Int = when (this) {
    is Technique.Dropset -> drops * EstimateSeconds.PerDrop
    is Technique.MyoReps -> EstimateSeconds.MyoReps
    is Technique.LengthenedPartials -> EstimateSeconds.LengthenedPartials
    else -> 0
}

/**
 * About how long [day] takes, to the nearest 5 minutes: the median of the latest 3 [finished]
 * sessions of the same program day, else the whole session counted (warm-ups, changeovers,
 * techniques, work and rest as the session gives it).
 */
internal fun aboutMinutes(day: PlannedDay, library: List<Exercise>, finished: List<PastSessionItem>): Int {
    val past = finished.filter { it.position?.day == day.position.day }
        .sortedByDescending { it.startedAt }.take(EstimateSeconds.PastSessions)
        .map { (it.finishedAt - it.startedAt) / 60_000.0 }.sorted()
    val minutes = if (past.isNotEmpty()) {
        if (past.size % 2 == 1) past[past.size / 2] else (past[past.size / 2 - 1] + past[past.size / 2]) / 2
    } else {
        countedSeconds(day, library) / 60.0
    }
    return Math.round(minutes / 5).toInt() * 5
}

internal fun countedSeconds(day: PlannedDay, library: List<Exercise>): Int {
    val byId = library.associateBy { it.id }
    var seconds = 0
    val groups = day.slots.groupBy { it.group }.values
    groups.forEachIndexed { g, slots ->
        seconds += EstimateSeconds.Changeover
        slots.forEach { slot ->
            val main = byId[slot.exercise]?.isMainLift() == true
            seconds += EstimateSeconds.WarmupSet * if (main) EstimateSeconds.MainLiftWarmups else EstimateSeconds.OtherWarmups
            slot.sets.forEach { seconds += EstimateSeconds.WorkSet + it.technique.seconds() }
        }
        // Rest comes after a set, or after a superset's round (the later partner's), and not after the day's last set.
        val rounds = slots.maxOf { it.sets.size }
        val last = g == groups.size - 1
        for (round in 0 until rounds) {
            if (last && round == rounds - 1) continue
            seconds += slots.last { round < it.sets.size }.sets[round].rest
        }
    }
    return seconds
}
