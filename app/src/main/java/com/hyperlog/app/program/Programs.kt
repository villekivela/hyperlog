package com.hyperlog.app.program

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.ProgramEntity
import com.hyperlog.app.data.SessionEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.data.MissedDayEntity
import com.hyperlog.app.data.SessionStartRow
import com.hyperlog.app.week.Rotation
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.SessionStart
import com.hyperlog.app.week.Turn
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeDiff
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.ProgramProposal
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.exercises
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.data.PlanChangeEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The program being run, where you are in it, and its Changes: [history] holds every accepted
 * one, oldest first, reverted ones included. [today] is the date the plan is read for. With
 * training days its days take turns on them: [rotation] (ADR 0006), whose next day is [position];
 * null while it runs as a sequence.
 */
data class ActiveProgram(
    val id: Long,
    val program: Program,
    val position: Position,
    val history: List<ChangeRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val rotation: Rotation? = null,
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

    /**
     * The day Today runs, with today's Changes, or with the rotation the next day on its date;
     * null once the program is finished.
     */
    val next: PlannedDay? get() = when {
        finished -> null
        rotation != null -> rotation.planned.firstOrNull()?.let(::dayOf)
        else -> Changes.day(program, position, today, changes)
    }

    /** [turns]' days by date, each as [dayOf] reads it. */
    fun daysOf(turns: List<Turn>): Map<LocalDate, PlannedDay> = turns.mapNotNull { turn -> dayOf(turn)?.let { turn.date to it } }.toMap()

    /** [turn]'s day as trained on its date, with the Changes covering it, eased in an easy week. */
    fun dayOf(turn: Turn): PlannedDay? = Changes.day(program, turn.position, turn.date, changes)?.easyWeek(turn.easy)
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

/** A program on the Programs list (SPEC, Program → Programs), with what it is now. */
data class ListedProgram(val id: Long, val title: String, val program: Program, val source: String, val status: Status) {
    sealed interface Status {
        /** The one in progress, at [position]. */
        data class Running(val position: Position, val finished: Boolean) : Status

        /** Saved for later at [savedAt], never started, with the [newExercises] it adds to the library when it starts. */
        data class Saved(val savedAt: Long, val newExercises: List<Exercise> = emptyList()) : Status

        /**
         * Ran from [startedAt] until another replaced it at [endedAt]; [resumeAt], the day it was
         * at, if it was replaced mid-way (past its first day, not finished).
         */
        data class Past(val startedAt: Long, val endedAt: Long?, val resumeAt: Position? = null) : Status
    }
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
        else combine(changeDao.changesFlow(row.id), db.weekdayPlan().observe(), startsFlow(row), db.missedDays().flow(row.id)) { changes, plan, starts, missed ->
            toActive(row, changes, plan.map { DayOfWeek.of(it.weekday) }.toSet(), starts, missed)
        }
    }

    /** The sessions the rotation of [row] reads: those started since its anchor's week. */
    private fun startsFlow(row: ProgramEntity): Flow<List<SessionStartRow>> =
        row.rotationFrom?.let { db.sessions().startsFlow(sinceOf(it)) } ?: flowOf(emptyList())

    /** The start of the week of epoch day [from], in millis. */
    private fun sinceOf(from: Long): Long =
        Rotations.mondayOf(LocalDate.ofEpochDay(from)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Every program held, newest first: the one running, then by when each was saved or replaced
     * (those saved for later, and past runs).
     */
    val listed: Flow<List<ListedProgram>> = combine(dao.allFlow(), active) { rows, running ->
        rows.mapNotNull { row ->
            val program = parsed.getOrPut(row.id) { stored(row) } ?: return@mapNotNull null
            val status = when {
                // On training days the rotation, not the row, knows the next day.
                row.active -> (running?.takeIf { it.id == row.id }?.position ?: Position(row.week, row.dayIndex))
                    .let { ListedProgram.Status.Running(it, it.week > program.weeks) }
                row.startedAt == null -> ListedProgram.Status.Saved(
                    row.importedAt, row.waitingExercises(),
                )
                else -> {
                    val at = Position(row.week, row.dayIndex)
                    ListedProgram.Status.Past(row.startedAt, row.endedAt, at.takeIf { it != Position(1, 0) && row.week <= program.weeks })
                }
            }
            ListedProgram(row.id, row.title, program, row.source, status)
        }.sortedByDescending { listed ->
            // A saved program started later keeps its row, so each is dated by what last happened to it.
            when (val s = listed.status) {
                is ListedProgram.Status.Running -> Long.MAX_VALUE
                is ListedProgram.Status.Saved -> s.savedAt
                is ListedProgram.Status.Past -> s.endedAt ?: s.startedAt
            }
        }
    }

    /** The date [millis] falls on, here. */
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** What accepting [change] would do to the running program's plan; null with no program running. */
    suspend fun preview(change: Change): ChangeDiff? {
        val active = active.first() ?: return null
        return withContext(Dispatchers.Default) { Changes.diff(active.program, active.position, dateOf(clock()), active.changes, change, active.rotation?.trainingDays) }
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
            val rotation = rotationOf(row)
            val position = rotation?.next ?: Position(row.week, row.dayIndex)
            val accepted = Changes.accept(program, position, change, dateOf(clock()), rotation?.trainingDays)
            changeDao.insert(
                PlanChangeEntity(
                    programId = row.id, json = ChangeJson.encode(accepted.change), acceptedAt = clock(),
                    acceptedOn = accepted.acceptedOn.toEpochDay(),
                    weekBefore = accepted.before.week, dayBefore = accepted.before.day,
                    weekAfter = accepted.after.week, dayAfter = accepted.after.day,
                    blockId = accepted.blockId, checkIn = checkIn?.let(com.hyperlog.app.coach.SavedCheckIn::encode),
                ),
            )
            if (accepted.after != accepted.before) moveTo(row, rotation, accepted.after)
        }
    }

    /** Moves program [row]'s next day to [position]: with a [rotation], set again from today at it. */
    private suspend fun moveTo(row: ProgramEntity, rotation: Rotation?, position: Position) {
        if (rotation == null) return dao.setPosition(row.id, position.week, position.day)
        setAnchor(row.id, rotation, rotation.anchor.copy(week = position.week, day = position.day, rollbackDay = position.day))
    }

    /**
     * Program [id] rotates from [anchor] on, or, with none, runs as a sequence from the
     * [rotation]'s next day. The days the [rotation] missed before are kept, since it no longer
     * reaches them.
     */
    private suspend fun setAnchor(id: Long, rotation: Rotation?, anchor: RotationAnchor?) {
        rotation?.let { r ->
            val from = anchor?.from ?: dateOf(clock())
            db.missedDays().insert(r.missed.filter { it.date < from }.map { MissedDayEntity(id, it.date.toEpochDay(), it.position.week, it.position.day) })
        }
        if (anchor == null) {
            val next = rotation?.next ?: return
            dao.setRotation(id, null, next.week, next.day, 0, 0)
        } else {
            dao.setRotation(id, anchor.from.toEpochDay(), anchor.week, anchor.day, anchor.rollbackDay, anchor.emptyWeeks)
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
        val rotation = rotationOf(row)
        if (changeDao.revert(id, clock()) == 0) return
        val accepted = entity.toAccepted() ?: return
        val current = rotation?.next ?: Position(row.week, row.dayIndex)
        val back = Changes.revert(program, current, accepted)
        if (back != current) moveTo(row, rotation, back)
    }

    /**
     * The day at [position] of program [id] as trained on [date], with the Changes in force: now,
     * or, for a session finished at [asOf], those in force then (accepted by then and not yet
     * reverted), so a later Change never rewrites a past session.
     */
    suspend fun plannedDay(id: Long, position: Position, date: LocalDate, asOf: Long? = null, easy: Boolean = false): PlannedDay? {
        val program = program(id) ?: return null
        val changes = changeDao.changes(id)
            .filter { c -> if (asOf == null) c.revertedAt == null else c.acceptedAt <= asOf && (c.revertedAt == null || c.revertedAt > asOf) }
            .mapNotNull { it.toAccepted() }
        return Changes.day(program, position, date, changes)?.easyWeek(easy)
    }

    /**
     * [plannedDay] for stored session [row]: as it was when it finished, or as now while it runs.
     * A workout's session trains the workout's day.
     */
    suspend fun plannedDay(row: SessionEntity): PlannedDay? {
        row.workoutId?.let { return workoutDay(it) }
        if (row.programId == null || row.week == null || row.dayIndex == null) return null
        return plannedDay(row.programId, Position(row.week, row.dayIndex), dateOf(row.startedAt), asOf = row.finishedAt, easy = row.easy)
    }

    private val workouts: MutableMap<Long, com.hyperlog.app.coach.Workout?> = java.util.Collections.synchronizedMap(mutableMapOf())

    /** The stored workout [id], or null if there's none (it was removed before a session trained it). */
    suspend fun workout(id: Long): com.hyperlog.app.coach.Workout? {
        if (id in workouts) return workouts[id]
        val row = db.workouts().workout(id) ?: return null
        return com.hyperlog.app.coach.Workout.stored(row.json).also { workouts[id] = it }
    }

    /** Drops the programs and workouts read so far: a restored backup's rows reuse their ids. */
    fun forget() {
        parsed.clear()
        workouts.clear()
    }

    /** Workout [id] as a day to train. */
    suspend fun workoutDay(id: Long): PlannedDay? = workout(id)?.day()

    /**
     * Imports [json] as the program to run, from its first day; the one running before stays as a
     * record for its sessions. [newExercises], a new program's from Claude, are added to the
     * library with it. Returns the parse result: on errors nothing is imported or added.
     */
    suspend fun import(json: String, newExercises: List<Exercise> = emptyList()): ProgramParser.Result {
        val file = takeExercises(json)
        val result = check(file.source, newExercises + file.exercises).let { r ->
            if (file.errors.isEmpty()) r else ProgramParser.Result.Invalid(file.errors + (r as? ProgramParser.Result.Invalid)?.errors.orEmpty())
        }
        if (result is ProgramParser.Result.Valid) {
            db.withTransaction {
                // A file's own new exercises join the library only if the program uses them.
                val used = result.program.exercises()
                ExerciseLibrary(db).addNew((newExercises + file.exercises.filter { it.id in used }).distinctBy { it.id })
                deactivate()
                val id = dao.insert(ProgramEntity(title = result.program.title, source = file.source, importedAt = clock(), startedAt = clock()))
                parsed[id] = result.program
                rotateFromToday(id)
            }
        }
        return result
    }

    /**
     * Saves [json] for later, with the [newExercises] it adds to the library once it starts: the
     * program running goes on. Checked as [import] checks it; on errors nothing is saved.
     */
    suspend fun save(json: String, newExercises: List<Exercise> = emptyList()): ProgramParser.Result {
        val result = check(json, newExercises)
        if (result is ProgramParser.Result.Valid) {
            val id = dao.insert(
                ProgramEntity(
                    title = result.program.title, source = json, importedAt = clock(), active = false,
                    newExercises = newExercises.takeIf { it.isNotEmpty() }?.let(ProgramProposal::encodeExercises),
                ),
            )
            parsed[id] = result.program
        }
        return result
    }

    /**
     * Starts saved program [id] in place of the one running, from week 1, day 1, as an import
     * does; its new exercises join the library. Checked again first: if the library has gained one
     * of them since, the problems come back and nothing starts.
     */
    suspend fun start(id: Long): ProgramParser.Result? {
        val row = dao.program(id)?.takeIf { it.startedAt == null } ?: return null
        val newExercises = row.waitingExercises()
        val result = check(row.source, newExercises)
        if (result is ProgramParser.Result.Valid) {
            db.withTransaction {
                ExerciseLibrary(db).addNew(newExercises)
                deactivate()
                dao.start(id, clock())
                rotateFromToday(id)
            }
        }
        return result
    }

    /**
     * Run again: program [id], one that has run, starts a fresh run from week 1, day 1, in place of
     * the one running. The old run stays as it was, with its sessions and Changes.
     */
    suspend fun runAgain(id: Long): ProgramParser.Result? {
        val row = dao.program(id)?.takeIf { it.startedAt != null } ?: return null
        return import(row.source)
    }

    /**
     * Resume: program [id], replaced mid-way, runs again from the day it was at, in place of the
     * one running, with its sessions and Changes. False for any other program.
     */
    suspend fun resume(id: Long): Boolean {
        val listed = listed.first().firstOrNull { it.id == id }
        if ((listed?.status as? ListedProgram.Status.Past)?.resumeAt == null) return false
        db.withTransaction {
            deactivate()
            dao.resume(id)
            rotateFromToday(id)
        }
        return true
    }

    /**
     * Program [id]'s file for Export: as imported, with `newExercises` for the exercises it uses
     * that aren't in the [shipped] library (a saved one's waiting ones too), so it imports on
     * another phone.
     */
    suspend fun exportFile(id: Long, shipped: Set<String>): String? {
        val row = dao.program(id) ?: return null
        val program = program(id) ?: return null
        val waiting = row.waitingExercises()
        val library = db.exercises().all().associateBy { it.id }
        val own = program.exercises().filter { it !in shipped }.mapNotNull { library[it]?.toExercise() }
        val added = (waiting + own).distinctBy { it.id }
        if (added.isEmpty()) return row.source
        val root = Json.parseToJsonElement(row.source) as JsonObject
        val exercises = Json.parseToJsonElement(ProgramProposal.encodeExercises(added))
        return PrettyJson.encodeToString(JsonElement.serializer(), JsonObject(root + ("newExercises" to exercises)))
    }

    /** A program file read for import: the program without its `newExercises`, and those the library doesn't have. */
    private class ImportFile(val source: String, val exercises: List<Exercise>, val errors: List<String>)

    private suspend fun takeExercises(json: String): ImportFile {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject
        if (root == null || "newExercises" !in root) return ImportFile(json, emptyList(), emptyList())
        val library = db.exercises().all()
        val errors = mutableListOf<String>()
        val exercises = ProgramProposal.fileExercises(root, library.map { it.id }.toSet(), library.associate { it.id to it.name }, errors)
        return ImportFile(JsonObject(root - "newExercises").toString(), exercises, errors)
    }

    /** Deletes saved program [id]; false for one that has run, which keeps its sessions and Changes. */
    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    /** [json] checked against the library and [newExercises], which the library may have gained since the review. */
    private suspend fun check(json: String, newExercises: List<Exercise>): ProgramParser.Result {
        val library = exerciseIds()
        val taken = newExercises.withIndex().filter { it.value.id in library }
        if (taken.isNotEmpty()) return ProgramParser.Result.Invalid(taken.map { "newExercises[${it.index}].id: the library already has \"${it.value.id}\"" })
        val ids = library + newExercises.map { it.id }
        return withContext(Dispatchers.Default) { ProgramParser.parse(json, ids, merged) }
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
        // On training days the rotation moves on by the calendar, not by sessions finished.
        if (dao.program(id)?.rotationFrom != null) return
        val program = program(id) ?: return
        val next = program.next(from) ?: Position(program.weeks + 1, 0)
        dao.advance(id, from.week, from.day, next.week, next.day)
    }

    private suspend fun exerciseIds() = db.exercises().all().map { it.id }.toSet()

    private fun toActive(row: ProgramEntity, changes: List<PlanChangeEntity>, days: Set<DayOfWeek>, starts: List<SessionStartRow>, missed: List<MissedDayEntity>): ActiveProgram? {
        val program = parsed.getOrPut(row.id) { stored(row) } ?: return null
        val history = changes.mapNotNull { c -> c.toAccepted()?.let { ChangeRecord(it, c.acceptedAt, c.revertedAt, c.checkIn?.let(com.hyperlog.app.coach.SavedCheckIn::decode)) } }
        val today = dateOf(clock())
        val rotation = rotation(row, program, history.filter { it.revertedAt == null }.map { it.accepted }, days, starts, today, missed)
        return ActiveProgram(row.id, program, rotation?.next ?: Position(row.week, row.dayIndex), history, today, rotation)
    }

    /**
     * Program [row]'s rotation on [days] today, given the sessions [starts] since its anchor's week,
     * its Changes in force and the days it [missed] before the anchor; null while it runs as a
     * sequence or is finished.
     */
    private fun rotation(
        row: ProgramEntity,
        program: Program,
        changes: List<AcceptedChange>,
        days: Set<DayOfWeek>,
        starts: List<SessionStartRow>,
        today: LocalDate,
        missed: List<MissedDayEntity>,
    ): Rotation? {
        val from = row.rotationFrom ?: return null
        if (days.isEmpty() || row.week > program.weeks) return null
        val sessions = starts.map { s ->
            SessionStart(dateOf(s.startedAt), if (s.programId == row.id && s.week != null && s.dayIndex != null) Position(s.week, s.dayIndex) else null)
        }
        val skips = changes.flatMap { c -> c.change.operations.filterIsInstance<Operation.SkipDay>().mapNotNull { op -> op.date?.let(LocalDate::parse) } }.toSet()
        // A repeat_week accepted on training days leaves the position to the rotation.
        val repeats = changes.filter { c -> c.before == c.after && c.change.operations.any { it is Operation.RepeatWeek } }.map { Rotations.mondayOf(it.acceptedOn) }.toSet()
        val anchor = RotationAnchor(LocalDate.ofEpochDay(from), row.week, row.dayIndex, row.rotationRollback, row.rotationEmptyWeeks)
        val rotation = Rotations.of(program, anchor, days, sessions, today, skips, repeats)
        val kept = missed.map { Turn(LocalDate.ofEpochDay(it.date), Position(it.week, it.dayIndex)) }.filter { it.date < anchor.from }
        return if (kept.isEmpty()) rotation else rotation.copy(missed = (kept + rotation.missed).sortedBy { it.date })
    }

    /** Program [row]'s rotation now, read from the database; null while it runs as a sequence. */
    private suspend fun rotationOf(row: ProgramEntity, days: Set<DayOfWeek>? = null): Rotation? {
        val from = row.rotationFrom ?: return null
        val program = program(row.id) ?: return null
        val changes = changeDao.changes(row.id).filter { it.revertedAt == null }.mapNotNull { it.toAccepted() }
        val plan = days ?: db.weekdayPlan().all().map { DayOfWeek.of(it.weekday) }.toSet()
        return rotation(row, program, changes, plan, db.sessions().starts(sinceOf(from)), dateOf(clock()), db.missedDays().all(row.id))
    }

    /**
     * Program [id] rotates on the training days from today, at its next day; tomorrow once a
     * session started today. Nothing before is missed. Without training days it stays a sequence.
     */
    private suspend fun rotateFromToday(id: Long) {
        if (db.weekdayPlan().all().isEmpty()) return
        val row = dao.program(id) ?: return
        val today = dateOf(clock())
        val trainedToday = db.sessions().starts(today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).any { dateOf(it.startedAt) == today }
        val from = if (trainedToday) today.plusDays(1) else today
        setAnchor(id, null, RotationAnchor(from, row.week, row.dayIndex))
    }

    /** The program running stops, replaced: on training days, at the rotation's next day. */
    private suspend fun deactivate() {
        dao.active()?.let { row -> rotationOf(row)?.let { setAnchor(row.id, it, null) } }
        dao.deactivateAll(clock())
    }

    /**
     * The weekday plan's days changed from [before] to [after]: the program running keeps its place.
     * Cleared, it runs as a sequence from the rotation's next day; set or moved, the rotation is
     * set again from today (ADR 0006).
     */
    suspend fun weekdayPlanChanged(before: Set<DayOfWeek>, after: Set<DayOfWeek>) {
        val row = dao.active() ?: return
        val rotation = if (before.isEmpty()) null else rotationOf(row, before)
        when {
            rotation == null -> if (after.isNotEmpty()) rotateFromToday(row.id)
            else -> setAnchor(row.id, rotation, rotation.anchor.takeIf { after.isNotEmpty() })
        }
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

private val PrettyJson = Json { prettyPrint = true }

/** A saved program's new exercises, waiting until it starts. */
private fun ProgramEntity.waitingExercises(): List<Exercise> = newExercises?.let(ProgramProposal::decodeExercises).orEmpty()

/** Accepts every exercise id, for programs already validated on import. */
private object AnyExercise : Set<String> by emptySet() {
    override fun contains(element: String) = true
}
