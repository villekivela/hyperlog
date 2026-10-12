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
    ArrowBack(R.drawable.symbol_arrow_back),
    ArrowDropDown(R.drawable.symbol_arrow_drop_down),
    ArrowForward(R.drawable.symbol_arrow_forward),
    Backspace(R.drawable.symbol_backspace),
    Block(R.drawable.symbol_block),
    Calculate(R.drawable.symbol_calculate),
    Check(R.drawable.symbol_check),
    CheckCircle(R.drawable.symbol_check_circle),
    ChevronRight(R.drawable.symbol_chevron_right),
    Close(R.drawable.symbol_close),
    Delete(R.drawable.symbol_delete),
    Edit(R.drawable.symbol_edit),
    ExpandLess(R.drawable.symbol_expand_less),
    FileOpen(R.drawable.symbol_file_open),
    FitnessCenter(R.drawable.symbol_fitness_center),
    Flag(R.drawable.symbol_flag),
    History(R.drawable.symbol_history),
    Keyboard(R.drawable.symbol_keyboard),
    KeyboardHide(R.drawable.symbol_keyboard_hide),
    List(R.drawable.symbol_list),
    LocalFireDepartment(R.drawable.symbol_local_fire_department),
    LocationOn(R.drawable.symbol_location_on),
    MoreVert(R.drawable.symbol_more_vert),
    PanTool(R.drawable.symbol_pan_tool),
    RadioButtonChecked(R.drawable.symbol_radio_button_checked),
    RadioButtonUnchecked(R.drawable.symbol_radio_button_unchecked),
    Remove(R.drawable.symbol_remove),
    Repeat(R.drawable.symbol_repeat),
    Search(R.drawable.symbol_search),
    Settings(R.drawable.symbol_settings),
    SmartDisplay(R.drawable.symbol_smart_display),
    SouthEast(R.drawable.symbol_south_east),
    StickyNote2(R.drawable.symbol_sticky_note_2),
    Timer(R.drawable.symbol_timer),
    TrendingDown(R.drawable.symbol_trending_down),
    TrendingFlat(R.drawable.symbol_trending_flat),
    TrendingUp(R.drawable.symbol_trending_up),
    UnfoldLess(R.drawable.symbol_unfold_less),
    ViewTimeline(R.drawable.symbol_view_timeline),
    Visibility(R.drawable.symbol_visibility),
    VisibilityOff(R.drawable.symbol_visibility_off),
    Warning(R.drawable.symbol_warning),
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
