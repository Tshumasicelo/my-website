package com.aspects.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/**
 * The hero backdrop: focusing a card washes the whole screen in that app's own
 * artwork, the way the Netflix browse screen and the Xbox dashboard do.
 *
 * What makes it affordable on a 1 GB television is that the image is rendered
 * deliberately tiny - 64x36 - and then stretched to fill the panel by the GPU.
 * Bilinear upscaling of a thumbnail IS a blur, so the soft wash costs about 9 KB
 * per app instead of the 8 MB a real full-screen bitmap would need. It is the
 * cheapest blur in Android, and on this hardware it is the only one available:
 * RenderScript is gone and RenderEffect needs API 31, four releases newer than
 * this TV.
 */
class Backdrop(context: Context) {

    /** @param tint the app's dominant colour, or 0 when it has no strong hue. */
    data class Art(val bitmap: Bitmap, val tint: Int)

    private val pm = context.applicationContext.packageManager
    private val cache = LruCache<String, Art>(40)
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun shutdown() {
        io.shutdownNow()
    }

    /** The tint if this app's artwork is already cached, or 0 if not yet known. */
    fun cachedTint(entry: AppEntry): Int = cache.get(entry.key)?.tint ?: 0

    fun load(entry: AppEntry, onReady: (Art?) -> Unit) {
        cache.get(entry.key)?.let {
            onReady(it)
            return
        }
        io.execute {
            val art = runCatching { render(entry) }.getOrNull()
            ui.post {
                if (art != null) cache.put(entry.key, art)
                onReady(art)
            }
        }
    }

    private fun render(entry: AppEntry): Art? {
        val source = artwork(entry) ?: return null
        if (source is BitmapDrawable) source.paint.isFilterBitmap = true

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        source.setBounds(0, 0, WIDTH, HEIGHT)
        source.draw(Canvas(bitmap))
        return Art(bitmap, dominantColour(bitmap))
    }

    /** Prefer the 16:9 TV banner; fall back to the icon so every app gets something. */
    private fun artwork(entry: AppEntry): Drawable? {
        val component = ComponentName(entry.packageName, entry.activityName)
        return runCatching { pm.getActivityBanner(component) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(entry.packageName) }.getOrNull()
            ?: runCatching { pm.getActivityIcon(component) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(entry.packageName) }.getOrNull()
    }

    /**
     * Averages only the saturated pixels. Averaging everything would drag every
     * app towards the same muddy grey, because most banners are mostly
     * background; ignoring the washed-out pixels is what makes Netflix come back
     * red and Spotify green.
     */
    private fun dominantColour(bitmap: Bitmap): Int {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)

        var r = 0L
        var g = 0L
        var b = 0L
        var counted = 0L
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            if (Color.alpha(pixel) < 128) continue
            Color.colorToHSV(pixel, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.20f) continue
            r += Color.red(pixel)
            g += Color.green(pixel)
            b += Color.blue(pixel)
            counted++
        }
        if (counted == 0L) return 0
        return Color.rgb((r / counted).toInt(), (g / counted).toInt(), (b / counted).toInt())
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 36
    }
}
