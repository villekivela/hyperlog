package com.hyperlog.app.ui.session

import androidx.compose.foundation.background
import com.hyperlog.app.domain.summary
import com.hyperlog.app.domain.LoggedSet
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.hyperlog.app.progression.Record

// The RIR chip (SPEC, RIR chip): set text keeps `@ N` as its one source, and the UI draws each
// `@ N` (or `@ N RIR`) as the number, in its colour, in a small chip of the fixed RIR scale. A
// target is outlined; a logged set is a badge, deep with a pale number in dark mode and a tint with
// a thin edge in light. The logging screen draws the same chip on its own ([RirChip]).

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


/**
 * A target chip's outline colour. In light mode each is a darker shade of its hue, so the outline
 * keeps 3:1 against every light surface in daylight; dark mode keeps [rirColor].
 */
internal fun rirChipColor(rir: Int, dark: Boolean): Color = when {
    dark -> rirColor(rir)
    rir <= 0 -> Color(0xFFD5544A)
    rir <= 2 -> Color(0xFFAA741E)
    else -> Color(0xFF4A8983)
}

/**
 * A chip's number, target and logged alike: its hue, darker in light mode and pale in dark (owner,
 * 2026-11-02, #118), so it keeps 4.5:1 on its badge and on every neutral surface.
 */
internal fun rirNumberColor(rir: Int, dark: Boolean): Color = when {
    dark -> when {
        rir <= 0 -> Color(0xFFF2C8C5)
        rir <= 2 -> Color(0xFFF4E1C2)
        else -> Color(0xFFD1E6E4)
    }
    rir <= 0 -> Color(0xFFA52F26)
    rir <= 2 -> Color(0xFF7D5516)
    else -> Color(0xFF366460)
}

/**
 * A logged chip's fill. Dark: a deep shade of [rirColor]'s hue, which stands off the card by its
 * colour, with no edge. Light: a soft tint, opaque so it reads the same on any surface, with
 * [rirBadgeEdge] to keep it a chip on its white card.
 */
internal fun rirBadgeColor(rir: Int, dark: Boolean): Color = when {
    dark -> when {
        rir <= 0 -> Color(0xFF952A22)
        rir <= 2 -> Color(0xFF825917)
        else -> Color(0xFF36635F)
    }
    rir <= 0 -> Color(0xFFFAE9E8)
    rir <= 2 -> Color(0xFFFBF3E6)
    else -> Color(0xFFEAF4F3)
}

/** A logged chip's 1 dp edge in light mode: the outline's colour at 55% over the fill. A dark badge has none. */
internal fun rirBadgeEdge(rir: Int, dark: Boolean): Color? = when {
    dark -> null
    rir <= 0 -> Color(0xFFE69791)
    rir <= 2 -> Color(0xFFCEAD78)
    else -> Color(0xFF92B9B5)
}

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
        val label = "${key.rir}"
        key.id to InlineTextContent(Placeholder(chipWidth(label).em, ChipHeight.em, PlaceholderVerticalAlign.TextCenter)) {
            // Drawing only: the line's alternate text already reads "N RIR".
            ChipBox(key.rir, key.target, (size.value * NumberSize).sp, label, Modifier.fillMaxSize().clearAndSetSemantics { testTag = chipTag(key.target) })
        }
    }
}

// The number is 0.8 em; each semibold digit takes about 0.5 em, with 0.45 em either side.
private const val NumberSize = 0.8f
private const val ChipHeight = 1.25f
private fun chipWidth(label: String) = 0.9f + 0.5f * label.length
private fun chipTag(target: Boolean) = if (target) RirChipTargetTag else RirChipLoggedTag

/** The chip's drawing, filling [modifier]'s size, with [label] at [fontSize]. */
@Composable
private fun ChipBox(rir: Int, target: Boolean, fontSize: TextUnit, label: String, modifier: Modifier, spoken: String? = null) {
    val dark = isDarkSurface()
    Box(
        modifier.then(
            if (target) Modifier.border(1.5.dp, rirChipColor(rir, dark), RirChipShape)
            else Modifier.background(rirBadgeColor(rir, dark), RirChipShape).then(
                rirBadgeEdge(rir, dark)?.let { Modifier.border(1.dp, it, RirChipShape) } ?: Modifier,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = rirNumberColor(rir, dark),
            maxLines = 1,
        )
    }
}

/**
 * An RIR chip on its own, outside set text: the logging screen's RIR column, its pad and the
 * exercise sheet. Its number is [fontSize]; 4 and up read `4+`, as on the pad. [spoken] replaces
 * the bare number for a screen reader.
 */
@Composable
internal fun RirChip(rir: Int, target: Boolean, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp, spoken: String? = null) {
    val label = if (rir >= 4) "4+" else "$rir"
    val em = with(LocalDensity.current) { fontSize.toDp() } / NumberSize
    ChipBox(rir, target, fontSize, label, modifier.size(em * chipWidth(label), em * ChipHeight).testTag(chipTag(target)), spoken)
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
    textAlign: TextAlign? = null,
) {
    val chips = mutableSetOf<RirChipKey>()
    val labels = Record.entries.associateWith { it.label() }
    val annotated = buildAnnotatedString {
        // A record's mark (RecordMark.kt) is drawn inline and read as its name.
        var at = 0
        text.forEachIndexed { i, c ->
            val record = c.record() ?: return@forEachIndexed
            appendRir(text.substring(at, i), target, chips)
            appendInlineContent(record.inlineId(), labels.getValue(record))
            at = i + 1
        }
        appendRir(text.substring(at), target, chips)
    }
    Text(annotated, modifier, color = color, style = style, textAlign = textAlign, maxLines = maxLines, overflow = overflow, inlineContent = rirChipContent(chips, style.fontSize) + recordMarkContent(
        // The mark is ink of the text it sits in, never the accent.
        color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
    ))
}
