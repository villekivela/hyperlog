package com.hyperlog.app.coach

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SettingEntity
import com.hyperlog.app.history.History
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.SetPrescriptionJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the coach line from the stored log (SPEC, Coaching → The coach line), and keeps the lines
 * hidden by their ×.
 */
class CoachLineReader(private val db: HyperlogDatabase, private val programs: Programs, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dao = db.sessions()

    /** The keys of the lines hidden by their × ([CoachLine.key]). */
    val hidden: Flow<Set<String>> = db.settings().observe(HIDDEN).map { it.orEmpty().lines().filter(String::isNotBlank).toSet() }

    /** The line Today shows for [active] (null with no program running), with [hidden] lines left out. */
    suspend fun shown(active: ActiveProgram?, hidden: Set<String>): CoachLine? =
        CoachLines.shown(lines(active), hidden, active?.inForce.orEmpty().map { it.change }, active?.program)

    /** Hides [line] until it grows; hidden lines no longer found are forgotten. */
    suspend fun hide(line: CoachLine, active: ActiveProgram?) {
        val found = lines(active).map { it.key }.toSet()
        val kept = db.settings().get(HIDDEN).orEmpty().lines().filter { it in found }
        db.settings().put(SettingEntity(HIDDEN, (kept + line.key).distinct().joinToString("\n")))
    }

    /** Every pattern in the log whose latest session or missed day is within the last 4 weeks. */
    private suspend fun lines(active: ActiveProgram?): List<CoachLine> {
        val today = active?.today ?: LocalDate.now(zone)
        val log = log(active, today)
        val dates = log.sessions.associate { it.id to it.date }
        val since = today.minusDays(RECENT_DAYS)
        return CoachLines.of(log).filter { line ->
            val last = when (line) {
                is CoachLine.DayMissed -> line.last
                is CoachLine.Stall -> dates[line.last]
                is CoachLine.Grinding -> dates[line.last]
                is CoachLine.SameSwap -> dates[line.last]
            }
            last != null && last >= since
        }
    }

    /**
     * The log as the coach line reads it: the running program's turns, and every finished session
     * with each exercise's estimate after it, its sets against their targets, and its swap. Slots
     * are read only in the running program's sessions, whose slot ids they are.
     */
    private suspend fun log(active: ActiveProgram?, today: LocalDate): CoachLog {
        val history = History(db, programs, zone) { today }
        // The estimate after each session, by session and exercise, on its machine for machines.
        val estimates = history.exercises().flatMap { trend ->
            trend.lines.flatMap { line -> line.points.map { (it.sessionId to trend.exercise.id) to (line.itemId to it.estimateKg) } }
        }.toMap()
        val gyms = db.gyms().observeGyms().first().associate { it.id to it.name }
        val sessions = dao.finishedSessions().reversed().map { row ->
            val day = programs.plannedDay(row)
            val ownSlots = row.programId != null && row.programId == active?.id
            LoggedSession(
                id = row.id,
                date = dateOf(row.startedAt),
                gym = row.gymId?.let(gyms::get),
                eased = row.easy || day?.deload == true,
                exercises = dao.sessionExercises(row.id).filter { !it.removed }.mapNotNull { se ->
                    val sets = dao.sets(se.id).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val slot = day?.slot(se.slotId)
                    val targets = se.sets?.let(SetPrescriptionJson::decode) ?: slot?.setsFor(se.exerciseId)
                    val (machine, estimate) = estimates[row.id to se.exerciseId] ?: (null to null)
                    LoggedExercise(
                        exercise = se.exerciseId,
                        machine = machine,
                        estimateKg = estimate,
                        sets = sets.mapIndexed { i, set -> RirSet(set.rir, targets?.getOrNull(i)?.rir) },
                        slot = se.slotId.takeIf { ownSlots },
                        inPlaceOf = slot?.exercise?.takeIf { ownSlots && it != se.exerciseId },
                    )
                },
            )
        }
        return CoachLog(turns(active, today, sessions), sessions)
    }

    /**
     * The running program's past turns of each day: the training days the rotation missed, and
     * the sessions that trained the day.
     */
    private suspend fun turns(active: ActiveProgram?, today: LocalDate, sessions: List<LoggedSession>): List<DayTurn> {
        active ?: return emptyList()
        fun turn(date: LocalDate, position: Position, missed: Boolean): DayTurn? {
            val day = active.program.blockAt(position.week)?.days?.getOrNull(position.day) ?: return null
            return DayTurn(date, day.id, day.name, missed)
        }
        val missed = active.rotation?.missed.orEmpty().filter { it.date < today }.mapNotNull { turn(it.date, it.position, missed = true) }
        val dates = sessions.associate { it.id to it.date }
        val trained = dao.finishedSessions().filter { it.programId == active.id && it.week != null && it.dayIndex != null && it.workoutId == null }
            .mapNotNull { row -> turn(dates.getValue(row.id), Position(row.week!!, row.dayIndex!!), missed = false) }
        return (missed + trained).sortedBy { it.date }
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private companion object {
        /** The setting holding the hidden lines' keys, one a line. */
        const val HIDDEN = "coach_line_hidden"
        const val RECENT_DAYS = 28L
    }
}
