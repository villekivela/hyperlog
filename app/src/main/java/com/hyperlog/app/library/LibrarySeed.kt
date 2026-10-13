package com.hyperlog.app.library

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The exercise library the app ships with (`assets/exercises.json`): the legacy app's library,
 * cleaned up. [merged] maps each legacy id that was merged into another exercise to the id kept,
 * for anything that still carries an old id (a legacy backup's starting weights).
 */
class LibrarySeed(val exercises: List<Exercise>, val merged: Map<String, String>) {
    companion object {
        fun fromAssets(context: Context): LibrarySeed =
            parse(context.assets.open("exercises.json").bufferedReader().use { it.readText() })

        fun parse(json: String): LibrarySeed {
            val file = Json.decodeFromString<SeedFile>(json)
            return LibrarySeed(file.exercises.map { it.toExercise() }, file.merged)
        }
    }
}

@Serializable
private class SeedFile(val merged: Map<String, String>, val exercises: List<SeedExercise>)

@Serializable
private class SeedExercise(
    val id: String,
    val name: String,
    val equipment: String,
    val pattern: String,
    val grip: String,
    val muscles: List<String>,
    val secondary: List<String> = emptyList(),
    val video: String? = null,
    val note: String? = null,
    val twoDumbbells: Boolean = false,
) {
    fun toExercise() = Exercise(
        id = id,
        name = name,
        equipment = Equipment.ofKey(equipment),
        pattern = MovementPattern.ofKey(pattern),
        grip = GripDemand.ofKey(grip),
        muscles = muscles,
        secondary = secondary,
        video = video,
        note = note,
        twoDumbbells = twoDumbbells,
    )
}
