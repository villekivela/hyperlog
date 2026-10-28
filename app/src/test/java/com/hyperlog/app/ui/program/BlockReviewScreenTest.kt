package com.hyperlog.app.ui.program

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.hyperlog.app.click
import com.hyperlog.app.history.LiftGoal
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.GoalPace
import com.hyperlog.app.program.GoalStanding
import com.hyperlog.app.program.Group
import com.hyperlog.app.program.LiftChange
import com.hyperlog.app.program.MuscleVolume
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.Review
import com.hyperlog.app.program.ReviewPage
import com.hyperlog.app.program.ReviewRecord
import com.hyperlog.app.program.SetRecord
import com.hyperlog.app.program.SetSpec
import com.hyperlog.app.program.Slot
import com.hyperlog.app.program.TurningUp
import com.hyperlog.app.progression.Record
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A finished block's review page (SPEC, Program → Block review). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class BlockReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 8, 3)

    private fun day(id: String, exercise: String, sets: Int) =
        Day(id, "Day ${id.uppercase()}", listOf(Group(false, listOf(Slot(id, exercise, emptyList(), null, listOf(SetSpec(sets, RepRange(6, 8), rir = 2)))))))

    private val program = Program(
        "Hyperlog Full Body", null,
        listOf(
            Block("build", "Build", 1, 4, listOf(day("a", "barbell_bench_press", 3))),
            Block("peak", "Peak", 5, 6, listOf(day("a", "barbell_bench_press", 2))),
        ),
    )
    private val names = mapOf("barbell_bench_press" to "Barbell Bench Press", "seated_cable_row" to "Seated Cable Row", "hack_squat" to "Hack Squat")

    private val review = Review(
        lifts = listOf(
            LiftChange("hack_squat", null, 120.0, 126.0),
            LiftChange("seated_cable_row", "Cable left · Harbour", 70.0, 70.0),
            LiftChange("barbell_bench_press", null, 100.0, 97.5),
        ),
        turningUp = TurningUp.OnTrainingDays(11, 12, listOf("Day A" to 1), weeks = 5, written = 4, again = listOf(3), easyWeeks = 0),
        volume = listOf(MuscleVolume("Chest", 9.5, 10.0)),
        changes = listOf("No grip work for a week", "Lighter bench"),
        records = listOf(SetRecord("hack_squat", null, monday.plusDays(9), ReviewRecord(Record.Estimate, 110.0, 6))),
        bodyweight = 82.0 to 81.5,
        goals = listOf(GoalStanding(LiftGoal("hack_squat", 140.0, monday.plusDays(40)), 126.0, GoalPace.OnPace(monday.plusDays(30)))),
        dates = monday..monday.plusDays(27),
    )

    private fun show(page: ReviewPage, checkIn: (String, String) -> Unit = { _, _ -> }, newProgram: (String, String) -> Unit = { _, _ -> }) = compose.setContent {
        HyperlogTheme { BlockReviewScreen(page, names, onBack = {}, onCheckIn = checkIn, onNewProgram = newProgram) }
    }

    private fun seen(text: String) = compose.onNodeWithText(text).performScrollTo()

    @Test
    fun theReviewShowsEachPartOfTheBlock() {
        show(ReviewPage(1, program, program.blocks[0], review, null, program.blocks[1]))
        compose.onNodeWithText("Build review").assertExists()
        seen("Weeks 1–4 · 3 Aug – 30 Aug")
        // Biggest gain first; a stall and a drop muted.
        seen("120 → 126 kg"); seen("+6")
        seen("Cable left · Harbour"); seen("±0")
        seen("100 → 97.5 kg"); seen("−2.5")
        seen("11 of 12 training days"); seen("Day A missed once")
        seen("Took 5 weeks, planned 4"); seen("Week 3 again")
        seen("9.5 → 10 sets a week")
        seen("2 Changes: No grip work for a week, Lighter bench")
        seen("110 kg × 6 · 12 Aug"); seen("Estimate record")
        seen("82 → 81.5 kg")
        seen("126 of 140 kg by 12 Sep"); seen("On pace for 2 Sep")
        seen("From Build")
    }

    @Test
    fun checkInAboutTheNextBlockOpensWithTheReviewInItsContext() {
        var opened: Pair<String, String>? = null
        show(ReviewPage(1, program, program.blocks[0], review, null, program.blocks[1]), checkIn = { o, c -> opened = o to c })
        compose.click("Check in about the next block")
        assertEquals("Build is done. Look at how it went and the next block.", opened!!.first)
        assertTrue("Hack Squat: 120 → 126 kg (+6)" in opened!!.second)
    }

    @Test
    fun theProgramReviewHasAColumnForTheWholeProgramAndNewProgram() {
        val whole = review.copy(lifts = listOf(LiftChange("hack_squat", null, 100.0, 126.0)), bodyweight = 84.0 to 81.5)
        var opened: Pair<String, String>? = null
        show(ReviewPage(1, program, program.blocks[1], review, whole, null), newProgram = { o, c -> opened = o to c })
        compose.onNodeWithText("Hyperlog Full Body review").assertExists()
        seen("Peak")
        assertEquals(true, compose.onAllNodesWithText("Program").fetchSemanticsNodes().isNotEmpty())
        seen("+26")
        seen("Program: 84 → 81.5 kg")
        compose.click("New program")
        assertEquals("Hyperlog Full Body is done. I want a new program for what comes next.", opened!!.first)
        assertTrue("## How the whole program went" in opened!!.second)
    }

    @Test
    fun aLiftIsOneRowOnTheProgramReviewWhicheverMachineEachColumnUsedMost() {
        val block = review.copy(lifts = listOf(LiftChange("seated_cable_row", "Cable left · Harbour", 70.0, 72.0)))
        val whole = review.copy(lifts = listOf(LiftChange("seated_cable_row", "Cable right · Itis", 60.0, 66.0)))
        show(ReviewPage(1, program, program.blocks[1], block, whole, null))
        assertEquals(1, compose.onAllNodesWithText("Seated Cable Row").fetchSemanticsNodes().size)
        seen("+2"); seen("+6")
    }

    @Test
    fun estimatesReadToTheHalfKiloAndTheGainBetweenThem() {
        show(ReviewPage(1, program, program.blocks[0], review.copy(lifts = listOf(LiftChange("hack_squat", null, 109.29, 112.04))), null, program.blocks[1]))
        seen("109.5 → 112 kg"); seen("+2.5")
    }

    @Test
    fun onTheProgramReviewALiftTheBlockDidntCountReadsTheProgramsEstimates() {
        val block = review.copy(lifts = emptyList())
        val whole = review.copy(lifts = listOf(LiftChange("hack_squat", null, 115.0, 121.5)))
        show(ReviewPage(1, program, program.blocks[1], block, whole, null))
        seen("115 → 121.5 kg"); seen("+6.5")
    }

    @Test
    fun aBlockReviewAndAProgramReviewStartDifferentKindsOfCheckIn() {
        assertEquals(com.hyperlog.app.coach.Opening.BlockReview, ReviewPage(1, program, program.blocks[0], review, null, program.blocks[1]).checkInKind)
        assertEquals(com.hyperlog.app.coach.Opening.ProgramReview, ReviewPage(1, program, program.blocks[1], review, review, null).checkInKind)
    }

    @Test
    fun checkInOnTheProgramReviewIsCheckInAboutWhatComesNext() {
        var opened: String? = null
        // A program replaced mid-way: its program review has a next block, still Check in.
        show(ReviewPage(1, program, program.blocks[0], review, review, program.blocks[1]), checkIn = { o, _ -> opened = o })
        compose.click("Check in")
        assertEquals("Hyperlog Full Body is done. Look at how it went and what comes next.", opened)
    }
}
