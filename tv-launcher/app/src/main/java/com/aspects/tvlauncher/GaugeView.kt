package com.aspects.tvlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * A half-dial instrument: track, filled arc, needle, hub and reading. Past
 * three-quarters it crosses into the redline and recolours, so pressure reads
 * at a glance the way a rev counter does - which is the entire point of putting
 * a dashboard on a television.
 */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var glow: Int = 0xFFFFFFFF.toInt()
    var accent: Int = 0xFFFF2D34.toInt()
    var dim: Int = 0xFF9AA1AE.toInt()

    var label: String = ""
    var reading: String = ""

    var fraction: Float = 0f
        private set

    private var drawn = 0f
    private var animator: ValueAnimator? = null

    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val box = RectF()

    fun setValue(value: Float, animate: Boolean) {
        fraction = value.coerceIn(0f, 1f)
        if (!animate) {
            animator?.cancel()
            drawn = fraction
            invalidate()
            return
        }
        animate(ValueAnimator.ofFloat(drawn, fraction), 500L)
    }

    /** The cluster self-test: hard sweep to redline, back, then settle. */
    fun sweep() {
        animate(ValueAnimator.ofFloat(0f, 1f, 0f, fraction), 1150L)
    }

    private fun animate(next: ValueAnimator, ms: Long) {
        animator?.cancel()
        next.duration = ms
        next.interpolator = DecelerateInterpolator(1.3f)
        next.addUpdateListener {
            drawn = it.animatedValue as Float
            invalidate()
        }
        animator = next
        next.start()
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.74f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        if (w <= 0f) return

        val band = w * 0.085f
        val radius = (w - band) / 2f
        val cx = w / 2f
        val cy = radius + band / 2f
        box.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val value = drawn.coerceIn(0f, 1f)
        val colour = if (value > 0.75f) accent else glow

        arc.strokeWidth = band
        arc.color = 0x21FFFFFF.toInt()
        canvas.drawArc(box, 180f, 180f, false, arc)

        arc.color = colour
        canvas.drawArc(box, 180f, 180f * value, false, arc)

        // 180deg at rest, sweeping clockwise to 360deg at full
        val angle = Math.PI * (1.0 + value)
        needle.color = colour
        needle.strokeWidth = w * 0.030f
        canvas.drawLine(
            cx, cy,
            cx + cos(angle).toFloat() * radius * 0.78f,
            cy + sin(angle).toFloat() * radius * 0.78f,
            needle
        )

        solid.color = colour
        canvas.drawCircle(cx, cy, w * 0.045f, solid)

        text.color = 0xFFFFFFFF.toInt()
        text.textSize = w * 0.185f
        text.isFakeBoldText = true
        canvas.drawText(reading, cx, cy + w * 0.20f, text)

        text.color = dim
        text.textSize = w * 0.125f
        text.isFakeBoldText = false
        canvas.drawText(label, cx, cy + w * 0.375f, text)
    }
}
