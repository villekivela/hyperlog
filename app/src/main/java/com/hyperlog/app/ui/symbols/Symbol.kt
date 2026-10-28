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
 * A `Fill` entry is the filled style (`_fill1` in Material Symbols), for a selected navigation item.
 */
enum class Symbol(@param:DrawableRes val drawable: Int) {
    Add(R.drawable.symbol_add),
    AirlineSeatFlat(R.drawable.symbol_airline_seat_flat),
    Air(R.drawable.symbol_air),
    AttachFile(R.drawable.symbol_attach_file),
    Album(R.drawable.symbol_album),
    ArrowBack(R.drawable.symbol_arrow_back),
    ArrowDownward(R.drawable.symbol_arrow_downward),
    ArrowDropDown(R.drawable.symbol_arrow_drop_down),
    ArrowForward(R.drawable.symbol_arrow_forward),
    ArrowUpward(R.drawable.symbol_arrow_upward),
    Backspace(R.drawable.symbol_backspace),
    Block(R.drawable.symbol_block),
    Bookmark(R.drawable.symbol_bookmark),
    Cable(R.drawable.symbol_cable),
    Calculate(R.drawable.symbol_calculate),
    CalendarToday(R.drawable.symbol_calendar_today),
    Check(R.drawable.symbol_check),
    CheckCircle(R.drawable.symbol_check_circle),
    ChevronLeft(R.drawable.symbol_chevron_left),
    ChevronRight(R.drawable.symbol_chevron_right),
    Close(R.drawable.symbol_close),
    ContentCopy(R.drawable.symbol_content_copy),
    ContentPaste(R.drawable.symbol_content_paste),
    Delete(R.drawable.symbol_delete),
    Download(R.drawable.symbol_download),
    Edit(R.drawable.symbol_edit),
    ExpandLess(R.drawable.symbol_expand_less),
    ExpandMore(R.drawable.symbol_expand_more),
    Exercise(R.drawable.symbol_exercise),
    FileOpen(R.drawable.symbol_file_open),
    FitnessCenter(R.drawable.symbol_fitness_center),
    Flag(R.drawable.symbol_flag),
    History(R.drawable.symbol_history),
    Insights(R.drawable.symbol_insights),
    Keep(R.drawable.symbol_keep),
    Key(R.drawable.symbol_key),
    Keyboard(R.drawable.symbol_keyboard),
    KeyboardHide(R.drawable.symbol_keyboard_hide),
    LibraryAdd(R.drawable.symbol_library_add),
    List(R.drawable.symbol_list),
    LocalFireDepartment(R.drawable.symbol_local_fire_department),
    LocationOn(R.drawable.symbol_location_on),
    MoreTime(R.drawable.symbol_more_time),
    MoreVert(R.drawable.symbol_more_vert),
    PanTool(R.drawable.symbol_pan_tool),
    PlayArrow(R.drawable.symbol_play_arrow),
    RadioButtonChecked(R.drawable.symbol_radio_button_checked),
    RadioButtonUnchecked(R.drawable.symbol_radio_button_unchecked),
    PushPin(R.drawable.symbol_push_pin),
    Remove(R.drawable.symbol_remove),
    Repeat(R.drawable.symbol_repeat),
    Replay(R.drawable.symbol_replay),
    RestorePage(R.drawable.symbol_restore_page),
    Search(R.drawable.symbol_search),
    Settings(R.drawable.symbol_settings),
    SettingsFill(R.drawable.symbol_settings_fill),
    SkipNext(R.drawable.symbol_skip_next),
    SmartDisplay(R.drawable.symbol_smart_display),
    SouthEast(R.drawable.symbol_south_east),
    Spa(R.drawable.symbol_spa),
    Sports(R.drawable.symbol_sports),
    SportsFill(R.drawable.symbol_sports_fill),
    Stairs(R.drawable.symbol_stairs),
    Stacks(R.drawable.symbol_stacks),
    StickyNote2(R.drawable.symbol_sticky_note_2),
    Stop(R.drawable.symbol_stop),
    SubdirectoryArrowRight(R.drawable.symbol_subdirectory_arrow_right),
    SwapHoriz(R.drawable.symbol_swap_horiz),
    SystemUpdate(R.drawable.symbol_system_update),
    Timer(R.drawable.symbol_timer),
    Today(R.drawable.symbol_today),
    TodayFill(R.drawable.symbol_today_fill),
    TrendingDown(R.drawable.symbol_trending_down),
    TrendingFlat(R.drawable.symbol_trending_flat),
    TrendingUp(R.drawable.symbol_trending_up),
    Toll(R.drawable.symbol_toll),
    Trophy(R.drawable.symbol_trophy),
    Tune(R.drawable.symbol_tune),
    Undo(R.drawable.symbol_undo),
    UnfoldLess(R.drawable.symbol_unfold_less),
    ViewTimeline(R.drawable.symbol_view_timeline),
    ViewTimelineFill(R.drawable.symbol_view_timeline_fill),
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
