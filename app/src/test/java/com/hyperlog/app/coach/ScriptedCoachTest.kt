package com.hyperlog.app.coach

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The scripted coach (GLOSSARY; debug builds only): canned check-ins for every tile, with no key and
 * no network, that the wizard plays as it would Claude's.
 */
class ScriptedCoachTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val plan = ActiveProgram(1, program, Position(1, 1), today = LocalDate.of(2026, 10, 13))
    private val gyms = listOf(Gym(1, "Harbour", emptyList()))

    /** Plays [tile] to its end, tapping the first answer of each question. */
    private suspend fun play(tile: Tile, plan: ActiveProgram? = this.plan): Pair<CheckInStep, List<String>> {
        val scripted = BuildCoach.scripted { ScriptedWorld(plan, library.exercises, gyms, listOf("Back" to 4.0)) }!!
        val coach = CheckInCoach(scripted, CheckInWorld(plan?.program, ids, gyms = gyms))
        val frame = CheckInContext.frame(plan, emptyList(), library.exercises, gyms)
        var checkIn = coach.start(tile, frame)
        val asked = mutableListOf<String>()
        repeat(6) {
            val step = coach.step(checkIn) as? CheckInStep.Question ?: return coach.step(checkIn) to asked
            asked += step.question
            checkIn = coach.answer(checkIn, step.answers.first())
        }
        error("$tile asks on and on: $asked")
    }

    @Test
    fun everyTilePlaysToAnEnding() = runTest {
        val ends = Tile.entries.associateWith { play(it).first::class.simpleName }
        assertEquals(
            mapOf(
                Tile.Hurts to "ChangeReview", Tile.ShortOnTime to "ChangeReview", Tile.Gym to "WorkoutReview", Tile.Tired to "Words",
                Tile.ChangeProgram to "ChangeReview", Tile.Workout to "WorkoutReview", Tile.NewProgram to "ProgramReview", Tile.SomethingElse to "Words",
            ),
            ends,
        )
    }

    @Test
    fun changeMyProgramAsksWhichAndWhyThenSwapsForTheRestOfTheProgram() = runTest {
        val (end, asked) = play(Tile.ChangeProgram)
        assertEquals(2, asked.size)
        val change = (end as CheckInStep.ChangeReview).change
        assertEquals(Scope.RestOfProgram, change.scope)
        assertTrue(change.operations.single() is Operation.Swap)
    }

    @Test
    fun withNoProgramTheTilesThatChangeThePlanEndInWords() = runTest {
        assertTrue(play(Tile.Hurts, plan = null).first is CheckInStep.Words)
        assertTrue(play(Tile.Workout, plan = null).first is CheckInStep.WorkoutReview)
        assertTrue(play(Tile.NewProgram, plan = null).first is CheckInStep.ProgramReview)
    }

    @Test
    fun newProgramOffersAFileThenProposesAProgramWithASwapAndANewExercise() = runTest {
        val scripted = BuildCoach.scripted { ScriptedWorld(plan, library.exercises, gyms, emptyList()) }!!
        val coach = CheckInCoach(scripted, CheckInWorld(plan.program, ids, names = library.exercises.associate { it.id to it.name }))
        val first = coach.start(Tile.NewProgram, CheckInContext.frame(plan, emptyList(), library.exercises, gyms))
        assertTrue((coach.step(first) as CheckInStep.Question).attach)
        // A file attached goes on to the next question, as one from the answers does.
        val attached = coach.attach(first, Attachment("Nippard PPL.pdf", Attachment.PDF, byteArrayOf(1)))
        val days = coach.step(attached) as CheckInStep.Question
        val proposal = (coach.step(coach.answer(attached, days.answers.first())) as CheckInStep.ProgramReview).proposal
        assertEquals(1, proposal.swaps.size)
        assertEquals(1, proposal.newExercises.size)
        assertTrue(proposal.newExercises.single().id !in ids)
    }

    @Test
    fun aSecondNewProgramUsesTheDragCurlTheFirstOneAdded() = runTest {
        val drag = com.hyperlog.app.library.Exercise("barbell_drag_curl", "Barbell Drag Curl", com.hyperlog.app.library.Equipment.Barbell,
            com.hyperlog.app.library.MovementPattern.ElbowFlexion, com.hyperlog.app.library.GripDemand.High, listOf("Biceps"))
        val withDrag = library.exercises + drag
        val scripted = BuildCoach.scripted { ScriptedWorld(plan, withDrag, gyms, emptyList()) }!!
        val coach = CheckInCoach(scripted, CheckInWorld(plan.program, withDrag.map { it.id }.toSet(), names = withDrag.associate { it.id to it.name }))
        var checkIn = coach.start(Tile.NewProgram, CheckInContext.frame(plan, emptyList(), withDrag, gyms))
        repeat(2) { checkIn = coach.answer(checkIn, (coach.step(checkIn) as CheckInStep.Question).answers.first()) }
        val proposal = (coach.step(checkIn) as CheckInStep.ProgramReview).proposal
        assertEquals(emptyList<Any>(), proposal.newExercises)
        assertTrue("barbell_drag_curl" in proposal.program.exercises())
    }

    /** Opens a check-in of [kind] and plays it to its end, tapping the first answer; the check-in is returned with its end. */
    private suspend fun playKind(opening: String, kind: Opening, about: About? = null): Pair<CheckIn, CheckInStep> {
        val scripted = BuildCoach.scripted { ScriptedWorld(plan, library.exercises, gyms, listOf("Back" to 4.0)) }!!
        val coach = CheckInCoach(scripted, CheckInWorld(plan.program, ids, gyms = gyms, names = library.exercises.associate { it.id to it.name }))
        var checkIn = coach.start(opening, CheckInContext.frame(plan, emptyList(), library.exercises, gyms), kind, about)
        repeat(6) {
            val step = coach.step(checkIn) as? CheckInStep.Question ?: return checkIn to coach.step(checkIn)
            checkIn = coach.answer(checkIn, step.answers.first())
        }
        error("$kind asks on and on")
    }

    private fun change(played: Pair<CheckIn, CheckInStep>): Change {
        // A Change that passed the validators the first time needed no fix round.
        assertEquals(0, played.first.fixes)
        return (played.second as CheckInStep.ChangeReview).change
    }

    @Test
    fun aFitCheckInDropsTheLastExerciseOfTheDayTheChipWasOnForItsDate() = runTest {
        val change = change(playKind("Day B runs ~58 minutes but Saturday has 50.", Opening.Fit, About(slot = "curl", date = "2026-10-17")))
        assertEquals(Scope.Dates(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)), change.scope)
        assertEquals(listOf<Operation>(Operation.Remove("curl", reason = "short on time")), change.operations)
    }

    @Test
    fun aStallSwapsTheSlotForItsFirstAlternative() = runTest {
        val change = change(playKind("Bench press hasn't moved in 3 sessions.", Opening.CoachLine, About(pattern = LinePattern.Stall, exercise = "barbell_bench_press")))
        assertEquals(Scope.RestOfProgram, change.scope)
        assertEquals(listOf<Operation>(Operation.Swap("bench", "smith_machine_bench_press", reason = "stalled")), change.operations)
    }

    @Test
    fun grindingAddsARepInReserveOnThatExercise() = runTest {
        val change = change(playKind("Bench press has been ground out.", Opening.CoachLine, About(pattern = LinePattern.Grinding, exercise = "barbell_bench_press")))
        assertEquals(Scope.RestOfBlock, change.scope)
        assertEquals(listOf<Operation>(Operation.AdjustRir("bench", 1, reason = "grinding")), change.operations)
    }

    @Test
    fun theSameSwapBecomesARestOfProgramSwapToTheExerciseSwappedIn() = runTest {
        val change = change(playKind("Rows swapped 3 times.", Opening.CoachLine, About(pattern = LinePattern.SameSwap, slot = "row", exercise = "chest_supported_machine_row", swappedFor = "seated_cable_row")))
        assertEquals(Scope.RestOfProgram, change.scope)
        assertEquals(listOf<Operation>(Operation.Swap("row", "seated_cable_row", reason = "you keep choosing it")), change.operations)
    }

    @Test
    fun aMissedDayEndsInWordsAboutItsTrainingDay() = runTest {
        val end = playKind("Day A missed.", Opening.CoachLine, About(pattern = LinePattern.DayMissed, dayName = "Day A", missed = 3, of = 4)).second
        assertEquals(
            "Day A missed 3 of its last 4 turns. Check its training day still works for you, or change your training days.",
            (end as CheckInStep.Words).text,
        )
    }

    @Test
    fun theReviewsEndInWordsThatNameTheirParts() = runTest {
        listOf(Opening.BlockReview, Opening.ProgramReview).forEach { kind ->
            val words = (playKind("Build is done.", kind).second as CheckInStep.Words).text
            listOf("lifts", "turning up", "volume").forEach { assertTrue("$kind: $words", it in words) }
        }
    }

    @Test
    fun newProgramFromAProgramReviewPlaysNewProgramsScript() = runTest {
        val (checkIn, end) = playKind("Full body is done. I want a new program for what comes next.", Opening.NewProgramReview)
        assertTrue(end is CheckInStep.ProgramReview)
        assertEquals(2, checkIn.turns.count { it.reply.toolCalls.any { call -> call.name == CoachTools.ASK } })
    }
}
