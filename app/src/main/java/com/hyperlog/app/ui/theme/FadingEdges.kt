package com.hyperlog.app.ui.theme

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Fades the list out at an edge that has more to scroll to, so it reads as scrollable. */
fun Modifier.fadingEdges(state: LazyListState): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 32.dp.toPx()
        if (state.canScrollBackward) {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade), size = size.copy(height = fade), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade), size = size.copy(height = fade), blendMode = BlendMode.DstIn,
            )
        }
    }
