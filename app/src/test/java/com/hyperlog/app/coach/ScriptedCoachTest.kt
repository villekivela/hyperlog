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
                Tile.ChangeProgram to "ChangeReview", Tile.Workout to "WorkoutReview", Tile.NewProgram to "Words", Tile.SomethingElse to "Words",
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
    }
}
