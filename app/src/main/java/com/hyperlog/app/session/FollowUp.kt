package com.hyperlog.app.session

import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.Technique

/**
 * What comes next in a set that is still open: a drop, a myo mini-set or an iso hold. They follow
 * the set with no rest (a myo mini-set after a short breathing pause) and record no RIR.
 */
sealed interface FollowUp {
    /** Drop [index] (from 0) of [count]. */
    data class Drop(val index: Int, val count: Int) : FollowUp

    /** Mini-set [index] (from 0) of at most [max], aiming for [target] reps. */
    data class MiniSet(val index: Int, val max: Int, val target: Int) : FollowUp

    data class Hold(val seconds: Int, val position: HoldPosition) : FollowUp
}

/** How long a myo-reps breathing pause before a mini-set is. */
const val BREATHING_MS = 15_000L

/** Drop reps with nothing from last time to prefill them from. */
const val DEFAULT_DROP_REPS = 6

/**
 * The next continuation of this set, or null once its technique has none left: after the last
 * planned drop, after a mini-set that misses its target or the last one allowed, after the hold.
 */
fun LoggedSet.followUp(): FollowUp? = when (val t = technique) {
    is Technique.Dropset -> extensions.size.takeIf { it < t.drops }?.let { FollowUp.Drop(it, t.drops) }
    is Technique.MyoReps -> {
        val last = extensions.lastOrNull()
        val missed = last != null && last.reps < t.miniSetReps
        if (missed || extensions.size >= t.maxMiniSets) null else FollowUp.MiniSet(extensions.size, t.maxMiniSets, t.miniSetReps)
    }
    is Technique.IsoHold -> if (extensions.isEmpty()) FollowUp.Hold(t.seconds, t.position) else null
    else -> null
}

val FollowUp.kind: Extension.Kind
    get() = when (this) {
        is FollowUp.Drop -> Extension.Kind.Drop
        is FollowUp.MiniSet -> Extension.Kind.MiniSet
        is FollowUp.Hold -> Extension.Kind.Hold
    }

/** The technique with one more drop or mini-set; null for one with neither. */
fun Technique.oneMore(): Technique? = when (this) {
    is Technique.Dropset -> copy(drops = drops + 1)
    is Technique.MyoReps -> copy(maxMiniSets = maxMiniSets + 1)
    else -> null
}
