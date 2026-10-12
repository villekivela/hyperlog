package com.hyperlog.app.program

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.ProgramEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

/** The program being run and where you are in it. */
data class ActiveProgram(val id: Long, val program: Program, val position: Position) {
    /** Past the last day: every day has been trained. */
    val finished: Boolean get() = position.week > program.weeks

    /** The day Today runs; null once the program is finished. */
    val next: PlannedDay? get() = if (finished) null else program.plannedDay(position)
}

/**
 * Imported programs. Importing goes through [ProgramParser]: a file with any problem is rejected
 * whole and changes nothing. One program runs at a time, as a sequence of days.
 */
class Programs(
    private val db: HyperlogDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Legacy id -> kept id, for hints in import errors. */
    private val merged: Map<String, String> = emptyMap(),
) {
    private val dao = db.programs()
    private val parsed: MutableMap<Long, Program?> = java.util.Collections.synchronizedMap(mutableMapOf())

    val active: Flow<ActiveProgram?> = dao.activeFlow().map { it?.let(::toActive) }

    /**
     * Imports [json] as the program to run, from its first day; the one running before stays as a
     * record for its sessions. Returns the parse result: on errors nothing is imported.
     */
    suspend fun import(json: String): ProgramParser.Result {
        val ids = exerciseIds()
        val result = withContext(Dispatchers.Default) { ProgramParser.parse(json, ids, merged) }
        if (result is ProgramParser.Result.Valid) {
            db.withTransaction {
                dao.deactivateAll()
                val id = dao.insert(ProgramEntity(title = result.program.title, source = json, importedAt = clock()))
                parsed[id] = result.program
            }
        }
        return result
    }

    /** The program with [id] as imported, or null if it no longer reads (it always should). */
    suspend fun program(id: Long): Program? {
        if (id in parsed) return parsed[id]
        val row = dao.program(id) ?: return null
        return stored(row).also { parsed[id] = it }
    }

    /**
     * A session of the day at [from] was finished: the next day comes up. Only the program's next
     * day moves it, so finishing a resumed session again doesn't skip a day.
     */
    suspend fun advance(id: Long, from: Position) {
        val program = program(id) ?: return
        val next = program.next(from) ?: Position(program.weeks + 1, 0)
        dao.advance(id, from.week, from.day, next.week, next.day)
    }

    private suspend fun exerciseIds() = db.exercises().all().map { it.id }.toSet()

    private fun toActive(row: ProgramEntity): ActiveProgram? {
        val program = parsed.getOrPut(row.id) { stored(row) } ?: return null
        return ActiveProgram(row.id, program, Position(row.week, row.dayIndex))
    }

    /**
     * Reads a stored program without checking exercise ids: it was checked on import, and library
     * exercises are hidden, never deleted.
     */
    private fun stored(row: ProgramEntity): Program? =
        (ProgramParser.parse(row.source, AnyExercise) as? ProgramParser.Result.Valid)?.program
}

/** Accepts every exercise id, for programs already validated on import. */
private object AnyExercise : Set<String> by emptySet() {
    override fun contains(element: String) = true
}
