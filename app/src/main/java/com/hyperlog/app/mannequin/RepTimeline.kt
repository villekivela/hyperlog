package com.hyperlog.app.mannequin

/**
 * One rep in a loop, in four phases, in seconds: the lowering, the pause at the bottom, the
 * lifting and the pause at the top. A clip bakes only the lowering, so the loop plays it forward,
 * holds, plays it backward and holds again. Each exercise's comes from the index (SPEC, Technique
 * videos → The mannequin).
 */
data class RepTimeline(val lowering: Float, val bottom: Float, val lifting: Float, val top: Float) {
    val length: Float get() = lowering + bottom + lifting + top

    /** How far into the clip the rep is after [seconds]: 0 at the top, 1 at the bottom. */
    fun position(seconds: Float): Float {
        var t = seconds.mod(length)
        if (t < lowering) return ease(t / lowering)
        t -= lowering
        if (t < bottom) return 1f
        t -= bottom
        if (t < lifting) return 1f - ease(t / lifting)
        return 0f
    }

    companion object {
        /** Each move starts and ends gently. */
        private fun ease(t: Float) = t * t * (3 - 2 * t)
    }
}
