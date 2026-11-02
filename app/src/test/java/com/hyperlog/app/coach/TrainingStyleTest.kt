package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The training style every check-in carries, in the API's context and Ask Claude's prompt alike (SPEC, What Claude is told). */
class TrainingStyleTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)
    private val plan = ActiveProgram(1, program, Position(1, 1), today = oct13)
    private val sentences = Principles.parse(File("src/main/assets/coach/how-i-train.json").readText())

    /** The style with the shipped principles' sentences. */
    private fun style(targets: TrainingTargets = TrainingTargets(), aboutMe: AboutMe = AboutMe(), howITrain: HowITrain = HowITrain()) =
        TrainingStyle(targets, aboutMe, howITrain, sentences)

    /** The API's context and Ask Claude's prompt, for [style]. */
    private fun both(style: TrainingStyle, plan: ActiveProgram? = this.plan) = listOf(
        CheckInContext.frame(plan, emptyList(), library.exercises, today = oct13, style = style).context,
        AskClaude.prompt("My elbow aches.", plan, emptyList(), library.exercises, today = oct13, style = style),
    )

    @Test
    fun withNothingSetEveryCheckInCarriesTheDefaults() {
        both(style()).forEach { text ->
            assertTrue(
                text,
                text.contains(
                    """
                    ## My training style
                    - Goal: hypertrophy, with my lift goals on top
                    - Volume: 8–14 work sets per muscle a week, steady across a block
                    - Frequency: each muscle 2–3 times a week
                    - How hard: last set 0–1 RIR, earlier sets 1–2 RIR
                    - Failure: isolation exercises and machine, cable and Smith machine compounds only
                    - Calibration: in each block's last week, unless it is a deload, the last set of each exercise safe to fail goes to 0 RIR; never a barbell squat, bench or deadlift
                    - Rep ranges: compounds 5–10, isolation 8–15
                    ## How I train
                    """.trimIndent() + "\n" + Principle.entries.joinToString("\n") { "- " + sentences.getValue(it) } + "\n## Writing by my training style",
                ),
            )
            // About me is all optional: with nothing in it, not even its heading.
            assertTrue(text, !text.contains("## About me"))
        }
    }

    @Test
    fun aboutMeGoesAsStructuredLinesWithEachExercisesName() {
        val me = AboutMe(
            age = AgeBand.Forties, progress = Progress.Months, comingBack = true,
            aches = mapOf(BodyArea.WristsAndHands to "sore when gripping"),
            avoid = listOf("barbell_squat", "gone_from_the_library"), favour = listOf("hack_squat"), anythingElse = "I train fasted in the morning",
        )
        both(style(aboutMe = me)).forEach { text ->
            assertTrue(
                text,
                text.contains(
                    """
                    ## About me
                    - Age: 40–49
                    - Where I am now: progress takes months; coming back from a break
                    - Aches: Wrists and hands: sore when gripping
                    - Avoid: barbell_squat (Barbell Back Squat), gone_from_the_library
                    - Favour: hack_squat (Hack Squat)
                    - Anything else: I train fasted in the morning
                    ## How I train
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test
    fun otherTargetsAreSaidInWords() {
        val targets = TrainingTargets(
            goal = TrainingGoal.Both, acrossBlock = AcrossBlock.Rising, frequency = Frequency.Once, failure = FailureRule.Never, calibration = false,
        )
        val text = both(TrainingStyle(targets)).first()
        assertTrue(text, text.contains("- Goal: hypertrophy and strength, with my lift goals on top"))
        assertTrue(text, text.contains("- Volume: 8–14 work sets per muscle a week, rising across a block: near 8 in its first week, adding sets each week toward 14, then a deload"))
        assertTrue(text, text.contains("- Frequency: each muscle once a week"))
        assertTrue(text, text.contains("- Failure: never; every set stops at 1 RIR or more"))
        assertTrue(text, text.contains("- Calibration: off"))
    }

    @Test
    fun howITrainCarriesTheFixedRulesAndEachPrincipleSwitchedOnWithAnythingElse() {
        val mine = HowITrain(off = setOf(Principle.StretchBiased, Principle.Deload), anythingElse = "I like supersets for arms")
        both(style(howITrain = mine)).forEach { text ->
            val section = text.substringAfter("## How I train\n").substringBefore("## ")
            // Form before load, no loads, swapping around pain and alternatives are fixed rules: always there.
            assertTrue(section, section.contains(sentences.getValue(Principle.FormBeforeLoad)))
            assertTrue(section, section.contains(sentences.getValue(Principle.NoLoads)))
            assertTrue(section, section.contains(sentences.getValue(Principle.SwapAroundPain)))
            assertTrue(section, section.contains(sentences.getValue(Principle.Alternatives)))
            assertTrue(section, section.contains(sentences.getValue(Principle.Stable)))
            // Switched off, a principle's sentence goes.
            assertTrue(section, !section.contains(sentences.getValue(Principle.StretchBiased)))
            assertTrue(section, !section.contains(sentences.getValue(Principle.Deload)))
            assertTrue(section, section.endsWith("- Anything else: I like supersets for arms\n"))
        }
        // A fixed rule can't be switched off.
        val forced = style(howITrain = HowITrain(off = setOf(Principle.FormBeforeLoad, Principle.SwapAroundPain)))
        assertTrue(both(forced).first().contains(sentences.getValue(Principle.FormBeforeLoad)))
        assertTrue(both(forced).first().contains(sentences.getValue(Principle.SwapAroundPain)))
        assertEquals(listOf(Principle.FormBeforeLoad, Principle.NoLoads, Principle.SwapAroundPain, Principle.Alternatives), Principle.entries.filter { it.fixed })
    }

    @Test
    fun theAssetHasASentenceForEveryPrinciple() {
        assertEquals(Principle.entries.toSet(), sentences.keys)
        sentences.values.forEach { assertTrue(it, it.isNotBlank() && !it.contains('\n')) }
    }

    @Test
    fun neverToFailureMakesRoomForCalibrationSetsWhenTheyAreOn() {
        // Calibration holds with Never (SPEC, Training targets), so the two lines mustn't contradict.
        val on = both(TrainingStyle(TrainingTargets(failure = FailureRule.Never, calibration = true))).first()
        assertTrue(on, on.contains("- Failure: never, apart from calibration sets; every other set stops at 1 RIR or more\n"))
        val off = both(TrainingStyle(TrainingTargets(failure = FailureRule.Never, calibration = false))).first()
        assertTrue(off, off.contains("- Failure: never; every set stops at 1 RIR or more\n"))
        // A program's own Never reads with the calibration it runs with.
        val own = plan.copy(program = program.copy(targets = ProgramTrainingTargets(failure = FailureRule.Never)))
        val text = both(TrainingStyle(), own).first()
        assertTrue(text, text.contains("Failure for this program: never, apart from calibration sets; every other set stops at 1 RIR or more\n"))
    }

    @Test
    fun theRunningProgramsFocusAndOwnTargetsGoWithIt() {
        val focused = plan.copy(
            program = program.copy(
                focus = listOf(com.hyperlog.app.library.MuscleGroup.Chest, com.hyperlog.app.library.MuscleGroup.Shoulders),
                targets = ProgramTrainingTargets(volume = NumberRange(14, 20)),
            ),
        )
        both(style(), focused).forEach { text ->
            // The raised and maintenance ranges come from the program's own volume, 14–20.
            assertTrue(text, text.contains("## Program: ${program.title}\nProgram focus: Chest, Shoulders (their muscles 20–30 sets a week, the rest 7–14)\nVolume for this program: 14–20\n"))
        }
        // With the standing 8–14: 14–21 and 4–8 (SPEC, The program focus).
        val standing = plan.copy(program = program.copy(focus = listOf(com.hyperlog.app.library.MuscleGroup.Legs)))
        assertTrue(both(TrainingStyle(), standing).first().contains("Program focus: Legs (their muscles 14–21 sets a week, the rest 4–8)"))
        // No focus, no line.
        assertTrue(!both(TrainingStyle()).first().contains("Program focus"))
    }

    @Test
    fun claudeIsToldToWriteByTheStyleAndAskForTheFocus() {
        both(TrainingStyle()).forEach { text ->
            assertTrue(text, text.contains("Bring anything up this program?"))
            // ask takes 2 to 4 answers: None and up to three groups, the rest (or two) typed.
            assertTrue(text, text.contains("offer None and up to three of the muscle groups"))
            assertTrue(text, text.contains("\"focus\""))
            // The ranges as numbers, from my volume.
            assertTrue(text, text.contains("the raised range (14–21)"))
            assertTrue(text, text.contains("the maintenance range (4–8)"))
            assertTrue(text, text.contains("keep the how hard, failure and calibration rules"))
            assertTrue(text, text.contains("Quads drop to 6 sets a week, under your 8–14."))
        }
    }

    @Test
    fun aProgramsOwnVolumeAndFocusSetTheRangesClaudeIsToldOf() {
        val own = plan.copy(program = program.copy(targets = ProgramTrainingTargets(volume = NumberRange(14, 20), acrossBlock = AcrossBlock.Rising)))
        val text = both(TrainingStyle(), own).first()
        assertTrue(text, text.contains("the raised range (20–30)"))
        assertTrue(text, text.contains("Quads drop to 12 sets a week, under your 14–20."))
        assertTrue(text, text.contains("Across a block for this program: rising across a block: near 14 in its first week, adding sets each week toward 20, then a deload"))
        // With a focus, a Change names the range the muscle leaves.
        val focused = plan.copy(program = program.copy(focus = listOf(com.hyperlog.app.library.MuscleGroup.Chest)))
        val withFocus = both(TrainingStyle(), focused).first()
        assertTrue(withFocus, withFocus.contains("Quads drop to 3 sets a week, under the maintenance range of 4–8."))
    }

    @Test
    fun askClaudesProgramFormatHasTheFocusAndTargets() {
        val text = ProgramFormat.text(library.exercises)
        assertTrue(text, text.contains("\"focus\"?, \"targets\"?"))
        assertTrue(text, text.contains("\"focus\": up to 2 muscle groups"))
        assertTrue(text, text.contains("\"volume\"?: work sets per muscle a week as a range like \"14-20\""))
    }
}
