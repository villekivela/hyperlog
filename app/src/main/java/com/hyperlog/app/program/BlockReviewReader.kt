package com.hyperlog.app.program

import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.History
import com.hyperlog.app.history.LiftGoals
import com.hyperlog.app.library.toExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A finished block's review Today points at, until it's opened; [programDone] once the program is finished. */
data class DueReview(val programId: Long, val blockId: String, val blockName: String, val programDone: Boolean)

/**
 * A review as its page shows it: program [programId]'s [block] and its [review], the [whole]
 * program beside it on the program review, and the [next] block.
 */
data class ReviewPage(
    val programId: Long,
    val program: Program,
    val block: Block,
    val review: Review,
    val whole: Review?,
    val next: Block?,
)

/**
 * Reads block reviews from the stored log (SPEC, Program → Block review), and keeps which ones
 * were opened, so Today stops pointing at them.
 */
class BlockReviewReader(private val db: HyperlogDatabase, private val programs: Programs, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dao = db.sessions()

    /** The reviews opened, as `programId:blockId`. */
    val opened: Flow<Set<String>> = db.settings().observe(OPENED).map { it.orEmpty().lines().filter(String::isNotBlank).toSet() }

    /** The review was opened: Today no longer points at it. */
    suspend fun open(programId: Long, blockId: String) {
        val kept = db.settings().get(OPENED).orEmpty().lines().filter(String::isNotBlank)
        db.settings().put(SettingEntity(OPENED, (kept + key(programId, blockId)).distinct().joinToString("\n")))
    }

    /**
     * The review Today points at for [active]: its last finished block, unless [opened] or the
     * next block's first session has started; the last block once the program is finished.
     */
    suspend fun due(active: ActiveProgram?, opened: Set<String>): DueReview? {
        active ?: return null
        val block = BlockReviews.finished(active.program, active.position).lastOrNull() ?: return null
        if (key(active.id, block.id) in opened) return null
        val started = (dao.finishedSessions() + listOfNotNull(dao.runningSession()))
            .any { it.programId == active.id && it.workoutId == null && (it.week ?: 0) > block.weekEnd }
        if (started) return null
        return DueReview(active.id, block.id, block.name, programDone = active.finished)
    }

    /**
     * Program [programId]'s review of block [blockId] (null: the last block it trained, else the
     * one it was at), with the whole program beside it [withProgram]; null with no such block.
     */
    suspend fun page(programId: Long, blockId: String?, withProgram: Boolean): ReviewPage? {
        val program = programs.program(programId) ?: return null
        val log = log(programId, program)
        val block = if (blockId != null) program.blocks.firstOrNull { it.id == blockId } ?: return null
        else {
            // On training days the rotation, not the row, knows where the program running is.
            val week = log.sessions.maxOfOrNull { it.week }
                ?: programs.active.first()?.takeIf { it.id == programId }?.position?.week ?: db.programs().program(programId)?.week ?: return null
            program.blockAt(week.coerceAtMost(program.weeks)) ?: return null
        }
        return ReviewPage(
            programId, program, block, BlockReviews.of(log, block),
            if (withProgram) BlockReviews.program(log, block) else null,
            program.blocks.getOrNull(program.blocks.indexOf(block) + 1),
        )
    }

    /** The log of program [id]'s run as the review reads it. */
    private suspend fun log(id: Long, program: Program): ReviewLog {
        val rows = dao.finishedSessions().reversed()
        val byId = rows.associateBy { it.id }
        val run = rows.filter { it.programId == id && it.workoutId == null && it.week != null && it.dayIndex != null }
        // Each session of the run: eased by the plan, and in a deload or an easy week.
        val kinds = run.associate { row ->
            val day = programs.plannedDay(row)
            row.id to ((day?.eased == true) to (row.easy || day?.deload == true))
        }
        val history = History(db, programs, zone)
        val lines = history.exercises().flatMap { trend ->
            trend.lines.map { line ->
                ReviewLine(
                    trend.exercise.id, line.machine,
                    line.points.map { p ->
                        val row = byId[p.sessionId]
                        val kind = kinds[p.sessionId]
                        ReviewPoint(
                            p.date, row?.week?.takeIf { kind != null }, kind?.first == true, p.estimateKg,
                            p.record?.let { ReviewRecord(it, p.top.weightKg, p.top.fullReps) },
                            deload = kind?.second == true,
                        )
                    },
                )
            }
        }
        val exercises = db.exercises().all().map { it.toExercise() }
        val muscles = exercises.associate { e -> e.id to (e.secondary.associateWith { 0.5 } + e.muscles.associateWith { 1.0 }) }
        val sessions = run.map { row ->
            val sets = mutableMapOf<String, Double>()
            dao.sessionExercises(row.id).filter { !it.removed }.forEach { se ->
                val count = dao.sets(se.id).size
                muscles[se.exerciseId].orEmpty().forEach { (m, per) -> sets[m] = (sets[m] ?: 0.0) + per * count }
            }
            ReviewSession(dateOf(row.startedAt), row.week!!, sets.filterValues { it > 0 })
        }
        val changes = db.planChanges().changes(id).mapNotNull { c ->
            val summary = runCatching { ChangeJson.decode(c.json).summary }.getOrNull() ?: return@mapNotNull null
            c.blockId?.let { ReviewChange(it, summary) }
        }
        return ReviewLog(
            program, lines, turns(id, program, run), sessions, muscles, changes,
            db.bodyweights().all().map { Bodyweight(LocalDate.ofEpochDay(it.day), it.kg) },
            LiftGoals(db).all.first(),
        )
    }

    /**
     * The run's turns on training days: the days missed (the rotation's while it runs, the kept
     * ones after), and the sessions of its days. None while it ran as a sequence.
     */
    private suspend fun turns(id: Long, program: Program, run: List<SessionEntity>): List<ReviewTurn> {
        val active = programs.active.first()?.takeIf { it.id == id }
        val missed = active?.rotation?.missed?.filter { it.date < active.today }?.map { Triple(it.date, it.position, it.easy) }
            ?: db.missedDays().all(id).map { Triple(LocalDate.ofEpochDay(it.date), Position(it.week, it.dayIndex), false) }
        if (missed.isEmpty() && active?.rotation == null) return emptyList()
        fun name(position: Position) = program.blockAt(position.week)?.days?.getOrNull(position.day)?.name?.substringBefore(" — ")
        val trained = run.mapNotNull { row ->
            val position = Position(row.week!!, row.dayIndex!!)
            name(position)?.let { ReviewTurn(dateOf(row.startedAt), position.week, it, easy = row.easy) }
        }
        return (missed.mapNotNull { (date, position, easy) -> name(position)?.let { ReviewTurn(date, position.week, it, missed = true, easy = easy) } } + trained)
            .sortedBy { it.date }
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun key(programId: Long, blockId: String) = "$programId:$blockId"

    private companion object {
        /** The setting holding the opened reviews' keys, one a line. */
        const val OPENED = "block_review_opened"
    }
}
