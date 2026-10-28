package com.hyperlog.app.program

import com.hyperlog.app.history.LiftGoal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** How a finished block went, worked out from the log (SPEC, Program → Block review). */
class BlockReviewsTest {
    private val monday = LocalDate.of(2026, 8, 3)

    /** Two blocks of two weeks, Build then Peak, each with one day of bench and one of squat. */
    private val program = Program(
        "Hyperlog", null,
        listOf(
            Block("build", "Build", 1, 2, listOf(day("a", "bench", 3), day("b", "squat", 3))),
            Block("peak", "Peak", 3, 4, listOf(day("a", "bench", 2), day("b", "squat", 2))),
        ),
    )

    private fun day(id: String, exercise: String, sets: Int) =
        Day(id, "Day ${id.uppercase()}", listOf(Group(false, listOf(Slot(id, exercise, emptyList(), null, listOf(SetSpec(sets, RepRange(6, 8), rir = 2)))))))

    /** A session of week [week] on day [n] of the run (from 0), with the estimate after it. */
    private fun point(n: Int, week: Int?, kg: Double, eased: Boolean = false) = ReviewPoint(monday.plusDays(3L * n), week, eased, kg)

    @Test
    fun eachLiftReadsItsEstimateBeforeTheBlockToAfterItsLastSessionBiggestGainFirst() {
        val log = ReviewLog(
            program,
            lines = listOf(
                // Bench was trained before the block: it starts from the estimate then.
                ReviewLine("bench", points = listOf(point(-5, null, 98.0), point(0, 1, 100.0), point(2, 2, 101.0), point(4, 3, 104.0))),
                ReviewLine("squat", points = listOf(point(1, 1, 120.0), point(3, 2, 126.0))),
                // Trained once in the block: left out.
                ReviewLine("row", points = listOf(point(1, 1, 70.0))),
            ),
        )
        val review = BlockReviews.of(log, program.blocks[0])
        assertEquals(listOf(LiftChange("squat", null, 120.0, 126.0), LiftChange("bench", null, 98.0, 101.0)), review.lifts)
    }

    @Test
    fun deloadEasyWeekAndEasedSessionsAreLeftOutOfLifts() {
        val log = ReviewLog(
            program,
            lines = listOf(
                ReviewLine("squat", points = listOf(point(0, 1, 120.0), point(1, 1, 123.0), point(3, 2, 118.0, eased = true))),
                // Two sessions, one eased: trained once that counts.
                ReviewLine("bench", points = listOf(point(0, 1, 100.0), point(2, 2, 104.0, eased = true))),
            ),
        )
        assertEquals(listOf(LiftChange("squat", null, 120.0, 123.0)), BlockReviews.of(log, program.blocks[0]).lifts)
    }

    @Test
    fun aMachineExerciseReadsTheMachineUsedMostInTheBlock() {
        val log = ReviewLog(
            program,
            lines = listOf(
                ReviewLine("cable_row", "Cable right · Itis", listOf(point(0, 1, 60.0), point(4, 2, 66.0))),
                ReviewLine("cable_row", "Cable left · Harbour", listOf(point(1, 1, 70.0), point(2, 1, 71.0), point(3, 2, 72.0))),
            ),
        )
        assertEquals(listOf(LiftChange("cable_row", "Cable left · Harbour", 70.0, 72.0)), BlockReviews.of(log, program.blocks[0]).lifts)
    }

    /** A turn of Day [day] on Monday (A) or Thursday (B) of week [n] of the calendar (from 0), giving program [week]. */
    private fun turn(n: Int, day: String, week: Int, missed: Boolean = false, easy: Boolean = false) =
        ReviewTurn(monday.plusWeeks(n.toLong()).plusDays(if (day == "A") 0 else 3), week, "Day $day", missed, easy)

    @Test
    fun turningUpCountsTheTrainingDaysTrainedTheMissedDaysByDayAndTheWeeksTaken() {
        val log = ReviewLog(
            program,
            turns = listOf(
                turn(0, "A", 1), turn(0, "B", 1, missed = true),
                // A week with no session trains week 1 again.
                turn(1, "A", 1, missed = true), turn(1, "B", 1, missed = true),
                turn(2, "A", 1), turn(2, "B", 1),
                turn(3, "A", 2), turn(3, "B", 2),
                // Peak's isn't counted.
                turn(4, "A", 3, missed = true),
            ),
        )
        assertEquals(
            TurningUp.OnTrainingDays(done = 5, of = 8, missed = listOf("Day B" to 2, "Day A" to 1), weeks = 4, written = 2, again = listOf(1), easyWeeks = 0),
            BlockReviews.of(log, program.blocks[0]).turningUp,
        )
    }

    @Test
    fun anEasyWeekIsCountedAsAWeekTakenButNotAWeekAgain() {
        val log = ReviewLog(
            program,
            turns = listOf(turn(0, "A", 1), turn(0, "B", 1), turn(1, "A", 2, easy = true), turn(1, "B", 2, easy = true), turn(2, "A", 2), turn(2, "B", 2)),
        )
        val turningUp = BlockReviews.of(log, program.blocks[0]).turningUp as TurningUp.OnTrainingDays
        assertEquals(3 to emptyList<Int>(), turningUp.weeks to turningUp.again)
        assertEquals(1, turningUp.easyWeeks)
    }

    /** A set of each exercise counts 1 for its primary muscle and 0.5 for its secondary one. */
    private val muscles = mapOf("bench" to mapOf("Chest" to 1.0, "Triceps Long Head" to 0.5), "squat" to mapOf("Quads" to 1.0, "Glutes" to 0.5))

    @Test
    fun volumeIsTheAverageWeekDoneAgainstTheBlockAsWrittenWithItsWeekOverridesInBodyOrder() {
        // Build's week 2 has a set fewer of everything.
        val build = program.blocks[0].copy(weeks = listOf(WeekOverride(WeekRange(2, 2), fewerSets = 1)))
        val written = program.copy(blocks = listOf(build, program.blocks[1]))
        val log = ReviewLog(
            written,
            sessions = listOf(
                ReviewSession(monday, 1, mapOf("Chest" to 3.0, "Triceps Long Head" to 1.5)),
                ReviewSession(monday.plusDays(3), 1, mapOf("Quads" to 3.0, "Glutes" to 1.5)),
                ReviewSession(monday.plusWeeks(1), 2, mapOf("Chest" to 2.0, "Triceps Long Head" to 1.0)),
                ReviewSession(monday.plusWeeks(2), 3, mapOf("Chest" to 2.0)),
            ),
            muscles = muscles,
        )
        assertEquals(
            listOf(
                MuscleVolume("Chest", done = 2.5, written = 2.5),
                MuscleVolume("Triceps Long Head", done = 1.25, written = 1.25),
                MuscleVolume("Glutes", done = 0.75, written = 1.25),
                MuscleVolume("Quads", done = 1.5, written = 2.5),
            ),
            BlockReviews.of(log, build).volume,
        )
    }

    @Test
    fun withoutTrainingDaysTurningUpIsTheDaysDoneAndTheDatesTheBlockTook() {
        val log = ReviewLog(program, sessions = listOf(ReviewSession(monday, 1), ReviewSession(monday.plusDays(2), 1), ReviewSession(monday.plusDays(9), 2), ReviewSession(monday.plusDays(15), 3)))
        assertEquals(TurningUp.InSequence(days = 3, from = monday, to = monday.plusDays(9)), BlockReviews.of(log, program.blocks[0]).turningUp)
    }

    @Test
    fun theChangesAreThoseAcceptedDuringTheBlock() {
        val log = ReviewLog(program, changes = listOf(ReviewChange("build", "No grip work for a week"), ReviewChange("peak", "Swap squat"), ReviewChange("build", "Lighter bench")))
        assertEquals(listOf("No grip work for a week", "Lighter bench"), BlockReviews.of(log, program.blocks[0]).changes)
    }

    @Test
    fun recordsAreTheTopSetsThatMadeOneInTheBlock() {
        val pr = ReviewRecord(com.hyperlog.app.progression.Record.Estimate, 100.0, 6)
        val log = ReviewLog(
            program,
            lines = listOf(
                ReviewLine("bench", points = listOf(point(-5, null, 98.0).copy(record = pr), point(0, 1, 100.0), point(2, 2, 101.0).copy(record = pr))),
                ReviewLine("cable_row", "Cable left · Harbour", listOf(point(1, 1, 70.0).copy(record = pr.copy(kind = com.hyperlog.app.progression.Record.Reps)), point(4, 3, 75.0).copy(record = pr))),
            ),
        )
        assertEquals(
            listOf(
                SetRecord("cable_row", "Cable left · Harbour", monday.plusDays(3), pr.copy(kind = com.hyperlog.app.progression.Record.Reps)),
                SetRecord("bench", null, monday.plusDays(6), pr),
            ),
            BlockReviews.of(log, program.blocks[0]).records,
        )
    }

    @Test
    fun bodyweightIsTheFirstValueLoggedInTheBlockToTheLast() {
        val sessions = listOf(ReviewSession(monday, 1), ReviewSession(monday.plusDays(10), 2), ReviewSession(monday.plusDays(14), 3))
        val weights = listOf(com.hyperlog.app.history.Bodyweight(monday.minusDays(1), 83.0), com.hyperlog.app.history.Bodyweight(monday.plusDays(1), 82.0),
            com.hyperlog.app.history.Bodyweight(monday.plusDays(8), 81.5), com.hyperlog.app.history.Bodyweight(monday.plusDays(12), 80.0))
        assertEquals(82.0 to 81.5, BlockReviews.of(ReviewLog(program, sessions = sessions, bodyweights = weights), program.blocks[0]).bodyweight)
        assertEquals(null, BlockReviews.of(ReviewLog(program, sessions = sessions), program.blocks[0]).bodyweight)
    }

    @Test
    fun eachGoalReadsTheEstimateAgainstItAndWithADateTheBlocksGainCarriedOnInAStraightLine() {
        val bench = LiftGoal("bench", 110.0, by = monday.plusDays(40))
        val squat = LiftGoal("squat", 140.0, by = monday.plusDays(23))
        val row = LiftGoal("row", 80.0, by = null)
        val log = ReviewLog(
            program,
            lines = listOf(
                // A third of a kilo a day: 6 kg more takes 18 days.
                ReviewLine("bench", points = listOf(point(0, 1, 100.0), point(4, 2, 104.0))),
                // Half a kilo a day: 14 days on, 130 of 140.
                ReviewLine("squat", points = listOf(point(1, 1, 120.0), point(3, 2, 123.0))),
                ReviewLine("row", points = listOf(point(1, 1, 70.0))),
            ),
            goals = listOf(bench, squat, row, LiftGoal("deadlift", 200.0, by = null)),
        )
        assertEquals(
            listOf(
                GoalStanding(bench, 104.0, GoalPace.OnPace(monday.plusDays(30))),
                GoalStanding(squat, 123.0, GoalPace.Short(10.0)),
                // Without a date there's no pace.
                GoalStanding(row, 70.0, null),
            ),
            BlockReviews.of(log, program.blocks[0]).goals,
        )
    }

    @Test
    fun aGoalReachedOrNotGainingHasNoDateToBeOnPaceFor() {
        val bench = LiftGoal("bench", 100.0, by = monday.plusDays(40))
        val squat = LiftGoal("squat", 140.0, by = monday.plusDays(40))
        val log = ReviewLog(
            program,
            lines = listOf(ReviewLine("bench", points = listOf(point(0, 1, 100.0), point(4, 2, 102.0))), ReviewLine("squat", points = listOf(point(1, 1, 125.0), point(3, 2, 123.0)))),
            goals = listOf(bench, squat),
        )
        assertEquals(listOf(GoalStanding(bench, 102.0, null), GoalStanding(squat, 123.0, GoalPace.Short(17.0))), BlockReviews.of(log, program.blocks[0]).goals)
    }

    @Test
    fun theProgramColumnReadsTheWholeProgramUpToTheBlock() {
        val log = ReviewLog(
            program,
            lines = listOf(ReviewLine("bench", points = listOf(point(0, 1, 100.0), point(2, 2, 101.0), point(4, 3, 104.0), point(6, 4, 106.0)))),
            changes = listOf(ReviewChange("build", "Lighter bench"), ReviewChange("peak", "Swap squat")),
        )
        val review = BlockReviews.program(log, program.blocks[1])
        assertEquals(listOf(LiftChange("bench", null, 100.0, 106.0)), review.lifts)
        assertEquals(listOf("Lighter bench", "Swap squat"), review.changes)
    }

    @Test
    fun aBlockIsFinishedOnceThePositionIsPastItsLastWeek() {
        // The rotation moves the position on after the last planned day is trained, or once its week is over.
        assertEquals(emptyList<String>(), BlockReviews.finished(program, Position(2, 1)).map { it.id })
        assertEquals(listOf("build"), BlockReviews.finished(program, Position(3, 0)).map { it.id })
        assertEquals(listOf("build", "peak"), BlockReviews.finished(program, Position(5, 0)).map { it.id })
    }

    @Test
    fun checkInAboutTheNextBlockHasTheReviewAsTextInItsContext() {
        val review = Review(
            lifts = listOf(LiftChange("squat", null, 120.0, 126.0), LiftChange("cable_row", "Cable left · Harbour", 70.0, 70.0)),
            turningUp = TurningUp.OnTrainingDays(5, 8, listOf("Day B" to 2, "Day A" to 1), weeks = 4, written = 2, again = listOf(1), easyWeeks = 1),
            volume = listOf(MuscleVolume("Chest", 2.5, 3.0)),
            changes = listOf("Lighter bench"),
            records = listOf(SetRecord("squat", null, monday.plusDays(9), ReviewRecord(com.hyperlog.app.progression.Record.Estimate, 110.0, 6))),
            bodyweight = 82.0 to 81.5,
            goals = listOf(GoalStanding(LiftGoal("squat", 140.0, monday.plusDays(40)), 126.0, GoalPace.OnPace(monday.plusDays(30)))),
        )
        val names = mapOf("squat" to "Hack Squat", "cable_row" to "Seated Cable Row")
        assertEquals(
            """
            ## How Build went (weeks 1–2)
            Lifts, estimate before → after:
            - Hack Squat: 120 → 126 kg (+6)
            - Seated Cable Row (Cable left · Harbour): 70 → 70 kg (±0)
            Turning up: 5 of 8 training days; Day B missed 2 times, Day A missed once; took 4 weeks, planned 2: week 1 again, an easy week
            Weekly sets, done → as written:
            - Chest: 2.5 → 3
            Changes accepted: Lighter bench
            Records: Hack Squat 110 kg × 6 on 2026-08-12
            Bodyweight: 82 → 81.5 kg
            Lift goals:
            - Hack Squat: 126 of 140 kg by 2026-09-12; on pace for 2026-09-02 at this block's rate (an estimate)
            Next block: Peak
            """.trimIndent(),
            BlockReviews.context(program, program.blocks[0], review, null, names),
        )
        assertEquals("Build is done. Here's how it went: look at it and the next block.", BlockReviews.opening(program, program.blocks[0]))
    }

    @Test
    fun aReviewSpansItsFirstTurnOrSessionToItsLast() {
        val log = ReviewLog(program, turns = listOf(turn(0, "A", 1), turn(1, "B", 2, missed = true)), sessions = listOf(ReviewSession(monday, 1), ReviewSession(monday.plusDays(15), 3)))
        assertEquals(monday..monday.plusDays(10), BlockReviews.of(log, program.blocks[0]).dates)
    }

    @Test
    fun newProgramOpensWithTheProgramDoneAndHasTheWholeProgramInItsContext() {
        val review = BlockReviews.of(ReviewLog(program), program.blocks[1])
        assertEquals("Hyperlog is done. I want a new program for what comes next.", BlockReviews.opening(program, program.blocks[1], newProgram = true))
        assertEquals(true, "## How the whole program went (weeks 1–4)" in BlockReviews.context(program, program.blocks[1], review, review, emptyMap()))
    }

    @Test
    fun theProgramColumnsWrittenVolumeCountsOnlyTheWeeksTheProgramReached() {
        val muscles = mapOf("bench" to mapOf("Chest" to 1.0))
        // Stopped in week 3: Peak's week 4 isn't written for it.
        val log = ReviewLog(program, sessions = listOf(ReviewSession(monday, 1), ReviewSession(monday.plusWeeks(2), 3)), muscles = muscles)
        assertEquals(MuscleVolume("Chest", done = 0.0, written = (3 + 3 + 2) / 3.0), BlockReviews.program(log, program.blocks[1]).volume.single())
    }
}
