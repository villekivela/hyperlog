package com.hyperlog.app.coach

import androidx.room.withTransaction
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Workouts added to Today from Claude's replies (SPEC, Coaching → Workout). One waits at a time:
 * adding another replaces it. It shows on the day it was added, until a session trains it; that
 * session keeps it, so History lists the session under its name.
 */
class Workouts(private val db: HyperlogDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.workouts()

    /** A workout no session has trained yet, with the day it was added for and the gym picked on Coach. */
    data class Waiting(val id: Long, val workout: Workout, val addedOn: LocalDate, val gymId: Long?)

    /** The workouts waiting, newest first. Today shows the one added today. */
    val waiting: Flow<List<Waiting>> = dao.waitingFlow().map { rows -> rows.mapNotNull { it.toWaiting() } }.distinctUntilChanged()

    /** Adds [workout] for today with the gym picked on Coach ([gymId]); any other waiting goes. */
    suspend fun add(workout: Workout, gymId: Long?): Long = db.withTransaction {
        dao.removeWaiting()
        val now = clock()
        dao.insert(
            WorkoutEntity(
                name = workout.name, json = WorkoutJson.encode(workout), addedAt = now,
                addedOn = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(), gymId = gymId,
            ),
        )
    }

    /** Drops waiting workout [id] and returns it, for [restore]; one a session trains stays, and null comes back. */
    suspend fun remove(id: Long): WorkoutEntity? = db.withTransaction {
        dao.workout(id)?.also { dao.remove(id) }?.takeIf { dao.workout(id) == null }
    }

    /** Puts a removed workout back as it was (Undo): its id, its day and its gym. */
    suspend fun restore(workout: WorkoutEntity) {
        db.withTransaction {
            dao.removeWaiting()
            dao.insert(workout)
        }
    }

    private fun WorkoutEntity.toWaiting(): Waiting? =
        Workout.stored(json)?.let { Waiting(id, it, LocalDate.ofEpochDay(addedOn), gymId) }
}
