package com.hyperlog.app.library

import androidx.room.withTransaction
import com.hyperlog.app.data.ExerciseEntity
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The exercise library on the phone: the shipped seed plus what was added or changed here.
 * Exercises are never deleted, only hidden, because past sessions point at them.
 */
class ExerciseLibrary(private val db: HyperlogDatabase) {
    private val dao = db.exercises()

    /** Every exercise, hidden ones included, by name. */
    val exercises: Flow<List<Exercise>> = dao.observeAll().map { rows -> rows.map { it.toExercise() } }

    /**
     * Brings the library up to [seed]: new exercises are added, and ones never edited on the
     * phone take the seed's values. Edits and hidden flags are kept.
     */
    suspend fun sync(seed: LibrarySeed) = db.withTransaction {
        val rows = dao.all().associateBy { it.id }
        val (known, new) = seed.exercises.partition { it.id in rows }
        dao.insert(new.map { it.toEntity() })
        dao.update(
            known.mapNotNull { e ->
                val row = rows.getValue(e.id)
                e.toEntity(hidden = row.hidden).takeIf { !row.edited && it != row }
            },
        )
    }

    /** Adds [exercise] under a new id made from its name, and returns the id. */
    suspend fun add(exercise: Exercise): String = db.withTransaction {
        val taken = dao.all().map { it.id }.toSet()
        val base = slug(exercise.name)
        val id = (sequenceOf(base) + generateSequence(2) { it + 1 }.map { "${base}_$it" }).first { it !in taken }
        dao.insert(listOf(exercise.copy(id = id, hidden = false).toEntity(edited = true)))
        id
    }

    /** Saves the edited fields of [exercise]. From now on the seed leaves it alone. */
    suspend fun update(exercise: Exercise) = db.withTransaction {
        val row = dao.get(exercise.id) ?: return@withTransaction
        dao.update(listOf(exercise.toEntity(hidden = row.hidden, edited = true)))
    }

    suspend fun setHidden(id: String, hidden: Boolean) = dao.setHidden(id, hidden)

    private fun slug(name: String) =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "exercise" }
}

fun ExerciseEntity.toExercise() = Exercise(
    id = id,
    name = name,
    equipment = Equipment.valueOf(equipment),
    pattern = MovementPattern.valueOf(movementPattern),
    grip = GripDemand.valueOf(grip),
    muscles = muscles.lines().filter { it.isNotEmpty() },
    secondary = secondaryMuscles.lines().filter { it.isNotEmpty() },
    video = videoUrl,
    note = note,
    hidden = hidden,
)

private fun Exercise.toEntity(hidden: Boolean = this.hidden, edited: Boolean = false) = ExerciseEntity(
    id = id,
    name = name.trim(),
    equipment = equipment.name,
    movementPattern = pattern.name,
    grip = grip.name,
    muscles = muscles.joinToString("\n"),
    secondaryMuscles = secondary.joinToString("\n"),
    videoUrl = video?.trim()?.ifEmpty { null },
    note = note?.trim()?.ifEmpty { null },
    hidden = hidden,
    edited = edited,
)
