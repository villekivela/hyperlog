package com.hyperlog.app.session

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightCheck
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.library.Exercise

/** One exercise in a session, with what has been logged and what was done last time. */
data class SessionExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    val lastTime: List<LoggedSet>,
    /** The weights −/+ and the pad's strip move over. */
    val ladder: WeightLadder,
    /** False while [ladder] only stands in for equipment the session doesn't know yet (#5). */
    val ladderKnown: Boolean,
)

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
) {
    val current: SessionExercise? get() = exercises.firstOrNull { it.id == currentId }
}

data class ExerciseSummary(val name: String, val sets: List<LoggedSet>)

data class SessionSummary(val sessionId: Long, val startedAt: Long, val finishedAt: Long, val exercises: List<ExerciseSummary>)
