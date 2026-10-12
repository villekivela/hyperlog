package com.hyperlog.app.ui.symbols

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.hyperlog.app.R

/**
 * The Material Symbols (Rounded) the app ships, one vector drawable each. Add new ones with
 * `scripts/add-symbol.sh <name>`, then list them here.
 */
enum class Symbol(@param:DrawableRes val drawable: Int) {
    Add(R.drawable.symbol_add),
    ArrowForward(R.drawable.symbol_arrow_forward),
    Backspace(R.drawable.symbol_backspace),
    Check(R.drawable.symbol_check),
    CheckCircle(R.drawable.symbol_check_circle),
    ExpandLess(R.drawable.symbol_expand_less),
    FitnessCenter(R.drawable.symbol_fitness_center),
    Keyboard(R.drawable.symbol_keyboard),
    KeyboardHide(R.drawable.symbol_keyboard_hide),
    MoreVert(R.drawable.symbol_more_vert),
    RadioButtonChecked(R.drawable.symbol_radio_button_checked),
    RadioButtonUnchecked(R.drawable.symbol_radio_button_unchecked),
    Remove(R.drawable.symbol_remove),
    Settings(R.drawable.symbol_settings),
    Timer(R.drawable.symbol_timer),
}

@Composable
fun SymbolIcon(
    symbol: Symbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(symbol.drawable), contentDescription, modifier, tint)
}
