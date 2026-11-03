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
                    MannequinClip(
                        id, "$DIR/${e.clip}", Equipment.ofKey(e.equipment), e.camera, e.tempo.timeline, e.resources.associateWith { "$DIR/$it" },
                        e.bench?.let { b -> BenchClips(b.typical, b.hinge, b.clips.entries.associate { (angle, c) -> angle.toInt() to BenchClip("$DIR/${c.clip}", c.camera) }) },
                        e.grips?.map { g -> GripClip(g.name, "$DIR/${g.clip}", g.camera) },
                    )
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
    val bench: BenchClips? = null,
    /** An exercise done with a different grip set by set: a clip per grip, in the order the sets take them; null with one grip. */
    val grips: List<GripClip>? = null,
) {
    /** The grip the set at [set] (0 the first) takes; a set past the last grip keeps the last. */
    fun gripForSet(set: Int): GripClip = grips!!.let { it[set.coerceIn(0, it.size - 1)] }
}

/** One grip of an exercise done with a different grip set by set: its [name] (`wide`, `narrow`), its `.glb` in the assets and how the stage frames it. */
data class GripClip(val name: String, val file: String, val camera: StageCamera)

/** One baked bench angle of an adjustable-bench exercise: its `.glb` in the assets and how the stage frames it. */
data class BenchClip(val file: String, val camera: StageCamera)

/**
 * An adjustable-bench exercise's clips, one per baked bench angle ([clips], by degrees), the
 * [typical] angle it is shown at when no angle was ever set, and the [hinge] ([y, z], metres) the
 * backrest turns about.
 */
data class BenchClips(val typical: Int, val hinge: List<Float>, val clips: Map<Int, BenchClip>) {
    /**
     * What to show for this session's angle, otherwise last time's, otherwise the typical one: the
     * [BenchShown.angle] the bench stands at, and the nearest baked clip the pose comes from. An
     * angle outside the baked ones takes the end clip, and the bench still turns to it.
     */
    fun shown(session: Int?, last: Int?): BenchShown {
        val angle = session ?: last ?: typical
        val baked = clips.keys.sorted().minBy { kotlin.math.abs(it - angle) }
        return BenchShown(angle, baked, clips.getValue(baked), hinge)
    }
}

/** The glTF node of a clip's bench back, which the phone turns to the exact angle. */
const val BENCH_BACK = "bench_back"

/**
 * The transform (column-major 4×4) of the bench's back pad stood at [degrees] about the [hinge]
 * ([y, z]): the same node the clip bakes at its own angle, turned to the exact one.
 */
fun benchBackTransform(degrees: Int, hinge: List<Float>): FloatArray {
    val a = Math.toRadians(degrees.toDouble())
    val c = kotlin.math.cos(a).toFloat()
    val s = kotlin.math.sin(a).toFloat()
    return floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, c, s, 0f,
        0f, -s, c, 0f,
        0f, hinge[0], hinge[1], 1f,
    )
}

/** A bench angle shown: the exact [angle], the [baked] angle whose [clip] supplies the pose, and the [hinge] ([y, z]) the backrest turns about. */
data class BenchShown(val angle: Int, val baked: Int, val clip: BenchClip, val hinge: List<Float>)

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
private class IndexEntry(val clip: String, val equipment: String, val camera: StageCamera, val tempo: IndexTempo, val resources: List<String> = emptyList(), val bench: IndexBench? = null, val grips: List<IndexGrip>? = null)

@Serializable
private class IndexGrip(val name: String, val clip: String, val camera: StageCamera)

@Serializable
private class IndexBenchClip(val clip: String, val camera: StageCamera)

@Serializable
private class IndexBench(val typical: Int, val hinge: List<Float>, val clips: Map<String, IndexBenchClip>)
