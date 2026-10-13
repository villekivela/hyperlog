package com.hyperlog.app.ui.program

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.hyperlog.app.R
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.planChunks
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/** The symbol a technique is shown with (docs/design/logging-screen.md, Technique label). */
fun Technique.symbol(): Symbol = when (this) {
    is Technique.Dropset -> Symbol.TrendingDown
    is Technique.MyoReps -> Symbol.Repeat
    is Technique.LengthenedPartials -> Symbol.UnfoldLess
    is Technique.IsoHold -> Symbol.PanTool
    Technique.Failure -> Symbol.LocalFireDepartment
}

/** `Drop set, 2 drops of 25%`, `Myo-reps, mini-sets of 4`, `To failure`… */
@Composable
fun techniqueText(technique: Technique): String = when (technique) {
    is Technique.Dropset -> if (technique.drops == 1) stringResource(R.string.technique_dropset_one, technique.percent)
    else stringResource(R.string.technique_dropset, technique.drops, technique.percent)
    is Technique.MyoReps -> stringResource(R.string.technique_myo, technique.miniSetReps)
    is Technique.LengthenedPartials -> technique.partials?.let { stringResource(R.string.technique_partials_count, it) }
        ?: stringResource(R.string.technique_partials_failure)
    is Technique.IsoHold -> when (technique.position) {
        com.hyperlog.app.program.HoldPosition.Stretch -> stringResource(R.string.technique_iso_stretch, technique.seconds)
        com.hyperlog.app.program.HoldPosition.Contraction -> stringResource(R.string.technique_iso_contraction, technique.seconds)
    }
    Technique.Failure -> stringResource(R.string.technique_failure)
}

/**
 * Sets summed up in one line: `1 × 4–6 @ 1 · ↘ 2 × 6–8 @ 1`, a back-off run marked with
 * `south_east` and each technique's symbol after its run, in tertiary.
 */
@Composable
fun SetSummary(
    sets: List<SetPrescription>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val chunks = planChunks(sets)
    val symbols = mutableMapOf<String, Symbol>()
    val parts = chunks.map { c ->
        if (c.rir != null) stringResource(R.string.plan_sets_rir, c.count, c.reps.toString(), c.rir)
        else stringResource(R.string.plan_sets, c.count, c.reps.toString())
    }
    val text = buildAnnotatedString {
        chunks.forEachIndexed { i, c ->
            if (i > 0) append("  ·  ")
            if (c.backoff) {
                symbols["backoff"] = Symbol.SouthEast
                appendInlineContent("backoff", "back-off")
                append(" ")
            }
            append(parts[i])
            c.techniques.distinct().forEach { t ->
                val key = t.symbol().name
                symbols[key] = t.symbol()
                append(" ")
                appendInlineContent(key, t.symbol().name)
            }
        }
    }
    val tertiary = MaterialTheme.colorScheme.tertiary
    val inline = symbols.mapValues { (key, symbol) ->
        InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.Center)) {
            SymbolIcon(symbol, null, Modifier.fillMaxSize(), tint = if (key == "backoff") color else tertiary)
        }
    }
    Text(text, modifier, style = style, color = color, inlineContent = inline, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
