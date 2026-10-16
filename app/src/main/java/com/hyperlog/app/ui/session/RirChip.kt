package com.hyperlog.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

// The RIR chip (SPEC, RIR chip): set text keeps `@ N` as its one source, and the UI draws each
// `@ N` (or `@ N RIR`) as the number in a small chip of the fixed RIR scale. A target is outlined,
// like the logging screen's ring; a logged set is filled, like its dot.

internal const val RirChipTargetTag = "rir-chip-target"
internal const val RirChipLoggedTag = "rir-chip-logged"

/** One chip's look: its number and whether it is a target. */
internal data class RirChipKey(val rir: Int, val target: Boolean) {
    val id get() = "rir-$rir-${if (target) "target" else "logged"}"
}

private const val Gap = "[ \u00A0]"
private val RirPattern = Regex("(^|$Gap)@$Gap(\\d+)(?:${Gap}RIR\\b)?")
private val TargetWord = Regex("\\btarget$Gap$", RegexOption.IGNORE_CASE)
private val RirChipShape = RoundedCornerShape(4.dp)

/** A chip's fill or outline: the ring's shades, darker in light mode so a chip keeps 3:1 on every surface. */
internal fun rirChipColor(rir: Int, dark: Boolean): Color = rirRingColor(rir, dark)

/** A filled chip's number, on every shade of the scale. */
internal val RirChipOnFill = Color.Black.copy(alpha = 0.87f)

/**
 * Appends [text] with each `@ N` as a chip, a target if [target] or if the word before it is
 * "target" (`set 2 was @ 0, target @ 1`). TalkBack reads each chip as "N RIR". The chips used are
 * added to [chips], for [rirChipContent].
 */
internal fun AnnotatedString.Builder.appendRir(text: String, target: Boolean, chips: MutableSet<RirChipKey>) {
    var at = 0
    for (m in RirPattern.findAll(text)) {
        val before = text.substring(at, m.range.first)
        append(before)
        append(m.groupValues[1])
        val key = RirChipKey(m.groupValues[2].toInt(), target || TargetWord.containsMatchIn(text.substring(0, m.range.first + m.groupValues[1].length)))
        chips += key
        appendInlineContent(key.id, "${key.rir} RIR")
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/** The inline chips for [chips], sized to text of [fontSize]. */
@Composable
internal fun rirChipContent(chips: Set<RirChipKey>, fontSize: TextUnit): Map<String, InlineTextContent> {
    val size = if (fontSize.isSpecified) fontSize else 14.sp
    return chips.associate { key ->
        val width = if (key.rir >= 10) 2.1.em else 1.5.em
        key.id to InlineTextContent(Placeholder(width, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
            RirChip(key.rir, key.target, (size.value * 0.8f).sp)
        }
    }
}

/** The chip itself. */
@Composable
private fun RirChip(rir: Int, target: Boolean, fontSize: TextUnit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = rirChipColor(rir, dark)
    Box(
        Modifier.fillMaxSize()
            .testTag(if (target) RirChipTargetTag else RirChipLoggedTag)
            .then(if (target) Modifier.border(1.5.dp, color, RirChipShape) else Modifier.background(color, RirChipShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rir",
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = if (target) MaterialTheme.colorScheme.onSurface else RirChipOnFill,
            maxLines = 1,
        )
    }
}

/** Set text, with each `@ N` drawn as an RIR chip: outlined for a [target], else filled. */
@Composable
internal fun RirText(
    text: String,
    modifier: Modifier = Modifier,
    target: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val chips = mutableSetOf<RirChipKey>()
    val annotated = buildAnnotatedString { appendRir(text, target, chips) }
    Text(annotated, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, inlineContent = rirChipContent(chips, style.fontSize))
}
