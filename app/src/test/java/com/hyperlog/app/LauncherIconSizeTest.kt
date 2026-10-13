package com.hyperlog.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher glyph sits inside the adaptive-icon keyline, as large as other apps' (about 44–48 dp
 * of the 108 dp canvas), in both the coloured and the themed layer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconSizeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The drawn extent of drawable [name] on a 108 px canvas (1 px per dp): width and height. */
    private fun extent(name: String): Pair<Int, Int> {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        val drawable = context.getDrawable(id)!!
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 108, 108)
        drawable.draw(Canvas(bitmap))
        var minX = 108; var maxX = -1; var minY = 108; var maxY = -1
        for (y in 0 until 108) for (x in 0 until 108) {
            if ((bitmap.getPixel(x, y) ushr 24) > 0x40) {
                minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
        }
        return (maxX - minX + 1) to (maxY - minY + 1)
    }

    @Test
    fun theGlyphIsAboutFortySixDpAtItsWidest() {
        listOf("ic_launcher_foreground", "ic_launcher_monochrome").forEach { name ->
            val (w, h) = extent(name)
            val widest = maxOf(w, h)
            assertTrue("$name spans $w × $h dp", widest in 42..50)
        }
    }
}
