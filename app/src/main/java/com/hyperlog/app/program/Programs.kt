package com.hyperlog.app.program

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.ProgramEntity
import com.hyperlog.app.data.SessionEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeDiff
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.data.PlanChangeEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The program being run, where you are in it, and its Changes: [history] holds every accepted
 * one, oldest first, reverted ones included. [today] is the date the plan is read for.
 */
data class ActiveProgram(
    val id: Long,
    val program: Program,
    val position: Position,
    val history: List<ChangeRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    /** Past the last day: every day has been trained. */
    val finished: Boolean get() = position.week > program.weeks

    /** The Changes in force, oldest first. */
    val changes: List<AcceptedChange> get() = history.filter { it.revertedAt == null }.map { it.accepted }

    /**
     * The accepted Changes, not reverted, that still cover today or a later day, newest first:
     * the cards at the top of Coach. A date range runs to its last day, the rest of the block
     * while the block it was accepted in runs, the rest of the program until it's finished.
     */
    val inForce: List<ChangeRecord>
        get() = if (finished) emptyList() else history.filter { record ->
            // One that only ended others does nothing more.
            record.revertedAt == null && record.change.operations.isNotEmpty() && when (val scope = record.change.scope) {
                is Scope.Dates -> scope.to >= today
                Scope.RestOfProgram -> true
                Scope.RestOfBlock -> program.blockAt(position.week)?.id == record.accepted.blockId
            }
        }.sortedWith(compareByDescending<ChangeRecord> { it.acceptedAt }.thenByDescending { it.id })

    /** The ids of Changes ended by a later one's `replaces`. */
    val replaced: Set<Long> get() = history.flatMap { it.change.replaces }.toSet()

    /** The day Today runs, with today's Changes; null once the program is finished. */
    val next: PlannedDay? get() = if (finished) null else Changes.day(program, position, today, changes)
}

/** An accepted Change as kept: when it was accepted, and reverted if it was. */
data class ChangeRecord(
    val accepted: AcceptedChange,
    val acceptedAt: Long,
    val revertedAt: Long?,
    /** The check-in that made it; null for one pasted, or proposed in a session. */
    val checkIn: com.hyperlog.app.coach.SavedCheckIn? = null,
) {
    val id: Long get() = accepted.id
    val change: Change get() = accepted.change
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

    private val changeDao = db.planChanges()

    @OptIn(ExperimentalCoroutinesApi::class)
    val active: Flow<ActiveProgram?> = dao.activeFlow().flatMapLatest { row ->
        if (row == null) flowOf(null)
        else changeDao.changesFlow(row.id).map { changes -> toActive(row, changes) }
    }

    /** The date [millis] falls on, here. */
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** What accepting [change] would do to the running program's plan; null with no program running. */
    suspend fun preview(change: Change): ChangeDiff? {
        val active = active.first() ?: return null
        return withContext(Dispatchers.Default) { Changes.diff(active.program, active.position, dateOf(clock()), active.changes, change) }
    }

    /**
     * Accepts [change] for the running program: its position operations run, and its plan edits
     * apply to the sessions it covers. It is kept as history.
     */
    suspend fun accept(change: Change, checkIn: com.hyperlog.app.coach.SavedCheckIn? = null) {
        db.withTransaction {
            // The Changes it replaces end first, by the rules of Revert.
            change.replaces.sortedDescending().forEach { revertIn(it) }
            val row = dao.active() ?: return@withTransaction
            val program = program(row.id) ?: return@withTransaction
            val accepted = Changes.accept(program, Position(row.week, row.dayIndex), change, dateOf(clock()))
            changeDao.insert(
                PlanChangeEntity(
                    programId = row.id, json = ChangeJson.encode(accepted.change), acceptedAt = clock(),
                    acceptedOn = accepted.acceptedOn.toEpochDay(),
                    weekBefore = accepted.before.week, dayBefore = accepted.before.day,
                    weekAfter = accepted.after.week, dayAfter = accepted.after.day,
                    blockId = accepted.blockId, checkIn = checkIn?.let(com.hyperlog.app.coach.SavedCheckIn::encode),
                ),
            )
            if (accepted.after != accepted.before) dao.setPosition(row.id, accepted.after.week, accepted.after.day)
        }
    }

    /** Reverts the accepted Change [id]: its plan edits stop, and its move of the position is taken back. */
    suspend fun revert(id: Long) {
        db.withTransaction { revertIn(id) }
    }

    private suspend fun revertIn(id: Long) {
        val entity = changeDao.change(id) ?: return
        val row = dao.program(entity.programId) ?: return
        val program = program(row.id) ?: return
        if (changeDao.revert(id, clock()) == 0) return
        val accepted = entity.toAccepted() ?: return
        val back = Changes.revert(program, Position(row.week, row.dayIndex), accepted)
        dao.setPosition(row.id, back.week, back.day)
    }

    /**
     * The day at [position] of program [id] as trained on [date], with the Changes in force: now,
     * or, for a session finished at [asOf], those in force then (accepted by then and not yet
     * reverted), so a later Change never rewrites a past session.
     */
    suspend fun plannedDay(id: Long, position: Position, date: LocalDate, asOf: Long? = null): PlannedDay? {
        val program = program(id) ?: return null
        val changes = changeDao.changes(id)
            .filter { c -> if (asOf == null) c.revertedAt == null else c.acceptedAt <= asOf && (c.revertedAt == null || c.revertedAt > asOf) }
            .mapNotNull { it.toAccepted() }
        return Changes.day(program, position, date, changes)
    }

    /**
     * [plannedDay] for stored session [row]: as it was when it finished, or as now while it runs.
     * A workout's session trains the workout's day.
     */
    suspend fun plannedDay(row: SessionEntity): PlannedDay? {
        row.workoutId?.let { return workoutDay(it) }
        if (row.programId == null || row.week == null || row.dayIndex == null) return null
        return plannedDay(row.programId, Position(row.week, row.dayIndex), dateOf(row.startedAt), asOf = row.finishedAt)
    }

    private val workouts: MutableMap<Long, com.hyperlog.app.coach.Workout?> = java.util.Collections.synchronizedMap(mutableMapOf())

    /** The stored workout [id], or null if there's none (it was removed before a session trained it). */
    suspend fun workout(id: Long): com.hyperlog.app.coach.Workout? {
        if (id in workouts) return workouts[id]
        val row = db.workouts().workout(id) ?: return null
        return com.hyperlog.app.coach.Workout.stored(row.json).also { workouts[id] = it }
    }

    /** Workout [id] as a day to train. */
    suspend fun workoutDay(id: Long): PlannedDay? = workout(id)?.day()

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

    private fun toActive(row: ProgramEntity, changes: List<PlanChangeEntity>): ActiveProgram? {
        val program = parsed.getOrPut(row.id) { stored(row) } ?: return null
        val history = changes.mapNotNull { c -> c.toAccepted()?.let { ChangeRecord(it, c.acceptedAt, c.revertedAt, c.checkIn?.let(com.hyperlog.app.coach.SavedCheckIn::decode)) } }
        return ActiveProgram(row.id, program, Position(row.week, row.dayIndex), history, dateOf(clock()))
    }

    /** A kept Change; null only if its JSON no longer reads, which it always should. */
    private fun PlanChangeEntity.toAccepted(): AcceptedChange? = runCatching {
        AcceptedChange(
            id = id,
            change = ChangeJson.decode(json),
            acceptedOn = LocalDate.ofEpochDay(acceptedOn),
            before = Position(weekBefore, dayBefore),
            after = Position(weekAfter, dayAfter),
            blockId = blockId,
        )
    }.getOrNull()

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
