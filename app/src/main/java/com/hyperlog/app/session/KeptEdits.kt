package com.hyperlog.app.session

import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.EditedExercise
import com.hyperlog.app.coach.KeepEdits

/**
 * This program session's edits as a Change for the rest of the block, with [summary]; null in an
 * ad-hoc session or when nothing it keeps was edited. Slots of the day missing from the session
 * were taken out of it.
 */
fun ActiveSession.keptEdits(summary: String): Change? {
    val day = day ?: return null
    val present = exercises.mapNotNull { it.slot?.id }.toSet()
    val rows = exercises.map { EditedExercise(it.slot?.id, it.exercise.id, removed = false, sets = it.plan, logged = it.sets) } +
        day.slots.filter { it.id !in present }.map { EditedExercise(it.id, it.exercise, removed = true, sets = null, logged = emptyList()) }
    return KeepEdits.change(day, rows, summary)
}
