package com.hyperlog.app.ui.mannequin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.mannequin.Mannequins
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.TextEdge
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures

/**
 * Every mannequin clip, one page each, for checking a batch on the phone (debug builds, Settings →
 * Debug). Swipe below the stage for the next clip; on the stage a drag turns the mannequin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MannequinGallery(mannequins: Mannequins?, exercises: List<Exercise>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val clips = mannequins?.clips.orEmpty()
    val names = exercises.associate { it.id to it.name }
    Scaffold(
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.mannequin_gallery)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val pager = rememberPagerState { clips.size }
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding)) { page ->
            val clip = clips[page]
            // Each page is laid out as the top of the exercise's sheet: the stage a card at the
            // cards' margin, the name under it on the text edge.
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                MannequinStage(clip, Modifier.padding(horizontal = CardMargin))
                Spacer(Modifier.height(16.dp))
                Text(
                    interFigures(names[clip.exerciseId] ?: clip.exerciseId),
                    Modifier.padding(horizontal = TextEdge),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mannequin_gallery_page, page + 1, clips.size),
                    Modifier.padding(horizontal = TextEdge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
