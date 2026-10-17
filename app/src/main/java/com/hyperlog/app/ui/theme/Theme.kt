package com.hyperlog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hyperlog.app.BuildConfig

// PROTOTYPE: the look comes from PrototypeLook, cycled by its floating switcher (debug builds only).
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    val look = PrototypeLook.look
    val dark = PrototypeLook.forcedDark ?: isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = if (dark) look.dark else look.light,
        typography = if (look.inter) InterTypography else HyperlogTypography,
        motionScheme = MotionScheme.expressive(),
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (BuildConfig.DEBUG) LookSwitcher(Modifier.align(Alignment.TopCenter))
        }
    }
}
