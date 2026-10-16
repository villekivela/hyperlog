package com.hyperlog.app.ui.session

import androidx.compose.foundation.background
import com.hyperlog.app.domain.summary
import com.hyperlog.app.domain.LoggedSet
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
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

/** A target chip's outline: the ring's shades, darker in light mode so it keeps 3:1 on every surface. */
internal fun rirChipColor(rir: Int, dark: Boolean): Color = rirRingColor(rir, dark)

/**
 * A logged chip's fill, the same in light and dark: a solid badge. Each is the ring's light-mode
 * shade darkened, keeping its hue, just far enough for [RirBadgeNumber] to keep 4.5:1.
 */
internal fun rirBadgeColor(rir: Int): Color = when {
    rir <= 0 -> Color(0xFFD14237)
    rir <= 2 -> Color(0xFF9D6B1C)
    else -> Color(0xFF447E79)
}

/** A logged chip's number. */
internal val RirBadgeNumber = Color.White

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
        // A no-break space keeps the chip on its number's line.
        if (m.groupValues[1].isNotEmpty()) append('\u00A0')
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
    // A style without a size is drawn at bodyMedium's.
    val size = if (fontSize.isSpecified) fontSize else 14.sp
    return chips.associate { key ->
        // The number is 0.8 em; each semibold digit takes about 0.5 em, with 0.45 em either side.
        val width = (0.9f + 0.5f * "${key.rir}".length).em
        key.id to InlineTextContent(Placeholder(width, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
            RirChip(key.rir, key.target, (size.value * 0.8f).sp)
        }
    }
}

/** The chip itself. */
@Composable
private fun RirChip(rir: Int, target: Boolean, fontSize: TextUnit) {
    val dark = isDarkSurface()
    Box(
        Modifier.fillMaxSize()
            // Drawing only: the line's alternate text already reads "N RIR".
            .clearAndSetSemantics { testTag = if (target) RirChipTargetTag else RirChipLoggedTag }
            .then(if (target) Modifier.border(1.5.dp, rirChipColor(rir, dark), RirChipShape) else Modifier.background(rirBadgeColor(rir), RirChipShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$rir",
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = if (target) MaterialTheme.colorScheme.onSurface else RirBadgeNumber,
            maxLines = 1,
        )
    }
}

/** A logged set as the screen writes it: its RIR after the whole set, and no break inside it. */
internal fun LoggedSet.setText(): String = summary(rirLast = true).replace(' ', '\u00A0')

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
