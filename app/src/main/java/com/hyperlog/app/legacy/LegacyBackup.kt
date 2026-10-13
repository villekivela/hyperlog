package com.hyperlog.app.legacy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import com.hyperlog.app.progression.Progression
import java.time.Instant
import kotlin.math.roundToInt

/**
 * A starting weight (SPEC, Fresh start): an exercise's top work set (12 reps or fewer if it can) in
 * the latest old-app session that had one, in the new app's terms. [kg] is per dumbbell and, for machines, the total with the
 * base; [rir] is null when the set had no RPE. [doneAt] is when that session was.
 */
data class StartingWeight(val exerciseId: String, val kg: Double, val reps: Int, val rir: Int?, val doneAt: Long)

/** Reads a legacy Hyperlog full backup (format 7.0). Only work sets are read; no history is kept. */
object LegacyBackup {
    sealed interface Result {
        data class Read(val weights: List<StartingWeight>) : Result
        /** Not a legacy full backup, or not readable. */
        data object Invalid : Result
    }

    /**
     * The old app logged these as the total of both dumbbells (SPEC); the new app logs per
     * dumbbell. Kept ids, after [merged] maps old ones.
     */
    val DumbbellTotals = setOf(
        "seated_db_shoulder_press", "db_bench_press", "db_incline_press", "chest_supported_db_row",
        "incline_chest_supported_db_row", "db_romanian_deadlift",
    )

    private const val BASE_PREFIX = "machine_base_weight_"

    /**
     * The starting weight of every exercise in [library] the backup has work sets for. Old ids go
     * through [merged] (legacy id -> kept id) first.
     */
    fun startingWeights(json: String, library: Set<String>, merged: Map<String, String>): Result {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return Result.Invalid
        if (root["type"].text() != "full_backup") return Result.Invalid
        // Format 7.0 is the layout read here; other versions keep their data elsewhere.
        if (root["version"].text()?.substringBefore(".") != "7") return Result.Invalid
        val logs = root["workoutLogs"] as? JsonArray ?: return Result.Invalid
        val bases = (root["settings"] as? JsonArray).orEmpty().mapNotNull { s ->
            val o = s as? JsonObject ?: return@mapNotNull null
            val key = o["key"].text() ?: return@mapNotNull null
            val value = (o["value"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (key.startsWith(BASE_PREFIX)) key.removePrefix(BASE_PREFIX) to value else null
        }.toMap()
        val latest = mutableMapOf<String, StartingWeight>()
        for (log in logs) {
            val o = log as? JsonObject ?: continue
            val at = o["date"].text()?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: continue
            for (entry in (o["exercises"] as? JsonArray).orEmpty()) {
                val e = entry as? JsonObject ?: continue
                val legacyId = e["exerciseId"].text() ?: continue
                val id = merged[legacyId] ?: legacyId
                if (id !in library) continue
                val work = (e["sets"] as? JsonArray).orEmpty().mapNotNull { work(it, bases[legacyId] ?: 0.0) }
                // The heaviest set, with the most reps at it; high-rep sets only when there is nothing else.
                val top = work.filter { it.reps <= Progression.MAX_ESTIMATE_REPS }.ifEmpty { work }
                    .maxWithOrNull(compareBy<Work> { it.kg }.thenBy { it.reps }) ?: continue
                if ((latest[id]?.doneAt ?: Long.MIN_VALUE) > at) continue
                val kg = if (id in DumbbellTotals) top.kg / 2 else top.kg
                latest[id] = StartingWeight(id, kg, top.reps, top.rir, at)
            }
        }
        return Result.Read(latest.values.sortedBy { it.exerciseId })
    }

    private class Work(val kg: Double, val reps: Int, val rir: Int?)

    /** A work or AMRAP set with reps, its weight including the machine [base] when logged as added. */
    private fun work(set: kotlinx.serialization.json.JsonElement, base: Double): Work? {
        val s = set as? JsonObject ?: return null
        if (s["setType"].text() !in setOf("work", "amrap")) return null
        val reps = (s["reps"] as? JsonPrimitive)?.intOrNull ?: return null
        if (reps <= 0) return null
        val weight = (s["weight"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val added = s["machineWeightMode"].text() == "added"
        val rir = (s["rpe"] as? JsonPrimitive)?.doubleOrNull?.let { (10 - it).roundToInt().coerceIn(0, 10) }
        return Work(if (added) weight + base else weight, reps, rir)
    }

    private fun kotlinx.serialization.json.JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
