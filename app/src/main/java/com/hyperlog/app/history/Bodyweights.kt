package com.hyperlog.app.history

import com.hyperlog.app.data.BodyweightEntity
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Bodyweight on [date]. */
data class Bodyweight(val date: LocalDate, val kg: Double)

/**
 * The bodyweight log (SPEC, Bodyweight): one value a day. Weighted bodyweight exercises add it to
 * the weight logged on the belt or held.
 */
class Bodyweights(db: HyperlogDatabase) {
    private val dao = db.bodyweights()

    /** Every value, newest day first. */
    val entries: Flow<List<Bodyweight>> = dao.observe().map { rows -> rows.map { it.toBodyweight() } }

    /** Logs [kg] for [date], replacing that day's value. */
    suspend fun log(date: LocalDate, kg: Double) = dao.put(BodyweightEntity(date.toEpochDay(), kg))

    suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())

    /** The value of the latest day logged; null with none. */
    suspend fun latest(): Double? = dao.all().firstOrNull()?.kg

    /** The value logged on [date] or the latest before it, else the first one logged; null with none. */
    suspend fun on(date: LocalDate): Double? = dao.all().let { rows -> rows.firstOrNull { it.day <= date.toEpochDay() } ?: rows.lastOrNull() }?.kg

    private fun BodyweightEntity.toBodyweight() = Bodyweight(LocalDate.ofEpochDay(day), kg)
}
