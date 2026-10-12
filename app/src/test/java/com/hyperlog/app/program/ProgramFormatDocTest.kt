package com.hyperlog.app.program

import com.hyperlog.app.library.LibrarySeed
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The example in docs/program-format.md is a valid program against the shipped library. */
class ProgramFormatDocTest {
    private val doc = File("../docs/program-format.md").readText()
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())

    @Test
    fun theDocumentedExampleImportsAndDoesWhatTheDocSays() {
        val example = Regex("```json\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(doc)
            .map { it.groupValues[1] }
            .single { "\"format\"" in it }
        val result = ProgramParser.parse(example, library.exercises.map { it.id }.toSet(), library.merged)
        val program = (result as? ProgramParser.Result.Valid)?.program ?: error("example rejected: $result")

        // Week 1: the ramp-in is one RIR easier with no techniques.
        val week1 = program.plannedDay(Position(1, 0))!!
        assertEquals(listOf(2, 2, 2), week1.slot("bench")!!.sets.map { it.rir })
        assertEquals(listOf("1", "2a", "2b"), week1.slots.map { it.label })
        assertEquals(null, week1.slot("lateral_raise")!!.sets.last().technique)
        // Week 3: the deload has one set fewer at 3 RIR, and the top set's own override.
        val week3 = program.plannedDay(Position(3, 0))!!.slot("bench")!!.sets
        assertEquals(listOf(RepRange(3, 5), RepRange(6, 8)), week3.map { it.reps })
        assertEquals(listOf(3, 3), week3.map { it.rir })
    }
}
