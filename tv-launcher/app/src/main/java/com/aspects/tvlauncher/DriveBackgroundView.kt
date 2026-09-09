package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.pow
import kotlin.random.Random

/**
 * Paints the Drive Mode atmosphere straight onto the canvas with gradients and
 * strokes. Because the result is a hardware display list rather than a bitmap,
 * this costs essentially no heap - which is the whole reason we can have a
 * full-screen background on a 1 GB television at all.
 */
class DriveBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode: DriveMode = DriveMode.NIGHT
        set(value) {
            field = value
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var w = 0f
    private var h = 0f
    private var rain: FloatArray? = null

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat()
        h = nh.toFloat()
        buildRain()
    }

    /** Seeded, so the rain pattern is identical on every redraw instead of crawling. */
    private fun buildRain() {
        if (w <= 0f || h <= 0f) return
        val rnd = Random(4219)
        val count = 70
        val pts = FloatArray(count * 4)
        for (i in 0 until count) {
            val x = rnd.nextFloat() * w * 1.2f - w * 0.1f
            val y = rnd.nextFloat() * h
            val len = h * (0.05f + rnd.nextFloat() * 0.07f)
            pts[i * 4] = x
            pts[i * 4 + 1] = y
            pts[i * 4 + 2] = x - len * 0.22f
            pts[i * 4 + 3] = y + len
        }
        rain = pts
    }

    override fun onDraw(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return
        when (mode) {
            DriveMode.NIGHT -> night(canvas)
            DriveMode.WET -> wet(canvas)
            DriveMode.SUNSET -> sunset(canvas)
        }
        fill.shader = null
        stroke.shader = null
    }

    // ------------------------------------------------------------- NIGHT DRIVE

    private fun night(c: Canvas) {
        fill.shader = null
        fill.color = DriveMode.NIGHT.ground
        c.drawRect(0f, 0f, w, h, fill)

        // twin headlight blooms rising from below the bottom edge
        for (cx in floatArrayOf(w * 0.24f, w * 0.76f)) {
            fill.shader = RadialGradient(
                cx, h * 1.06f, w * 0.42f,
                intArrayOf(0x4DFFFFFF.toInt(), 0x12D2E1FF.toInt(), 0x00FFFFFF.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, fill)
        }

        bokeh(c, 0xFF3A34, floatArrayOf(0.82f, 0.16f, 0.030f))
        bokeh(c, 0xFF3A34, floatArrayOf(0.90f, 0.26f, 0.018f))
        bokeh(c, 0xFF3A34, floatArrayOf(0.72f, 0.09f, 0.014f))
        bokeh(c, 0xFF3A34, floatArrayOf(0.95f, 0.12f, 0.011f))

        // a faint gloss band, like light running along a panel
        fill.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(0x00FFFFFF.toInt(), 0x0EFFFFFF.toInt(), 0x00FFFFFF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.60f, w, h * 0.635f, fill)
    }

    // ---------------------------------------------------------------- WET NEON

    private fun wet(c: Canvas) {
        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF040A12.toInt(), 0xFF06101A.toInt(), 0xFF0A1C28.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)

        // taillight red bleeding up off wet asphalt
        fill.shader = RadialGradient(
            w * 0.5f, h * 1.02f, w * 0.46f,
            intArrayOf(0x75FF1F3D.toInt(), 0x1AFF1F3D.toInt(), 0x00FF1F3D.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)

        bokeh(c, 0x39C6E8, floatArrayOf(0.14f, 0.20f, 0.026f))
        bokeh(c, 0x39C6E8, floatArrayOf(0.26f, 0.11f, 0.016f))
        bokeh(c, 0x39C6E8, floatArrayOf(0.86f, 0.18f, 0.022f))
        bokeh(c, 0x39C6E8, floatArrayOf(0.70f, 0.26f, 0.013f))

        stroke.shader = null
        stroke.color = 0x29BEE1F5.toInt()
        stroke.strokeWidth = (w * 0.0012f).coerceAtLeast(1f)
        rain?.let { c.drawLines(it, stroke) }

        fill.shader = LinearGradient(
            0f, h * 0.80f, 0f, h,
            0x00FFFFFF.toInt(), 0x1AA0D7F0.toInt(),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.80f, w, h, fill)
    }

    // -------------------------------------------------------------- SUNSET RUN

    private fun sunset(c: Canvas) {
        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                0xFF231038.toInt(), 0xFF7C2A5C.toInt(), 0xFFE86A93.toInt(),
                0xFFF49AB4.toInt(), 0xFF5E2350.toInt()
            ),
            floatArrayOf(0f, 0.42f, 0.62f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)

        val horizon = h * 0.66f
        val sun = w * 0.155f
        val sunY = horizon - sun * 0.15f

        fill.shader = RadialGradient(
            w * 0.5f, sunY, sun,
            intArrayOf(0xFFFFE6F0.toInt(), 0xFFFFC2DA.toInt(), 0x0DFFB4D2.toInt()),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(w * 0.5f, sunY, sun, fill)

        // wireframe ridgelines
        stroke.shader = null
        stroke.color = 0x6BC9A6FF.toInt()
        stroke.strokeWidth = (w * 0.0013f).coerceAtLeast(1f)
        ridge(c, w * 0.06f, w * 0.30f, h * 0.30f, horizon)
        ridge(c, w * 0.88f, w * 0.30f, h * 0.34f, horizon)

        // the mirror plane
        fill.shader = LinearGradient(
            0f, horizon, 0f, h,
            0x8C3C143C.toInt(), 0xE014061C.toInt(),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, w, h, fill)

        // receding grid
        stroke.color = 0x4DFF96C8.toInt()
        stroke.strokeWidth = (w * 0.0011f).coerceAtLeast(1f)
        for (i in -9..9) {
            c.drawLine(
                w * 0.5f + i * (w * 0.030f), horizon,
                w * 0.5f + i * (w * 0.30f), h,
                stroke
            )
        }
        for (i in 1..8) {
            val t = (i / 8f).toDouble().pow(2.1).toFloat()
            val y = horizon + (h - horizon) * t
            c.drawLine(0f, y, w, y, stroke)
        }

        // the sun smeared down the reflection
        fill.shader = LinearGradient(
            0f, horizon, 0f, h,
            0x4DFFC8E1.toInt(), 0x00FFC8E1.toInt(),
            Shader.TileMode.CLAMP
        )
        c.drawRect(w * 0.40f, horizon, w * 0.60f, h, fill)
    }

    // ------------------------------------------------------------------ shared

    /** @param rgb a plain 0xRRGGBB value; alpha is applied here. */
    private fun bokeh(c: Canvas, rgb: Int, spec: FloatArray) {
        val radius = w * spec[2]
        if (radius <= 0f) return
        fill.shader = RadialGradient(
            w * spec[0], h * spec[1], radius,
            (0x8C000000.toInt() or rgb), (rgb and 0x00FFFFFF),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(w * spec[0], h * spec[1], radius, fill)
    }

    private fun ridge(c: Canvas, cx: Float, halfWidth: Float, height: Float, baseY: Float) {
        for (k in 0 until 5) {
            val spread = 1f - k * 0.16f
            val rise = 1f - k * 0.17f
            val peakX = cx + halfWidth * 0.42f
            val peakY = baseY - height * rise
            c.drawLine(cx - halfWidth * spread, baseY, peakX, peakY, stroke)
            c.drawLine(peakX, peakY, cx + halfWidth * spread, baseY, stroke)
        }
    }
}
