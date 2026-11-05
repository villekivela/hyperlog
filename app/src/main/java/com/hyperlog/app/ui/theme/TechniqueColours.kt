package com.hyperlog.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.hyperlog.app.program.TechniqueKind

// Each intensity technique's own colour (SPEC, Palette → Technique colours): red for a drop set,
// teal for lengthened partials, violet for myo-reps, orange for to failure, blue for an iso hold.
// It fills the set's index circle and tall pill and tints the technique's icon in the plan text
// and the technique sheet. Darker in light, lighter in dark, so it keeps 3:1 on every surface.

/** The technique's colour: a fill, and an icon tint that keeps 3:1 on the theme's surfaces. */
fun techniqueColor(kind: TechniqueKind, dark: Boolean): Color = Color(
    when (kind) {
        TechniqueKind.Dropset -> if (dark) 0xFFFF7A70 else 0xFFC62F2A
        TechniqueKind.LengthenedPartials -> if (dark) 0xFF4FD0C8 else 0xFF0B7A75
        TechniqueKind.MyoReps -> if (dark) 0xFFB38CFF else 0xFF7A45C9
        TechniqueKind.Failure -> if (dark) 0xFFF0923A else 0xFFB85A00
        TechniqueKind.IsoHold -> if (dark) 0xFF6FA8FF else 0xFF1F66C6
    },
)

/** The icon on a technique's fill: white in light, near-black in dark. */
@Suppress("UNUSED_PARAMETER")
fun onTechniqueColor(kind: TechniqueKind, dark: Boolean): Color = if (dark) Color(0xFF141416) else Color(0xFFFFFFFF)
