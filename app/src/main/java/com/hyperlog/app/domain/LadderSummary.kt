package com.hyperlog.app.domain

/**
 * The ladder in one line, as setup checks it and the equipment lists show it: its runs of steps
 * (`1–10 kg by 1 · 12.5–50 by 2.5`, `1.1–89.6 kg by 5 lb`), `+ add-ons` for a pin stack with any at
 * a gym with [pinPlates], and plates as their bar and top.
 */
fun LadderSpec.summary(pinPlates: List<Double> = emptyList()): String = when (this) {
    is LadderSpec.PinStack -> StackReading.summary(this) +
        if (builtIn.isNotEmpty() || (this.pinPlates && pinPlates.isNotEmpty())) " + add-ons" else ""
    is LadderSpec.Steps -> StackReading.summary(
        LadderSpec.PinStack(unit, List(count.coerceIn(1, 200)) { start + it * step }),
    )
    is LadderSpec.Explicit -> StackReading.summary(LadderSpec.PinStack(WeightUnit.Kg, kgs.filter { it > 0 }.distinct().sorted().ifEmpty { listOf(0.0) }))
    is LadderSpec.Plates -> "${formatKg(baseKg)} kg ${if (baseKg > 0) "bar " else ""}+ plates, to ${formatKg(ladder().rungs.last())} kg"
}
