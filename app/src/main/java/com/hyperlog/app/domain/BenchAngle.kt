package com.hyperlog.app.domain

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A bench's backrest angle (SPEC, Today → Bench angle): whole degrees from flat, 0–90, measured
 * with the phone laid on the backrest or typed.
 */
object BenchAngle {
    const val MAX = 90

    /**
     * How far from last time still reads on target. Benches step 10–15° between notches, so 3°
     * tells the same notch from the next while allowing for the pad giving, the phone's camera
     * bump and the floor.
     */
    const val NEAR = 3

    /**
     * The phone's tilt from flat, from the gravity it feels ([x], [y], [z] in its own axes): 0
     * lying flat, face up or down, 90 standing on an edge. Null with no gravity to read.
     */
    fun tilt(x: Float, y: Float, z: Float): Int? {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 0.01f) return null
        return Math.toDegrees(acos((abs(z) / g).coerceIn(0f, 1f).toDouble())).roundToInt().coerceIn(0, MAX)
    }

    /** [angle] is a bench angle: whole degrees from flat to upright. */
    fun valid(angle: Int): Boolean = angle in 0..MAX

    /** A typed angle; null for nothing typed or past 90. */
    fun typed(text: String): Int? = text.toIntOrNull()?.takeIf(::valid)

    /** [angle] is on [target] (the angle set today, else last time's) to within [NEAR]. */
    fun near(angle: Int, target: Int): Boolean = abs(angle - target) <= NEAR
}
