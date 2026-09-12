package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * A user-chosen background.
 *
 * The picked image is copied into our own files directory rather than kept as a
 * content URI. That means it survives the USB stick being pulled out, needs no
 * storage permission afterwards, and - most importantly - we get to decide its
 * size. It is stored at 1280x720 in RGB_565: about 1.8 MB in memory, against the
 * 8 MB a full-resolution 1080p photo in ARGB would cost. On a television with a
 * gigabyte of RAM that difference decides whether the launcher survives.
 */
object Wallpaper {

    private const val FILE_NAME = "wallpaper.jpg"
    private const val TARGET_W = 1280
    private const val TARGET_H = 720

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    /** Changes whenever a new wallpaper is written, so callers can skip re-decoding. */
    fun stamp(ctx: Context): Long = file(ctx).let { if (it.exists()) it.lastModified() else 0L }

    fun load(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(f.absolutePath, options) }.getOrNull()
    }

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    /** Decodes downsampled, scales to cover the screen, writes it to our files dir. */
    fun save(ctx: Context, source: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = ctx.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching false

        val scaled = cover(decoded)
        FileOutputStream(file(ctx)).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        true
    }.getOrDefault(false)

    /**
     * Halve until the image is near the target. Decoding a 12 MP phone photo at
     * full size just to shrink it is how launchers get killed mid-pick.
     */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= TARGET_W && h / 2 >= TARGET_H) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun cover(src: Bitmap): Bitmap {
        val scale = maxOf(
            TARGET_W.toFloat() / src.width.toFloat(),
            TARGET_H.toFloat() / src.height.toFloat()
        )
        if (scale >= 1f) return src
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
