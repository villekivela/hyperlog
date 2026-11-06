package com.hyperlog.app.mannequin

import android.content.Context
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One rep drawn flat: each joint's place in a 0–1000 box at its [start] and [end], as x, y pairs in
 * [joints]' order, and the side further from the camera ([far], `l` or `r`).
 */
data class RepPose(val joints: List<String>, val start: List<Int>, val end: List<Int>, val far: String)

/** [pose] for an exercise, and whether it is that exercise's own ([exact]) or its pattern's. */
data class PoseShown(val pose: RepPose, val exact: Boolean)

/** Every exercise's flat rep, baked from the clips by `tools/exercise-poses/bake.py`. */
class ExercisePoses(private val joints: List<String>, private val poses: Map<String, PoseEntry>) {
    /** The rep to draw for [exercise]: its own, else one of its movement pattern, else any; null with none baked. */
    fun poseFor(exercise: Exercise): PoseShown? {
        val own = poses[exercise.id]?.takeIf { Equipment.ofKey(it.equipment) == exercise.equipment }
        if (own != null) return PoseShown(own.rep(), exact = true)
        val stand = poses.values.firstOrNull { it.pattern == exercise.pattern.key } ?: poses.values.firstOrNull()
        return stand?.let { PoseShown(it.rep(), exact = false) }
    }

    private fun PoseEntry.rep() = RepPose(joints, start, end, far)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExercisePoses = json.decodeFromString<PoseFile>(text).let { ExercisePoses(it.joints, it.poses) }

        fun fromAssets(context: Context): ExercisePoses =
            parse(context.assets.open("exercise-poses.json").bufferedReader().use { it.readText() })
    }
}

@Serializable
class PoseEntry(val start: List<Int>, val end: List<Int>, val far: String, val equipment: String, val pattern: String)

@Serializable
private class PoseFile(val joints: List<String>, val poses: Map<String, PoseEntry>)
