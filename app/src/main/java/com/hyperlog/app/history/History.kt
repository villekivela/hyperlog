package com.hyperlog.app.history

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.TrainedExercise
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.counting
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Performance
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.progression.WorkSet
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.session.toExtension
import com.hyperlog.app.session.toLoggedSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first

/** The work sets each muscle got in the week starting Monday [start], in body order. */
data class WeekSets(val start: LocalDate, val sets: Map<String, Int>)

/** One session on a trend: its top set and the estimate after it. */
data class TrendPoint(
    val sessionId: Long,
    val date: LocalDate,
    /** The heaviest work set, with the most reps at that weight. */
    val top: LoggedSet,
    /** The estimate after this session, as suggestions saw it; null with no set that gives one. */
    val estimateKg: Double?,
)

/**
 * An exercise's trend on one machine ([machine] reads `Cable left · Kamppi`), or across gyms for a
 * free weight ([machine] null).
 */
data class TrendLine(val machine: String?, val itemId: Long?, val points: List<TrendPoint>)

/**
 * An exercise's top sets and estimate over time: one line, or one per machine for machines
 * (ADR 0002), the one used last first.
 */
data class ExerciseTrend(val exercise: Exercise, val lines: List<TrendLine>, val goal: LiftGoal? = null)

/** A lift goal and the estimate on its way there: [points] oldest first, as on the exercise's trend. */
data class GoalProgress(val goal: LiftGoal, val points: List<TrendPoint>) {
    /** The current estimate; null before a session gives one. */
    val estimateKg: Double? get() = points.lastOrNull { it.estimateKg != null }?.estimateKg
}

/**
 * What was done and whether it's working (SPEC, Screens → History), read from finished sessions:
 * weekly sets per muscle and each exercise's trend. A session is a record: nothing here changes it.
 */
class History(
    private val db: HyperlogDatabase,
    private val programs: Programs,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) {
    private val dao = db.sessions()
    private val bodyweights = Bodyweights(db)
    private val liftGoals = LiftGoals(db)

    /**
     * Sets per primary muscle for the last [weeks] weeks (Monday to Sunday), newest first, this
     * week included. Every logged work set counts once for each of its exercise's primary muscles,
     * whatever partials, drops or mini-sets extend it (SPEC, Volume). Warm-ups are never logged.
     */
    suspend fun weeklySets(weeks: Int = 8): List<WeekSets> {
        val thisWeek = monday(today())
        val starts = (0 until weeks).map { thisWeek.minusWeeks(it.toLong()) }
        val muscles = db.exercises().all().associate { it.id to it.toExercise().muscles }
        val counts = starts.associateWith { mutableMapOf<String, Int>() }
        for (session in dao.finishedSessions()) {
            val week = counts[monday(dateOf(session.startedAt))] ?: continue
            for (se in dao.sessionExercises(session.id)) {
                if (se.removed) continue
                val sets = dao.sets(se.id).size
                if (sets == 0) continue
                for (m in muscles[se.exerciseId].orEmpty()) week[m] = (week[m] ?: 0) + sets
            }
        }
        return starts.map { start -> WeekSets(start, counts.getValue(start).inBodyOrder()) }
    }

    /** Every exercise with sets in a finished session, the one done last first, with its trend. */
    suspend fun exercises(): List<ExerciseTrend> =
        dao.trained().reversed().map { it.exerciseId }.distinct().map { trend(it) }

    /** Every lift goal with its exercise's estimates, for exercises that take one. */
    suspend fun goals(): List<GoalProgress> = liftGoals.all.first().mapNotNull { goal ->
        db.exercises().get(goal.exerciseId) ?: return@mapNotNull null
        val trend = trend(goal.exerciseId).takeIf { it.goal != null } ?: return@mapNotNull null
        GoalProgress(goal, trend.lines.firstOrNull()?.points.orEmpty())
    }

    /** [exerciseId]'s trend from every finished session with it. */
    suspend fun trend(exerciseId: String): ExerciseTrend {
        val exercise = db.exercises().get(exerciseId)!!.toExercise()
        val perMachine = exercise.equipment.perMachine
        val machines = Gyms(db).machineNames()
        val done = dao.trained(exerciseId)
        val groups = if (perMachine) done.groupBy { it.equipmentItemId } else mapOf(null to done)
        val lines = groups.entries
            .sortedByDescending { (_, rows) -> rows.last().startedAt }
            .map { (itemId, rows) -> TrendLine(itemId?.let { machines[it] }, itemId, points(rows, weighted = exercise.equipment == Equipment.WeightedBodyweight, dumbbells = exercise.implements)) }
        return ExerciseTrend(exercise, lines, goal = liftGoals.of(exerciseId)?.takeIf { exercise.takesGoal })
    }

    /**
     * Each session's top set and the estimate after it, from [rows] oldest first. A [weighted]
     * bodyweight exercise adds the bodyweight of the day to the weight logged; a two-dumbbell one
     * counts both [dumbbells].
     */
    private suspend fun points(rows: List<TrainedExercise>, weighted: Boolean, dumbbells: Int): List<TrendPoint> {
        val performances = mutableListOf<Performance>()
        return rows.mapNotNull { row ->
            val sets = dao.sets(row.id).map { it.toLoggedSet(dao.extensions(it.id).map { e -> e.toExtension() }) }
            val counting = sets.counting()
            performances += Performance(
                // The main set only, as progression reads it.
                sets.mapIndexed { i, s -> WorkSet(LoggedSet(s.weightKg, s.progressionReps, 0, s.rir, s.kept), null, counting[i]) },
                deload = deload(row),
                bodyweightKg = if (weighted) bodyweights.on(dateOf(row.startedAt)) ?: 0.0 else 0.0,
                dumbbells = dumbbells,
            )
            val top = sets.filterIndexed { i, _ -> counting[i] }
                .maxWithOrNull(compareBy<LoggedSet> { it.weightKg }.thenBy { it.progressionReps })
                ?: return@mapNotNull null
            // The estimate as suggestions read it: from the latest sessions up to this one.
            val estimate = Progression.estimate(performances.takeLast(SessionLogger.HISTORY_SESSIONS))
            TrendPoint(row.sessionId, dateOf(row.startedAt), top, estimate?.kg)
        }
    }

    private suspend fun deload(row: TrainedExercise): Boolean {
        if (row.programId == null || row.week == null || row.dayIndex == null) return false
        return programs.plannedDay(row.programId, Position(row.week, row.dayIndex), programs.dateOf(row.startedAt))?.deload == true
    }

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun Map<String, Int>.inBodyOrder(): Map<String, Int> =
        entries.sortedBy { (m, _) -> Muscles.indexOf(m).let { if (it < 0) Int.MAX_VALUE else it } }.associate { it.key to it.value }
}
