package com.hyperlog.app.ui.theme

import android.content.Context
import android.provider.Settings

/** Whether the system's animations are off (Settings → Accessibility → Remove animations). */
fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
