package com.hyperlog.app.session

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder

enum class Equipment { Barbell, Dumbbell, Machine, Cable, Bodyweight }

data class Exercise(val id: String, val name: String, val equipment: Equipment) {
    /** Until gyms and equipment exist (#4): the global dumbbell ladder, else 2.5 kg steps. */
    val ladder: WeightLadder
        get() = if (equipment == Equipment.Dumbbell) WeightLadder.Dumbbells else WeightLadder.Default
}

/** One exercise in a session, with what has been logged and what was done last time. */
data class SessionExercise(
    val id: Long,
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    val lastTime: List<LoggedSet>,
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
) {
    val current: SessionExercise? get() = exercises.firstOrNull { it.id == currentId }
}

data class ExerciseSummary(val name: String, val sets: List<LoggedSet>)

data class SessionSummary(val startedAt: Long, val finishedAt: Long, val exercises: List<ExerciseSummary>)

/**
 * The small list ad-hoc sessions pick from until the exercise library (#3) replaces it. Ids
 * follow the legacy library's so its merge can keep them.
 */
val StarterExercises = listOf(
    Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell),
    Exercise("db_bench_press", "DB Bench Press", Equipment.Dumbbell),
    Exercise("db_incline_press", "DB Incline Press", Equipment.Dumbbell),
    Exercise("seated_db_shoulder_press", "Seated DB Shoulder Press", Equipment.Dumbbell),
    Exercise("cable_lateral_raise", "Cable Lateral Raise", Equipment.Cable),
    Exercise("lat_pulldown", "Lat Pulldown", Equipment.Cable),
    Exercise("seated_cable_row", "Seated Cable Row", Equipment.Cable),
    Exercise("chest_supported_db_row", "Chest-Supported DB Row", Equipment.Dumbbell),
    Exercise("pull_up", "Pull-up", Equipment.Bodyweight),
    Exercise("back_squat", "Back Squat", Equipment.Barbell),
    Exercise("romanian_deadlift", "Romanian Deadlift", Equipment.Barbell),
    Exercise("leg_press", "Leg Press", Equipment.Machine),
    Exercise("leg_extension", "Leg Extension", Equipment.Machine),
    Exercise("seated_leg_curl", "Seated Leg Curl", Equipment.Machine),
    Exercise("cable_triceps_pushdown", "Cable Triceps Pushdown", Equipment.Cable),
    Exercise("db_curl", "DB Curl", Equipment.Dumbbell),
)
