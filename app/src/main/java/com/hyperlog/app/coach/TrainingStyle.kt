package com.hyperlog.app.coach

import kotlin.math.roundToInt

/**
 * How you like to train, carried by every check-in (SPEC, Coaching → Training style): the
 * [targets], [aboutMe] and [howITrain], with each principle's sentence as the app ships it
 * ([sentences], from the asset), which is what Claude reads and you never edit.
 */
data class TrainingStyle(
    val targets: TrainingTargets = TrainingTargets(),
    val aboutMe: AboutMe = AboutMe(),
    val howITrain: HowITrain = HowITrain(),
    val sentences: Map<Principle, String> = emptyMap(),
)

/**
 * How I train's principles (GLOSSARY), by their [key] in the asset. A [fixed] one is a rule Claude
 * always gets; the others are switches, on unless you switch them off.
 */
enum class Principle(val key: String, val fixed: Boolean = false) {
    FormBeforeLoad("form", fixed = true),
    NoLoads("no_load", fixed = true),
    SwapAroundPain("pain", fixed = true),
    Alternatives("alternatives", fixed = true),
    StretchBiased("stretch"),
    Stable("stable"),
    Deload("deload"),
}

/**
 * How I train as you set it: the principles switched [off] (only those are kept, so a principle
 * added later starts on) and one optional line, [anythingElse].
 */
data class HowITrain(val off: Set<Principle> = emptySet(), val anythingElse: String? = null) {
    /** Whether Claude gets [principle]: a fixed one always. */
    fun on(principle: Principle) = principle.fixed || principle !in off
}

/** The principles' sentences, as the asset (`coach/how-i-train.json`) holds them, one per key. */
object Principles {
    const val ASSET = "coach/how-i-train.json"

    /** Each principle's sentence by its key; a key the app doesn't know is left out. */
    fun parse(json: String): Map<Principle, String> {
        val o = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        return Principle.entries.mapNotNull { p ->
            (o[p.key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.let { p to it }
        }.toMap()
    }
}

/** A range of whole numbers, written `8-14` in files and `8–14` in text. */
data class NumberRange(val low: Int, val high: Int) {
    val key: String get() = "$low-$high"

    /** As weekly sets, the raised range (GLOSSARY): from the top to half as much again, rounded. */
    fun raised() = NumberRange(high, (high * 1.5).roundToInt())

    /** As weekly sets, the maintenance range (GLOSSARY): from half the bottom, rounded up, to the bottom. */
    fun maintenance() = NumberRange((low + 1) / 2, low)

    override fun toString() = "$low–$high"
}

enum class TrainingGoal(val key: String) { Hypertrophy("hypertrophy"), Strength("strength"), Both("both") }

/** How volume runs across a block. */
enum class AcrossBlock(val key: String) { Steady("steady"), Rising("rising") }

/** How often each muscle is trained a week. */
enum class Frequency(val key: String) { Once("1"), Twice("2"), TwoToThree("2-3"), ThreeOrMore("3+") }

/** Which lifts may go to failure (0 RIR). */
enum class FailureRule(val key: String) { IsolationAndMachines("isolation_and_machines"), Never("never"), AnyLift("any") }

/**
 * The training targets (GLOSSARY): standing, so every new program starts from them. [volume] is
 * work sets per muscle a week; [compound] and [isolation] are rep ranges.
 */
data class TrainingTargets(
    val goal: TrainingGoal = TrainingGoal.Hypertrophy,
    val volume: NumberRange = NumberRange(8, 14),
    val acrossBlock: AcrossBlock = AcrossBlock.Steady,
    val frequency: Frequency = Frequency.TwoToThree,
    val failure: FailureRule = FailureRule.IsolationAndMachines,
    val calibration: Boolean = true,
    val compound: NumberRange = NumberRange(5, 10),
    val isolation: NumberRange = NumberRange(8, 15),
)

enum class AgeBand(val key: String, val label: String) {
    Under30("under_30", "Under 30"),
    Thirties("30_39", "30–39"),
    Forties("40_49", "40–49"),
    Fifties("50_59", "50–59"),
    SixtyAndOver("60_plus", "60 and over"),
}

/** Where you are now: how fast you progress, not years trained. */
enum class Progress(val key: String, val label: String) {
    StillLearning("still_learning", "Still learning the lifts"),
    Weeks("weeks", "Progress takes weeks"),
    Months("months", "Progress takes months"),
}

enum class BodyArea(val key: String, val label: String) {
    Neck("neck", "Neck"),
    Shoulders("shoulders", "Shoulders"),
    Elbows("elbows", "Elbows"),
    WristsAndHands("wrists_and_hands", "Wrists and hands"),
    UpperBack("upper_back", "Upper back"),
    LowerBack("lower_back", "Lower back"),
    Hips("hips", "Hips"),
    Knees("knees", "Knees"),
    AnklesAndFeet("ankles_and_feet", "Ankles and feet"),
}

/**
 * About me (GLOSSARY), every field optional: the [age] band, how fast you [progress] and whether
 * you are [comingBack] from a break, [aches] by body area with a note each, exercise ids to
 * [avoid] and [favour], and [anythingElse].
 */
data class AboutMe(
    val age: AgeBand? = null,
    val progress: Progress? = null,
    val comingBack: Boolean = false,
    val aches: Map<BodyArea, String> = emptyMap(),
    val avoid: List<String> = emptyList(),
    val favour: List<String> = emptyList(),
    val anythingElse: String? = null,
)

/**
 * A program's own training targets (`targets` in docs/program-format.md): each one left out
 * (null) follows the standing training targets.
 */
data class ProgramTrainingTargets(
    val goal: TrainingGoal? = null,
    val volume: NumberRange? = null,
    val acrossBlock: AcrossBlock? = null,
    val frequency: Frequency? = null,
    val failure: FailureRule? = null,
    val calibration: Boolean? = null,
    val compound: NumberRange? = null,
    val isolation: NumberRange? = null,
) {
    /** These over [standing]: the training targets the program is written for. */
    fun over(standing: TrainingTargets) = TrainingTargets(
        goal ?: standing.goal, volume ?: standing.volume, acrossBlock ?: standing.acrossBlock, frequency ?: standing.frequency,
        failure ?: standing.failure, calibration ?: standing.calibration, compound ?: standing.compound, isolation ?: standing.isolation,
    )
}
