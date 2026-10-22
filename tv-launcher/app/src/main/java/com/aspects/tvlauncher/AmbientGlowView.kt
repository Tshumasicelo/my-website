package com.aspects.tvlauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Ambilight for a launcher: the edges of the screen glow in the focused app's
 * colour, as if the television were lighting the wall behind it.
 *
 * Four edge gradients and nothing else. They are rebuilt only when the colour
 * actually changes - every frame of a 420 ms crossfade, then not again until
 * focus moves - so a still screen costs one replayed display list.
 */
class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint()
    private var shown = 0
    private var target = 0
    private var built = 0
    private var builtW = 0
    private var builtH = 0
    private var animator: ValueAnimator? = null

    private var edgeLeft: Shader? = null
    private var edgeRight: Shader? = null
    private var edgeTop: Shader? = null
    private var edgeBottom: Shader? = null

    /** Crossfades the edges to [colour], whose alpha sets how strong they glow. */
    fun glowTo(colour: Int) {
        if (colour == target) return
        target = colour
        animator?.cancel()
        animator = ValueAnimator.ofObject(ArgbEvaluator(), shown, colour).apply {
            duration = 420L
            addUpdateListener {
                shown = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun off() {
        animator?.cancel()
        animator = null
        shown = 0
        target = 0
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || (shown ushr 24) == 0) return

        if (built != shown || builtW != w || builtH != h) {
            val clear = shown and 0x00FFFFFF
            val side = w * 0.11f
            edgeLeft = LinearGradient(0f, 0f, side, 0f, shown, clear, Shader.TileMode.CLAMP)
            edgeRight = LinearGradient(w.toFloat(), 0f, w - side, 0f, shown, clear, Shader.TileMode.CLAMP)
            edgeTop = LinearGradient(0f, 0f, 0f, h * 0.07f, shown, clear, Shader.TileMode.CLAMP)
            edgeBottom = LinearGradient(0f, h.toFloat(), 0f, h * 0.80f, shown, clear, Shader.TileMode.CLAMP)
            built = shown
            builtW = w
            builtH = h
        }

        paint.shader = edgeLeft
        canvas.drawRect(0f, 0f, w * 0.11f, h.toFloat(), paint)
        paint.shader = edgeRight
        canvas.drawRect(w * 0.89f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = edgeTop
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.07f, paint)
        paint.shader = edgeBottom
        canvas.drawRect(0f, h * 0.80f, w.toFloat(), h.toFloat(), paint)
    }
}
