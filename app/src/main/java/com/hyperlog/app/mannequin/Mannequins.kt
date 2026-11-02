package com.hyperlog.app.mannequin

import android.content.Context
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The mannequin clips the app ships (`assets/mannequins/`, baked by `tools/mannequins/`), indexed
 * by exercise in `mannequins.json` (SPEC, Technique videos → The mannequin).
 */
class Mannequins(val clips: List<MannequinClip>) {
    private val byExercise = clips.associateBy { it.exerciseId }

    /**
     * The clip for [exercise], or null for one without a pose yet. An exercise added on the phone
     * has an id of its own, so it never matches; a shipped one whose equipment was edited to
     * something else no longer matches its pose's.
     */
    fun clipFor(exercise: Exercise): MannequinClip? =
        byExercise[exercise.id]?.takeIf { it.equipment == exercise.equipment }

    companion object {
        const val DIR = "mannequins"

        fun fromAssets(context: Context): Mannequins =
            parse(context.assets.open("$DIR/mannequins.json").bufferedReader().use { it.readText() })

        fun parse(json: String): Mannequins {
            val file = Json.decodeFromString<IndexFile>(json)
            return Mannequins(
                file.clips.map { (id, e) ->
                    MannequinClip(id, "$DIR/${e.clip}", Equipment.ofKey(e.equipment), e.camera, e.tempo.timeline, e.resources.associateWith { "$DIR/$it" })
                },
            )
        }
    }
}

/**
 * One exercise's baked clip: its `.glb` in the app's assets, the equipment its pose is in, how the
 * stage frames it, the [tempo] its rep plays at, and the files it reads by name ([resources], name to asset): the mannequin's
 * mesh and skin, which every clip shares.
 */
data class MannequinClip(
    val exerciseId: String,
    val file: String,
    val equipment: Equipment,
    val camera: StageCamera,
    val tempo: RepTimeline,
    val resources: Map<String, String> = emptyMap(),
)

/**
 * Where the stage's camera looks ([target], x y z in metres), half the height it shows at the
 * target ([halfHeight], metres), and the turn and tilt it opens on, in degrees.
 */
@Serializable
data class StageCamera(val target: List<Float>, val halfHeight: Float, val azimuth: Float, val elevation: Float)

/** A tempo in the index: seconds in the lowering, the pause at the bottom, the lifting and the pause at the top. */
@Serializable
private class IndexTempo(val lowering: Float, val bottom: Float, val lifting: Float, val top: Float) {
    val timeline get() = RepTimeline(lowering, bottom, lifting, top)
}

@Serializable
private class IndexFile(val clips: Map<String, IndexEntry>)

@Serializable
private class IndexEntry(val clip: String, val equipment: String, val camera: StageCamera, val tempo: IndexTempo, val resources: List<String> = emptyList())
