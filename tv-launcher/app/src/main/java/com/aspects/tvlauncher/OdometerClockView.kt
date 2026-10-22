package com.aspects.tvlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * A clock whose digits roll over like an odometer wheel instead of cutting.
 *
 * Each character sits in its own slot. When a slot's character changes, the
 * old one scrolls up and out while the new one rises in from below, clipped to
 * the slot. Only the slots that changed move, so 12:59 to 13:00 rolls three
 * wheels and leaves the colon alone - and the rightmost wheel moves first, the
 * way a carry ripples leftward through a real odometer.
 *
 * Digits are laid out on their widest glyph, so a 1 never makes the clock
 * shuffle sideways the way proportional figures would.
 */
class OdometerClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var current = ""
    private var previous = ""
    private var progress = 1f
    private var animator: ValueAnimator? = null

    /** When false, changes cut instantly, like a plain clock. */
    var rolling = true

    var textColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    fun setTextSizePx(size: Float) {
        if (paint.textSize == size) return
        paint.textSize = size
        requestLayout()
        invalidate()
    }

    fun setTypeface(face: Typeface) {
        if (paint.typeface == face) return
        paint.typeface = face
        requestLayout()
        invalidate()
    }

    val text: String get() = current

    fun setText(next: String) {
        if (next == current) return
        val rolls = rolling && current.isNotEmpty() && next.length == current.length
        animator?.cancel()
        previous = current
        current = next
        if (current.length != previous.length) requestLayout()

        if (!rolls) {
            progress = 1f
            invalidate()
            return
        }
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ROLL_MS + STAGGER_MS * current.length
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        progress = 1f
        super.onDetachedFromWindow()
    }

    private fun digitWidth(): Float {
        var widest = 0f
        for (d in '0'..'9') widest = maxOf(widest, paint.measureText(d.toString()))
        return widest
    }

    private fun slotWidth(ch: Char, digit: Float): Float =
        if (ch in '0'..'9') digit else paint.measureText(ch.toString())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val digit = digitWidth()
        var width = 0f
        for (ch in current) width += slotWidth(ch, digit)
        val metrics = paint.fontMetrics
        val height = metrics.descent - metrics.ascent
        setMeasuredDimension(
            resolveSize((width + paddingLeft + paddingRight).toInt() + 1, widthMeasureSpec),
            resolveSize((height + paddingTop + paddingBottom).toInt() + 1, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (current.isEmpty()) return
        val digit = digitWidth()
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val baseline = paddingTop - metrics.ascent
        val top = paddingTop.toFloat()
        val bottom = top + lineHeight
        val solid = paint.alpha

        var x = paddingLeft.toFloat()
        val count = current.length
        for (i in 0 until count) {
            val ch = current[i]
            val slot = slotWidth(ch, digit)
            val centre = x + slot / 2f
            val old = if (i < previous.length) previous[i] else ch

            if (old == ch || progress >= 1f) {
                canvas.drawText(ch.toString(), centre, baseline, paint)
            } else {
                // right to left: each wheel starts a beat after the one on its right
                val delay = (count - 1 - i) * STAGGER_FRACTION
                val p = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
                canvas.save()
                canvas.clipRect(x, top, x + slot, bottom)
                paint.alpha = (solid * (1f - p)).toInt()
                canvas.drawText(old.toString(), centre, baseline - lineHeight * p, paint)
                paint.alpha = (solid * p).toInt().coerceAtLeast(0)
                canvas.drawText(ch.toString(), centre, baseline + lineHeight * (1f - p), paint)
                paint.alpha = solid
                canvas.restore()
            }
            x += slot
        }
    }

    private companion object {
        const val ROLL_MS = 520L
        const val STAGGER_MS = 40L
        const val STAGGER_FRACTION = 0.07f
    }
}
