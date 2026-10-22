package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout

/**
 * A comet of light that laps the border of the focused card, in that app's own
 * colour - the RGB-keyboard effect, done as a card foreground.
 *
 * The whole effect is one SweepGradient, built once per colour and size, and
 * turned by rotating its local matrix. Nothing is allocated per frame. It
 * steps at [FRAME_MS] rather than every vsync: a light this size reads as
 * smooth at thirty frames, and a fanless chip appreciates the other thirty.
 */
class EdgeLight(context: Context) : Drawable(), Runnable {

    private val width = ThemeKit.dp(context, 3f).toFloat()
    private val radius = ThemeKit.dp(context, RADIUS_DP).toFloat()

    private val comet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
    }
    private val box = RectF()
    private val spin = Matrix()
    private var sweep: SweepGradient? = null
    private var angle = 0f
    private var running = false

    var colour: Int = 0xFFFFFFFF.toInt()
        set(value) {
            if (field == value) return
            field = value
            sweep = null
            invalidateSelf()
        }

    fun start() {
        if (running) return
        running = true
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS)
    }

    fun stop() {
        running = false
        unscheduleSelf(this)
    }

    override fun run() {
        if (!running) return
        angle = (angle + 360f * FRAME_MS / LAP_MS) % 360f
        invalidateSelf()
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS)
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) stop()
        return changed
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        sweep = null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val half = width / 2f
        box.set(b.left + half, b.top + half, b.right - half, b.bottom - half)

        // a dim ring the comet runs along, so the card still reads as selected
        // in the moment the head is on the far side
        track.color = ThemeKit.withAlpha(colour, 0.32f)
        canvas.drawRoundRect(box, radius, radius, track)

        val cx = box.centerX()
        val cy = box.centerY()
        val shade = sweep ?: SweepGradient(
            cx, cy,
            intArrayOf(
                ThemeKit.withAlpha(colour, 0f),
                ThemeKit.withAlpha(colour, 0f),
                ThemeKit.withAlpha(colour, 0.45f),
                colour,
                0xFFFFFFFF.toInt(),
                ThemeKit.withAlpha(colour, 0f)
            ),
            floatArrayOf(0f, 0.5f, 0.82f, 0.965f, 0.985f, 1f)
        ).also { sweep = it }
        spin.setRotate(angle, cx, cy)
        shade.setLocalMatrix(spin)
        comet.shader = shade
        canvas.drawRoundRect(box, radius, radius, comet)
    }

    override fun setAlpha(alpha: Int) {
        comet.alpha = alpha
        track.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        comet.colorFilter = colorFilter
        track.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Matches the card's own corner radius in ThemeKit. */
        const val RADIUS_DP = 14f
        const val FRAME_MS = 33L
        const val LAP_MS = 2600L

        /** Lights [card] in [colour], reusing its light if it already has one. */
        fun attach(card: View, colour: Int) {
            val frame = card as? FrameLayout ?: return
            val light = frame.foreground as? EdgeLight
                ?: EdgeLight(card.context).also { frame.foreground = it }
            light.colour = colour
            light.start()
        }

        fun detach(card: View?) {
            val frame = card as? FrameLayout ?: return
            val light = frame.foreground as? EdgeLight ?: return
            light.stop()
            frame.foreground = null
        }
    }
}
