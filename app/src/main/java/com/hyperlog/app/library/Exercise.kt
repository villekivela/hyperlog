package com.hyperlog.app.library

/** An exercise in the library. Names carry the implement ("DB Bench Press"), so a name alone is unambiguous. */
data class Exercise(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val pattern: MovementPattern,
    val grip: GripDemand,
    /** Primary muscles, the ones the exercise trains most. */
    val muscles: List<String>,
    val secondary: List<String> = emptyList(),
    /** A YouTube link, optionally with a timestamp. */
    val video: String? = null,
    /** The exercise's own technique cue. Program-specific cues live in the program's slots. */
    val note: String? = null,
    /** Hidden exercises stay in past sessions but aren't offered when adding one. */
    val hidden: Boolean = false,
) {

    /**
     * Done one side at a time (SPEC, Unilateral exercises), so a set can be split into left and
     * right. Read from the name, which carries it: `1-Arm`, `Single-Leg`, lunges, split squats, step-ups.
     */
    val unilateral: Boolean get() = UnilateralName.containsMatchIn(name)

    /** What the library list shows under the name: `Cable · Lats, Biceps`. */
    val summary: String get() = (listOf(equipment.label) + muscles).let { "${it.first()} · ${it.drop(1).joinToString(", ")}" }
}

private val UnilateralName = Regex("""\b(1|one|single)[- ](arm|leg)\b|\blunge|\bsplit squat|\bstep[- ]up""", RegexOption.IGNORE_CASE)

/** The kind of equipment an exercise uses. [key] is its name in the library and program files. */
enum class Equipment(val key: String, val label: String) {
    Barbell("barbell", "Barbell"),
    Dumbbell("dumbbell", "Dumbbell"),
    Machine("machine", "Machine"),
    Cable("cable", "Cable"),
    SmithMachine("smith_machine", "Smith machine"),
    EzBar("ez_bar", "EZ bar"),
    TrapBar("trap_bar", "Trap bar"),
    SafetyBar("safety_bar", "Safety bar"),
    Plate("plate", "Plate"),
    Bodyweight("bodyweight", "Bodyweight"),
    WeightedBodyweight("weighted_bodyweight", "Weighted bodyweight"),
    ;

    /**
     * Machines, cables and Smith machines differ between gyms, so their exercises are done on an
     * equipment item at the gym and keep a history per item (ADR 0002). Everything else is a free
     * weight, with one history across gyms.
     */
    val perMachine: Boolean get() = this == Machine || this == Cable || this == SmithMachine

    companion object {
        fun ofKey(key: String) = entries.first { it.key == key }
    }
}

/** What a movement does. The coach swaps an exercise for others with the same pattern. */
enum class MovementPattern(val key: String, val label: String) {
    HorizontalPush("horizontal_push", "Horizontal push"),
    VerticalPush("vertical_push", "Vertical push"),
    HorizontalPull("horizontal_pull", "Horizontal pull"),
    VerticalPull("vertical_pull", "Vertical pull"),
    Squat("squat", "Squat"),
    Lunge("lunge", "Lunge"),
    Hinge("hinge", "Hinge"),
    HipThrust("hip_thrust", "Hip thrust"),
    KneeExtension("knee_extension", "Knee extension"),
    KneeFlexion("knee_flexion", "Knee flexion"),
    CalfRaise("calf_raise", "Calf raise"),
    HipAbduction("hip_abduction", "Hip abduction"),
    HipAdduction("hip_adduction", "Hip adduction"),
    ChestFly("chest_fly", "Chest fly"),
    LateralRaise("lateral_raise", "Lateral raise"),
    RearDeltFly("rear_delt_fly", "Rear delt fly"),
    Shrug("shrug", "Shrug"),
    ElbowFlexion("elbow_flexion", "Elbow flexion (curl)"),
    ElbowExtension("elbow_extension", "Elbow extension (triceps)"),
    Wrist("wrist", "Wrist"),
    CoreFlexion("core_flexion", "Core flexion"),
    Rotation("rotation", "Rotation"),
    Neck("neck", "Neck"),
    Hang("hang", "Hang"),
    ;

    companion object {
        fun ofKey(key: String) = entries.first { it.key == key }
    }
}

/** How hard an exercise works the hands, for the coach's "no grip" swaps. */
enum class GripDemand(val key: String, val label: String) {
    None("none", "None"),
    Low("low", "Low"),
    High("high", "High"),
    ;

    companion object {
        fun ofKey(key: String) = entries.first { it.key == key }
    }
}

/** The muscles the library names, in body order. */
val Muscles = listOf(
    "Chest", "Front Delts", "Side Delts", "Rear Delts", "Rotator Cuff",
    "Triceps Long Head", "Triceps Lateral Head", "Biceps", "Brachialis", "Forearm Flexors", "Forearm Extensors",
    "Lats", "Teres Major", "Traps", "Rhomboids", "Serratus Anterior", "Lower Back",
    "Upper Abs", "Lower Abs", "Obliques", "Hip Flexors",
    "Glutes", "Quads", "Hamstrings", "Adductors", "Hip Abductors", "Gastrocnemius", "Soleus", "Neck",
)

/**
 * The exercises whose words all appear in the name, equipment or primary muscles. Names starting
 * with the query come first, then names holding every word, then the rest ("lateral" finds the
 * lateral raises before the triceps work for the lateral head). A blank query keeps everything.
 */
fun List<Exercise>.search(query: String): List<Exercise> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    val q = query.trim().lowercase()
    return filter { e ->
        val haystack = (listOf(e.name, e.equipment.label) + e.muscles).joinToString(" ").lowercase()
        words.all { haystack.contains(it) }
    }.sortedBy { e ->
        val name = e.name.lowercase()
        when {
            name.startsWith(q) -> 0
            words.all { name.contains(it) } -> 1
            else -> 2
        }
    }
}
