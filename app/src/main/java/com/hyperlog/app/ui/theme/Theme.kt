package com.hyperlog.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperlog.app.BuildConfig

// PROTOTYPE (issue #42): a floating switcher cycles the palettes in PrototypePalettes.kt and forces
// light or dark. Debug builds only.
private var paletteIndex by mutableIntStateOf(1)
private var forcedDark by mutableStateOf<Boolean?>(null)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    val palette = PrototypePalettes[paletteIndex]
    val dark = forcedDark ?: isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = if (dark) palette.dark else palette.light,
        typography = HyperlogTypography,
        motionScheme = MotionScheme.expressive(),
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (BuildConfig.DEBUG) PaletteSwitcher(palette.name, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PaletteSwitcher(name: String, modifier: Modifier) {
    val n = PrototypePalettes.size
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 92.dp)
            .shadow(8.dp, RoundedCornerShape(50))
            .background(Color(0xEE000000), RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        Text("‹", modifier = Modifier.clickable { paletteIndex = (paletteIndex - 1 + n) % n }.then(style), color = Color.White, fontSize = 20.sp)
        Text(name, modifier = Modifier.padding(horizontal = 4.dp), color = Color.White, fontSize = 14.sp)
        Text("›", modifier = Modifier.clickable { paletteIndex = (paletteIndex + 1) % n }.then(style), color = Color.White, fontSize = 20.sp)
        Text(
            when (forcedDark) { null -> "Auto"; true -> "Dark"; false -> "Light" },
            modifier = Modifier.clickable { forcedDark = when (forcedDark) { null -> false; false -> true; true -> null } }.then(style),
            color = Color(0xFFFFD54F),
            fontSize = 13.sp,
        )
    }
}
