package com.hyperlog.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/**
 * The starter program, offered while no program runs: what it is, **Run this program**, and the
 * screen's other ways forward as text buttons under it (docs/design/program-and-today.md).
 */
@Composable
fun StarterOffer(starter: Program, onRun: () -> Unit, modifier: Modifier = Modifier, others: @Composable RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SymbolIcon(Symbol.ViewTimeline, null, Modifier.size(48.dp), tint = cs.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.starter_label), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(starter.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        starter.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRun) { Text(stringResource(R.string.starter_run)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = others)
        // Optical centre: a little above the middle, as the other empty states.
        Spacer(Modifier.height(96.dp))
    }
}
