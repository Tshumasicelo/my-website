package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paints the Drive Mode atmosphere straight onto the canvas with gradients and
 * strokes. Because the result is a hardware display list rather than a bitmap,
 * this costs essentially no heap - which is the whole reason we can have a
 * full-screen background on a 1 GB television at all.
 *
 * Every scene is a function of time [t]. Still, t stays at zero and the view is
 * painted once; its display list is then replayed by the GPU for free. Living,
 * it repaints every [FRAME_MS] - so nothing here may allocate per frame:
 * shaders are built once per mode and size, and anything that moves does so
 * through a reused matrix or a canvas translation.
 */
class DriveBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode: DriveMode = DriveMode.NIGHT
        set(value) {
            if (field == value) return
            field = value
            forget()
            invalidate()
        }

    /** Motion on or off. Off is the default and costs nothing after one paint. */
    var living: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            epoch = SystemClock.uptimeMillis()
            removeCallbacks(frame)
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val shift = Matrix()

    /** One slot per gradient a scene uses. Only one scene draws at a time. */
    private val shaders = arrayOfNulls<Shader>(SLOTS)

    private var w = 0f
    private var h = 0f
    private var t = 0f
    private var epoch = 0L
    private val frame = Runnable { invalidate() }

    // Scenery that is random but must not crawl: seeded, and built once per size.
    private var rain: FloatArray? = null
    private var starsA: FloatArray? = null
    private var starsB: FloatArray? = null
    private var carbon: Bitmap? = null
    private val farRange = Path()
    private val nearRange = Path()
    private val stripeWide = Path()
    private val stripeThin = Path()
    private val kerb = Path()
    private val skyline = Path()
    private val windowsAmber = Path()
    private val windowsCyan = Path()
    private val windowsPink = Path()
    private val signs = FloatArray(SIGNS * 4)
    private val trails = FloatArray(TRAILS * 6)
    private val trailPaths = Array(TRAILS) { Path() }

    // Scratch space for the aurora, reused every frame.
    private val ribbonTop = FloatArray(RIBBON_STEPS + 1)
    private val ribbonBottom = FloatArray(RIBBON_STEPS + 1)

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat()
        h = nh.toFloat()
        forget()
        if (w <= 0f || h <= 0f) return
        buildRain()
        buildStars()
        buildRanges()
        buildCarbon()
        buildTrack()
        buildCity()
        buildTrails()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Behind another app nobody can see us, so stop asking for frames.
        if (visibility == VISIBLE && living) invalidate() else removeCallbacks(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return
        // Wrapped hourly so float precision never degrades into visible jitter.
        t = if (living) ((SystemClock.uptimeMillis() - epoch) % 3_600_000L) / 1000f else 0f

        when (mode) {
            DriveMode.NIGHT -> night(canvas)
            DriveMode.WET -> wet(canvas)
            DriveMode.SUNSET -> sunset(canvas)
            DriveMode.AURORA -> aurora(canvas)
            DriveMode.TRACK -> track(canvas)
            DriveMode.KAIDO -> kaido(canvas)
            DriveMode.GOLDEN -> golden(canvas)
        }
        fill.shader = null
        stroke.shader = null
        fill.alpha = 255
        stroke.alpha = 255

        if (living && windowVisibility == VISIBLE) {
            // One pending frame at most: any other invalidate() would otherwise
            // start a second chain and quietly double the frame rate.
            removeCallbacks(frame)
            postDelayed(frame, FRAME_MS)
        }
    }

    private inline fun cached(slot: Int, make: () -> Shader): Shader {
        val have = shaders[slot]
        if (have != null) return have
        val made = make()
        shaders[slot] = made
        return made
    }

    private fun forget() {
        shaders.fill(null)
    }

    /** Wobbles around 1, and is exactly 1 at rest, so the still frame is unchanged. */
    private fun pulse(speed: Float, depth: Float): Float = 1f - depth + depth * cos(t * speed)

    // ------------------------------------------------------------- NIGHT DRIVE

    private fun night(c: Canvas) {
        fill.shader = null
        fill.color = DriveMode.NIGHT.ground
        c.drawRect(0f, 0f, w, h, fill)

        // twin headlight blooms rising from below the bottom edge; alive, they
        // breathe like an engine at idle
        for (i in 0..1) {
            val cx = if (i == 0) w * 0.24f else w * 0.76f
            fill.shader = cached(i) {
                RadialGradient(
                    cx, h * 1.06f, w * 0.42f,
                    ints(0x4DFFFFFF, 0x12D2E1FF, 0x00FFFFFF),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            fill.alpha = alpha(pulse(0.9f, 0.14f))
            c.drawRect(0f, 0f, w, h, fill)
        }
        fill.alpha = 255

        bokeh(c, 2, 0xFF3A34, 0.82f, 0.16f, 0.030f, pulse(1.10f, 0.25f))
        bokeh(c, 3, 0xFF3A34, 0.90f, 0.26f, 0.018f, pulse(1.47f, 0.25f))
        bokeh(c, 4, 0xFF3A34, 0.72f, 0.09f, 0.014f, pulse(0.83f, 0.25f))
        bokeh(c, 5, 0xFF3A34, 0.95f, 0.12f, 0.011f, pulse(1.91f, 0.25f))

        // a faint gloss band, like light running along a panel
        val band = cached(6) {
            LinearGradient(
                0f, 0f, w, 0f,
                ints(0x00FFFFFF, 0x0EFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        shift.setTranslate(w * 0.55f * sin(t * 0.12f), 0f)
        band.setLocalMatrix(shift)
        fill.shader = band
        c.drawRect(0f, h * 0.60f, w, h * 0.635f, fill)
    }

    // ---------------------------------------------------------------- WET NEON

    private fun wet(c: Canvas) {
        fill.shader = cached(0) {
            LinearGradient(
                0f, 0f, 0f, h,
                ints(0xFF040A12, 0xFF06101A, 0xFF0A1C28),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, h, fill)

        // taillight red bleeding up off wet asphalt
        fill.shader = cached(1) {
            RadialGradient(
                w * 0.5f, h * 1.02f, w * 0.46f,
                ints(0x75FF1F3D, 0x1AFF1F3D, 0x00FF1F3D),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        fill.alpha = alpha(pulse(0.7f, 0.12f))
        c.drawRect(0f, 0f, w, h, fill)
        fill.alpha = 255

        bokeh(c, 2, 0x39C6E8, 0.14f, 0.20f, 0.026f, pulse(0.95f, 0.3f))
        bokeh(c, 3, 0x39C6E8, 0.26f, 0.11f, 0.016f, pulse(1.33f, 0.3f))
        bokeh(c, 4, 0x39C6E8, 0.86f, 0.18f, 0.022f, pulse(0.71f, 0.3f))
        bokeh(c, 5, 0x39C6E8, 0.70f, 0.26f, 0.013f, pulse(1.62f, 0.3f))

        // The rain is one fixed pattern drawn twice, a screen apart, and slid
        // down along its own slant. When the first copy has fallen a full
        // screen it is exactly where the second began, so the loop never seams.
        stroke.shader = null
        stroke.color = 0x29BEE1F5
        stroke.strokeWidth = max(1f, w * 0.0012f)
        rain?.let { pts ->
            val fall = (t * h * RAIN_SPEED) % h
            c.save()
            c.translate(-fall * RAIN_SLANT, fall)
            c.drawLines(pts, stroke)
            c.translate(h * RAIN_SLANT, -h)
            c.drawLines(pts, stroke)
            c.restore()
        }

        fill.shader = cached(6) {
            LinearGradient(
                0f, h * 0.80f, 0f, h,
                0x00FFFFFF, 0x1AA0D7F0,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, h * 0.80f, w, h, fill)
    }

    // -------------------------------------------------------------- SUNSET RUN

    private fun sunset(c: Canvas) {
        fill.shader = cached(0) {
            LinearGradient(
                0f, 0f, 0f, h,
                ints(0xFF231038, 0xFF7C2A5C, 0xFFE86A93, 0xFFF49AB4, 0xFF5E2350),
                floatArrayOf(0f, 0.42f, 0.62f, 0.66f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, h, fill)

        val horizon = h * 0.66f
        val sun = w * 0.155f
        val sunY = horizon - sun * 0.15f

        fill.shader = cached(1) {
            RadialGradient(
                w * 0.5f, sunY, sun,
                ints(0xFFFFE6F0, 0xFFFFC2DA, 0x0DFFB4D2),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(w * 0.5f, sunY, sun, fill)

        // wireframe ridgelines
        stroke.shader = null
        stroke.color = 0x6BC9A6FF
        stroke.strokeWidth = max(1f, w * 0.0013f)
        ridge(c, w * 0.06f, w * 0.30f, h * 0.30f, horizon)
        ridge(c, w * 0.88f, w * 0.30f, h * 0.34f, horizon)

        // the mirror plane
        fill.shader = cached(2) {
            LinearGradient(
                0f, horizon, 0f, h,
                0x8C3C143C.toInt(), 0xE014061C.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, horizon, w, h, fill)

        // receding grid; alive, the cross lines rush towards you
        stroke.color = 0x4DFF96C8
        stroke.strokeWidth = max(1f, w * 0.0011f)
        for (i in -9..9) {
            c.drawLine(
                w * 0.5f + i * (w * 0.030f), horizon,
                w * 0.5f + i * (w * 0.30f), h,
                stroke
            )
        }
        val phase = (t * 0.35f) % 1f
        for (i in 0..8) {
            val u = (i + phase) / 8f
            if (u <= 0.001f || u > 1f) continue
            val y = horizon + (h - horizon) * u.pow(2.1f)
            c.drawLine(0f, y, w, y, stroke)
        }

        // the sun smeared down the reflection
        fill.shader = cached(3) {
            LinearGradient(
                0f, horizon, 0f, h,
                0x4DFFC8E1, 0x00FFC8E1,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(w * 0.40f, horizon, w * 0.60f, h, fill)
    }

    // ---------------------------------------------------------- AURORA HIGHWAY

    private fun aurora(c: Canvas) {
        val horizon = h * AURORA_HORIZON

        fill.shader = cached(0) {
            LinearGradient(
                0f, 0f, 0f, horizon,
                ints(0xFF01060D, 0xFF03111C, 0xFF072530, 0xFF0C3A3A),
                floatArrayOf(0f, 0.4f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, horizon, fill)

        // two star fields that twinkle out of step with each other
        stroke.shader = null
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = max(1.5f, w * 0.0014f)
        stroke.color = 0xB3FFFFFF.toInt()
        stroke.alpha = alpha(0.70f * pulse(0.80f, 0.45f))
        starsA?.let { c.drawPoints(it, stroke) }
        stroke.color = 0xB3FFFFFF.toInt()
        stroke.alpha = alpha(0.70f * pulse(0.63f, 0.45f))
        starsB?.let { c.drawPoints(it, stroke) }
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.alpha = 255

        // the curtains, faintest and highest first
        ribbon(c, 1, h * 0.08f, h * 0.030f, h * 0.12f, 3.1f, 0.17f, 4.0f, 0.55f)
        ribbon(c, 2, h * 0.15f, h * 0.050f, h * 0.22f, 1.6f, 0.22f, 0.0f, 1f)
        ribbon(c, 3, h * 0.30f, h * 0.040f, h * 0.16f, 2.3f, 0.31f, 2.1f, 0.8f)

        fill.shader = null
        fill.color = 0xFF07192A.toInt()
        c.drawPath(farRange, fill)
        fill.color = 0xFF020A12.toInt()
        c.drawPath(nearRange, fill)

        road(
            c, 4, horizon, w * 0.5f, w * 0.30f,
            0xFF071824.toInt(), 0xFF02080E.toInt(),
            0x5533FFC8, 0xCC9CFFE0.toInt()
        )
    }

    /**
     * One aurora curtain, built from vertical rays of light hanging off a hem.
     *
     * A single gradient over a wavy band cannot follow the wave: wherever the
     * band dips, its top edge cuts through the bright part and reads as a hard
     * line. So each ray carries its own copy of the gradient instead - one
     * shader, slid and stretched onto that ray by the canvas transform. The hem
     * is two sine waves beating against each other, the ray lengths breathe
     * along the curtain, and alive, both travel.
     */
    private fun ribbon(
        c: Canvas,
        slot: Int,
        base: Float,
        amp: Float,
        thick: Float,
        freq: Float,
        speed: Float,
        seed: Float,
        strength: Float
    ) {
        val phase = seed + t * speed
        val spacing = 1.1f * w / RIBBON_STEPS
        path.rewind()
        for (i in 0..RIBBON_STEPS) {
            val u = i / RIBBON_STEPS.toFloat()
            val a = u * freq * TAU + phase
            val hem = base + thick + amp * sin(a) + amp * 0.45f * sin(a * 1.7f - phase * 1.3f)
            val depth = 0.35f + 0.65f * (0.5f + 0.5f * sin(u * TAU * 1.3f + phase * 0.7f))
            ribbonTop[i] = hem - thick * depth
            ribbonBottom[i] = hem
            val x = -0.05f * w + u * 1.1f * w
            if (i == 0) path.moveTo(x, hem) else path.lineTo(x, hem)
        }

        // brightest green along the lower hem, fading up through violet to nothing
        stroke.shader = cached(slot) {
            LinearGradient(
                0f, 0f, 0f, -RAY_UNIT,
                ints(0xB35CFFC8, 0x6633FFB0, 0x2EB46CFF, 0x00B46CFF),
                floatArrayOf(0f, 0.18f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        stroke.strokeWidth = spacing * 1.02f
        for (i in 0..RIBBON_STEPS) {
            val x = -0.05f * w + (i / RIBBON_STEPS.toFloat()) * 1.1f * w
            val length = ribbonBottom[i] - ribbonTop[i]
            // a fixed shimmer between neighbouring rays is what makes it a curtain
            stroke.alpha = alpha(strength * (0.72f + 0.28f * RAY_SHIMMER[i % RAY_SHIMMER.size]))
            c.save()
            c.translate(x, ribbonBottom[i])
            c.scale(1f, length / RAY_UNIT)
            c.drawLine(0f, 0f, 0f, -RAY_UNIT, stroke)
            c.restore()
        }
        stroke.shader = null

        // a fine bright line along the hem smooths the ray steps away
        stroke.color = 0x6688FFD8
        stroke.alpha = alpha(0.4f * strength)
        stroke.strokeWidth = max(1f, h * 0.0025f)
        c.drawPath(path, stroke)
        stroke.alpha = 255
    }

    // --------------------------------------------------------------- TRACK DAY

    private fun track(c: Canvas) {
        val tile = carbon
        if (tile != null) {
            fill.shader = cached(0) {
                BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        } else {
            fill.shader = null
            fill.color = DriveMode.TRACK.ground
        }
        c.drawRect(0f, 0f, w, h, fill)

        // vignette, so the weave reads as a curved panel rather than wallpaper
        fill.shader = cached(1) {
            RadialGradient(
                w * 0.5f, h * 0.42f, w * 0.78f,
                ints(0x00000000, 0x59000000, 0xD9000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, h, fill)

        // a sheen of light across the lacquer; alive, it slides
        val sheen = cached(2) {
            LinearGradient(
                0f, 0f, w * 0.16f, w * 0.16f,
                ints(0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val along = w * (0.30f + 0.9f * sin(t * 0.18f))
        shift.setTranslate(along, along)
        sheen.setLocalMatrix(shift)
        fill.shader = sheen
        c.drawRect(0f, 0f, w, h, fill)

        // chequered flag, waving out of the top-right corner
        fill.shader = null
        val square = h * 0.03f
        val reach = square * 13f
        for (row in 0 until 6) {
            for (col in 0 until 12) {
                if ((row + col) % 2 != 0) continue
                val x = w - (col + 1) * square
                val y = row * square + square * 0.35f * sin(col * 0.7f + t * 1.4f)
                val distance = (col * col + row * row).toFloat().pow(0.5f) * square / reach
                if (distance >= 1f) continue
                fill.color = 0xFFFFFFFF.toInt()
                fill.alpha = alpha(0.10f * (1f - distance))
                c.drawRect(x, y, x + square, y + square, fill)
            }
        }
        fill.alpha = 255

        // twin racing stripes rising out of the bottom-right, fading as they go
        fill.shader = cached(3) {
            LinearGradient(
                0f, h * TRACK_STRIPE_TOP, 0f, h,
                0x00FFD60A, 0xC7FFD60A.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        c.drawPath(stripeWide, fill)
        c.drawPath(stripeThin, fill)

        // the kerb: red and white rumble strip along the bottom edge
        val kerbHeight = h * KERB
        fill.shader = null
        fill.color = 0xE6F2F2F2.toInt()
        c.drawRect(0f, h - kerbHeight, w, h, fill)
        fill.color = DriveMode.TRACK.accent
        c.drawPath(kerb, fill)
        fill.color = 0xFF050506.toInt()
        c.drawRect(0f, h - kerbHeight - max(1f, h * 0.003f), w, h - kerbHeight, fill)
    }

    // ------------------------------------------------------------- TOKYO KAIDO

    private fun kaido(c: Canvas) {
        val horizon = h * KAIDO_HORIZON

        fill.shader = cached(0) {
            LinearGradient(
                0f, 0f, 0f, horizon,
                ints(0xFF05020C, 0xFF140626, 0xFF2E0C44, 0xFF5A1858),
                floatArrayOf(0f, 0.45f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, horizon, fill)

        // light pollution: the city lighting its own sky from below
        fill.shader = cached(1) {
            RadialGradient(
                w * 0.5f, horizon, w * 0.62f,
                ints(0x55FF3EA5, 0x00FF3EA5),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, horizon, fill)

        fill.shader = null
        fill.color = 0xFF0A0516.toInt()
        c.drawPath(skyline, fill)

        fill.color = 0x80FFC36B.toInt()
        fill.alpha = alpha(0.5f * pulse(2.3f, 0.2f))
        c.drawPath(windowsAmber, fill)
        fill.color = 0x8C4DF0FF.toInt()
        fill.alpha = alpha(0.55f)
        c.drawPath(windowsCyan, fill)
        fill.color = 0x99FF3EA5.toInt()
        fill.alpha = alpha(0.6f * pulse(1.7f, 0.15f))
        c.drawPath(windowsPink, fill)
        fill.alpha = 255

        // neon signs; alive, they buzz
        for (i in 0 until SIGNS) {
            val rgb = if (signs[i * 4 + 3] < 0.5f) 0xFF3EA5 else 0x4DF0FF
            bokeh(
                c, 2 + i, rgb,
                signs[i * 4], signs[i * 4 + 1], signs[i * 4 + 2],
                pulse(3.1f + i * 0.9f, 0.18f)
            )
        }

        fill.shader = cached(9) {
            LinearGradient(
                0f, horizon, 0f, h,
                0xFF0E0619.toInt(), 0xFF04020A.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, horizon, w, h, fill)

        // the elevated expressway: pillars, then the traffic, then the deck
        val deckTop = h * 0.755f
        val deckBottom = h * 0.785f
        fill.shader = null
        fill.color = 0xFF0D0717.toInt()
        for (i in 0..7) {
            val x = w * (0.06f + i * 0.13f)
            c.drawRect(x, deckBottom, x + w * 0.018f, h, fill)
        }

        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until TRAILS) {
            val tail = i < TAIL_TRAILS
            stroke.shader = if (tail) {
                cached(10) {
                    LinearGradient(
                        0f, 0f, w, 0f,
                        ints(0x00FF2D6E, 0xCCFF2D6E, 0xE6FF3EA5, 0x00FF3EA5),
                        floatArrayOf(0f, 0.2f, 0.8f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            } else {
                cached(11) {
                    LinearGradient(
                        0f, 0f, w, 0f,
                        ints(0x00E8FBFF, 0xB3E8FBFF, 0xCC4DF0FF, 0x004DF0FF),
                        floatArrayOf(0f, 0.2f, 0.8f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            }
            stroke.strokeWidth = max(1f, h * (if (i % 2 == 0) 0.0035f else 0.0022f))
            c.drawPath(trailPaths[i], stroke)
        }

        // one brighter car in every lane; alive, they drive
        stroke.shader = null
        stroke.strokeWidth = max(1.5f, h * 0.006f)
        for (i in 0 until TRAILS) {
            val tail = i < TAIL_TRAILS
            val run = (t * (0.09f + i * 0.013f) + i * 0.37f) % 1f
            val u = if (tail) 1f - run else run
            val back = if (tail) min(1f, u + 0.06f) else max(0f, u - 0.06f)
            stroke.color = if (tail) 0xFFFF8AC4.toInt() else 0xFFFFFFFF.toInt()
            c.drawLine(
                bezier(i, 0, u), bezier(i, 1, u),
                bezier(i, 0, back), bezier(i, 1, back),
                stroke
            )
        }
        stroke.strokeCap = Paint.Cap.BUTT

        fill.color = 0xFF140A22.toInt()
        c.drawRect(0f, deckTop, w, deckBottom, fill)
        stroke.color = 0xCC4DF0FF.toInt()
        stroke.strokeWidth = max(1f, h * 0.003f)
        c.drawLine(0f, deckTop, w, deckTop, stroke)

        for (i in 0..9) {
            val x = w * (0.05f + i * 0.1f)
            val y = deckTop - h * 0.014f
            fill.color = 0x40FFC36B
            c.drawCircle(x, y, h * 0.013f, fill)
            fill.color = 0xFFFFD58A.toInt()
            c.drawCircle(x, y, h * 0.0035f, fill)
        }
    }

    /** @param axis 0 for x, 1 for y. A quadratic curve, evaluated without allocating. */
    private fun bezier(trail: Int, axis: Int, u: Float): Float {
        val base = trail * 6 + axis
        val v = 1f - u
        return v * v * trails[base] + 2f * v * u * trails[base + 2] + u * u * trails[base + 4]
    }

    // ------------------------------------------------------------- GOLDEN HOUR

    private fun golden(c: Canvas) {
        val horizon = h * GOLDEN_HORIZON
        val sunX = w * 0.66f
        val sunY = horizon - w * 0.012f
        val sun = w * 0.07f

        fill.shader = cached(0) {
            LinearGradient(
                0f, 0f, 0f, horizon,
                ints(0xFF1E1838, 0xFF5A3360, 0xFFC4614E, 0xFFF59A52, 0xFFFFCF7E),
                floatArrayOf(0f, 0.32f, 0.66f, 0.86f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w, horizon, fill)

        fill.shader = cached(1) {
            RadialGradient(
                sunX, sunY, sun,
                ints(0xFFFFFBEA, 0xFFFFE3A6, 0x00FFC870),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(sunX, sunY, sun, fill)

        fill.shader = cached(2) {
            LinearGradient(
                0f, horizon, 0f, h,
                ints(0xFF4A2A22, 0xFF22141A, 0xFF120A10),
                floatArrayOf(0f, 0.3f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, horizon, w, h, fill)

        // the halo goes over the land too: low sun fills the air, not just the sky
        fill.shader = cached(3) {
            RadialGradient(
                sunX, sunY, w * 0.5f,
                ints(0x66FFD48A, 0x22FF9A52, 0x00FF9A52),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        fill.alpha = alpha(pulse(0.5f, 0.1f))
        c.drawRect(0f, 0f, w, h, fill)
        fill.alpha = 255

        val nearHalf = w * 0.34f
        road(
            c, 4, horizon, w * 0.5f, nearHalf,
            0xFF2A1A1C.toInt(), 0xFF140C10.toInt(),
            0x66FFD9A0, 0xCCFFE2B0.toInt()
        )

        // telegraph poles down the left verge, at the same speed as the road
        stroke.shader = null
        stroke.color = 0xFF1A0E12.toInt()
        val phase = (t * ROAD_SPEED * POLES / DASHES) % 1f
        for (k in 0 until POLES) {
            val u = ((k + phase) / POLES).pow(2.2f)
            if (u <= 0.002f || u > 1f) continue
            val y = horizon + (h - horizon) * u
            val depth = (y - horizon) / (h - horizon)
            val x = w * 0.5f - nearHalf * depth * 1.45f
            val tall = (h - horizon) * 1.6f * depth
            stroke.strokeWidth = max(1f, w * 0.006f * depth)
            c.drawLine(x, y, x, y - tall, stroke)
            c.drawLine(x - tall * 0.18f, y - tall * 0.92f, x + tall * 0.18f, y - tall * 0.92f, stroke)
        }

        // lens flare, strung along the line from the sun through the centre
        fill.shader = null
        val dx = w * 0.5f - sunX
        val dy = h * 0.5f - sunY
        for (i in 0 until FLARE.size / 2) {
            val along = FLARE[i * 2]
            fill.color = FLARE_COLOURS[i]
            fill.alpha = alpha((FLARE_COLOURS[i] ushr 24) / 255f * pulse(0.8f + i * 0.31f, 0.3f))
            c.drawCircle(sunX + dx * along, sunY + dy * along, w * FLARE[i * 2 + 1], fill)
        }
        fill.alpha = 255
    }

    // ------------------------------------------------------------------ shared

    /**
     * A road running straight to the vanishing point, with centre dashes laid
     * out in perspective. Alive, the dashes rush towards you.
     */
    private fun road(
        c: Canvas,
        slot: Int,
        horizon: Float,
        vanishX: Float,
        nearHalf: Float,
        top: Int,
        bottom: Int,
        edge: Int,
        dash: Int
    ) {
        val farHalf = nearHalf * 0.02f
        path.rewind()
        path.moveTo(vanishX - farHalf, horizon)
        path.lineTo(vanishX + farHalf, horizon)
        path.lineTo(vanishX + nearHalf, h)
        path.lineTo(vanishX - nearHalf, h)
        path.close()
        fill.shader = cached(slot) {
            LinearGradient(0f, horizon, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        }
        c.drawPath(path, fill)

        stroke.shader = null
        stroke.color = edge
        stroke.strokeWidth = max(1f, w * 0.0016f)
        c.drawLine(vanishX - farHalf, horizon, vanishX - nearHalf, h, stroke)
        c.drawLine(vanishX + farHalf, horizon, vanishX + nearHalf, h, stroke)

        stroke.color = dash
        val phase = (t * ROAD_SPEED) % 1f
        for (k in 0 until DASHES) {
            val a = ((k + phase) / DASHES).pow(2.2f)
            if (a >= 1f) continue
            val b = min(1f, ((k + phase + 0.45f) / DASHES).pow(2.2f))
            val y1 = horizon + (h - horizon) * a
            val y2 = horizon + (h - horizon) * b
            stroke.strokeWidth = max(1f, nearHalf * 0.035f * (y2 - horizon) / (h - horizon))
            c.drawLine(vanishX, y1, vanishX, y2, stroke)
        }
    }

    /** A soft disc of out-of-focus light. @param rgb a plain 0xRRGGBB value. */
    private fun bokeh(
        c: Canvas,
        slot: Int,
        rgb: Int,
        x: Float,
        y: Float,
        size: Float,
        strength: Float
    ) {
        val radius = w * size
        if (radius <= 0f) return
        val cx = w * x
        val cy = h * y
        fill.shader = cached(slot) {
            RadialGradient(
                cx, cy, radius,
                (0x8C000000.toInt() or rgb), (rgb and 0x00FFFFFF),
                Shader.TileMode.CLAMP
            )
        }
        fill.alpha = alpha(strength)
        c.drawCircle(cx, cy, radius, fill)
        fill.alpha = 255
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

    private fun alpha(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 255f).toInt()

    private fun ints(vararg colours: Long): IntArray = IntArray(colours.size) { colours[it].toInt() }

    // ------------------------------------------------------------ the scenery

    /** Seeded, so the rain pattern is identical on every redraw instead of crawling. */
    private fun buildRain() {
        val rnd = Random(4219)
        val count = 84
        val pts = FloatArray(count * 4)
        for (i in 0 until count) {
            // wider than the screen, because falling along the slant walks the
            // pattern sideways and the right edge must never run dry
            val x = rnd.nextFloat() * w * 1.45f - w * 0.1f
            val y = rnd.nextFloat() * h
            val len = h * (0.05f + rnd.nextFloat() * 0.07f)
            pts[i * 4] = x
            pts[i * 4 + 1] = y
            pts[i * 4 + 2] = x - len * RAIN_SLANT
            pts[i * 4 + 3] = y + len
        }
        rain = pts
    }

    private fun buildStars() {
        val rnd = Random(9020)
        val horizon = h * AURORA_HORIZON
        fun field(count: Int) = FloatArray(count * 2).also {
            for (i in 0 until count) {
                it[i * 2] = rnd.nextFloat() * w
                // denser high up, thinning towards the glow of the horizon
                it[i * 2 + 1] = rnd.nextFloat() * rnd.nextFloat() * horizon * 0.9f
            }
        }
        starsA = field(60)
        starsB = field(45)
    }

    private fun buildRanges() {
        val horizon = h * AURORA_HORIZON
        fun range(p: Path, seed: Int, rise: Float, notch: Boolean) {
            val rnd = Random(seed)
            p.rewind()
            p.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                // the near range parts in the middle to let the road through
                val cut = if (notch) min(1f, abs(x / w - 0.5f) * 5f) else 1f
                p.lineTo(x, horizon - rise * (0.25f + 0.75f * rnd.nextFloat()) * cut)
                x += w * (0.025f + rnd.nextFloat() * 0.045f)
            }
            p.lineTo(w, horizon - rise * 0.4f)
            p.lineTo(w, h)
            p.close()
        }
        range(farRange, 311, h * 0.13f, false)
        range(nearRange, 577, h * 0.07f, true)
    }

    /**
     * A 2x2 twill tile, a few pixels across, repeated by the GPU. About a
     * kilobyte for a full-screen carbon-fibre weave.
     */
    private fun buildCarbon() {
        val cell = max(3, (w / 320f).toInt())
        val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        val paint = Paint()
        for (i in 0..1) {
            for (j in 0..1) {
                val left = (i * cell).toFloat()
                val top = (j * cell).toFloat()
                paint.shader = if ((i + j) % 2 == 0) {
                    LinearGradient(
                        left, 0f, left + cell, 0f,
                        0xFF2C2C33.toInt(), 0xFF0D0D10.toInt(), Shader.TileMode.CLAMP
                    )
                } else {
                    LinearGradient(
                        0f, top, 0f, top + cell,
                        0xFF1E1E24.toInt(), 0xFF08080A.toInt(), Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(left, top, left + cell, top + cell, paint)
            }
        }
        // Not recycled: the previous frame's display list may still hold it.
        carbon = tile
    }

    private fun buildTrack() {
        val top = h * TRACK_STRIPE_TOP
        val run = (h - top) * 0.8f
        fun stripe(p: Path, from: Float, to: Float) {
            p.rewind()
            p.moveTo(w * from, h)
            p.lineTo(w * to, h)
            p.lineTo(w * to + run, top)
            p.lineTo(w * from + run, top)
            p.close()
        }
        stripe(stripeWide, 0.66f, 0.72f)
        stripe(stripeThin, 0.735f, 0.755f)

        val tall = h * KERB
        val block = tall * 2.2f
        kerb.rewind()
        var x = -block * 2f
        while (x < w) {
            kerb.moveTo(x, h)
            kerb.lineTo(x + block, h)
            kerb.lineTo(x + block + tall * 0.6f, h - tall)
            kerb.lineTo(x + tall * 0.6f, h - tall)
            kerb.close()
            x += block * 2f
        }
    }

    private fun buildCity() {
        val horizon = h * KAIDO_HORIZON
        val rnd = Random(7741)
        skyline.rewind()
        windowsAmber.rewind()
        windowsCyan.rewind()
        windowsPink.rewind()

        val pane = max(2f, w * 0.0035f)
        var x = -w * 0.02f
        skyline.moveTo(x, horizon)
        while (x < w) {
            val wide = w * (0.025f + rnd.nextFloat() * 0.05f)
            // mostly low-rise, with the odd tower
            val tall = h * (0.06f + rnd.nextFloat() * rnd.nextFloat() * 0.30f)
            val top = horizon - tall
            skyline.lineTo(x, top)
            skyline.lineTo(x + wide, top)

            var wy = top + pane * 2f
            while (wy < horizon - pane * 2f) {
                var wx = x + pane * 1.5f
                while (wx < x + wide - pane * 2f) {
                    if (rnd.nextFloat() < 0.22f) {
                        val pick = rnd.nextFloat()
                        val target = when {
                            pick < 0.5f -> windowsAmber
                            pick < 0.78f -> windowsCyan
                            else -> windowsPink
                        }
                        target.addRect(wx, wy, wx + pane, wy + pane * 1.4f, Path.Direction.CW)
                    }
                    wx += pane * 2.6f
                }
                wy += pane * 3.2f
            }
            x += wide
        }
        skyline.lineTo(x, horizon)
        skyline.close()

        for (i in 0 until SIGNS) {
            signs[i * 4] = 0.06f + rnd.nextFloat() * 0.88f
            signs[i * 4 + 1] = KAIDO_HORIZON - 0.05f - rnd.nextFloat() * 0.2f
            signs[i * 4 + 2] = 0.010f + rnd.nextFloat() * 0.018f
            signs[i * 4 + 3] = rnd.nextFloat()
        }
    }

    /** Tail lights in the far lane, headlights in the near one. */
    private fun buildTrails() {
        for (i in 0 until TRAILS) {
            val tail = i < TAIL_TRAILS
            val lane = if (tail) i else i - TAIL_TRAILS
            val o = i * 6
            if (tail) {
                curve(o, -0.05f, 0.840f + lane * 0.012f, 0.5f, 0.860f + lane * 0.006f, 1.05f, 0.800f + lane * 0.012f)
            } else {
                curve(o, -0.05f, 0.930f + lane * 0.011f, 0.45f, 0.950f + lane * 0.010f, 1.05f, 0.890f + lane * 0.010f)
            }
            trailPaths[i].rewind()
            trailPaths[i].moveTo(trails[o], trails[o + 1])
            trailPaths[i].quadTo(trails[o + 2], trails[o + 3], trails[o + 4], trails[o + 5])
        }
    }

    private fun curve(o: Int, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        trails[o] = w * x0
        trails[o + 1] = h * y0
        trails[o + 2] = w * x1
        trails[o + 3] = h * y1
        trails[o + 4] = w * x2
        trails[o + 5] = h * y2
    }

    private companion object {
        /** About 24 frames a second: film rate, and gentle on a fanless chip. */
        const val FRAME_MS = 42L
        const val SLOTS = 16
        const val TAU = 6.2831855f

        const val RAIN_SPEED = 1.1f
        const val RAIN_SLANT = 0.22f

        const val ROAD_SPEED = 0.55f
        const val DASHES = 10
        const val POLES = 5

        const val RIBBON_STEPS = 120
        const val RAY_UNIT = 1000f
        val RAY_SHIMMER = floatArrayOf(1f, 0.35f, 0.8f, 0.1f, 0.6f, 0.95f, 0.25f, 0.7f, 0.45f, 0f, 0.85f)
        const val AURORA_HORIZON = 0.70f
        const val KAIDO_HORIZON = 0.66f
        const val GOLDEN_HORIZON = 0.64f
        const val TRACK_STRIPE_TOP = 0.36f
        const val KERB = 0.022f

        const val SIGNS = 6
        const val TRAILS = 7
        const val TAIL_TRAILS = 4

        /** Lens flare ghosts: distance along the sun-to-centre line, then radius. */
        val FLARE = floatArrayOf(
            0.55f, 0.012f,
            0.90f, 0.030f,
            1.25f, 0.018f,
            1.70f, 0.050f
        )
        val FLARE_COLOURS = intArrayOf(
            0x30FFE2B0, 0x1EFF9A52, 0x33FFF1C8, 0x14FFB070
        )
    }
}
