package com.hyperlog.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The largest size from [max] down to [floor], in half-sp steps, at which [fits] holds; [floor]
 * when none does, so a caller past it wraps or gives way rather than shrink out of sight.
 */
fun fitSize(max: TextUnit, floor: TextUnit, fits: (TextUnit) -> Boolean): TextUnit =
    generateSequence(max.value) { it - 0.5f }.takeWhile { it > floor.value }.map { it.sp }.firstOrNull(fits) ?: floor

/**
 * One size for a group of [texts], each with the width it has: the largest at which all of them
 * fit on a line. Labels that sit together step down together, where each fitted alone would differ.
 */
@Composable
fun sharedFontSize(texts: List<Pair<String, Dp>>, style: TextStyle, floor: TextUnit): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(texts, style, density, floor) {
        fitSize(style.fontSize, floor) { size ->
            texts.all { (text, width) ->
                val measured = measurer.measure(text, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width
                measured <= with(density) { width.toPx() }
            }
        }
    }
}
