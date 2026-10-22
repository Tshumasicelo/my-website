package com.aspects.tvlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * The gear selector: one letter per home row, stacked like P-R-N-D on an
 * automatic's console, with a lit marker that slides to whichever row holds
 * focus. It answers "where am I?" without reading a single row title, which
 * is exactly what a D-pad on a long screen leaves you wondering.
 *
 * One view, drawn by hand: a pill, a gate line, the letters and the marker.
 * Nothing in it is focusable - it reports position, it is not a control.
 */
class GearRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var gears: List<String> = emptyList()
    private var engaged = -1

    /** Where the marker is drawn, in gear slots; animates towards [engaged]. */
    private var marker = 0f
    private var animator: ValueAnimator? = null

    var glow: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }
    var ink: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }
    var dim: Int = 0xFF9AA1AE.toInt()
        set(value) {
            field = value
            invalidate()
        }
    var ground: Int = 0xFF000000.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()

    private val slot = ThemeKit.dp(context, 30f).toFloat()
    private val pad = ThemeKit.dp(context, 6f).toFloat()

    init {
        letters.textSize = ThemeKit.dp(context, 13f).toFloat()
        letters.isFakeBoldText = true
    }

    fun setTypeface(face: Typeface) {
        letters.typeface = face
        letters.isFakeBoldText = face == Typeface.DEFAULT || face == Typeface.DEFAULT_BOLD
        invalidate()
    }

    /** @param labels one short label per visible row, top to bottom. */
    fun setGears(labels: List<String>) {
        if (labels == gears) return
        val sizeChanged = labels.size != gears.size
        gears = labels
        if (engaged >= gears.size) engage(gears.size - 1, false)
        if (sizeChanged) requestLayout()
        invalidate()
    }

    /** Slides the marker to [index], with a little overshoot, like a lever finding its gate. */
    fun engage(index: Int, animate: Boolean = true) {
        if (index == engaged || gears.isEmpty()) return
        val from = if (engaged < 0) index.toFloat() else marker
        engaged = index.coerceIn(0, gears.size - 1)
        animator?.cancel()
        if (!animate || !isShown) {
            marker = engaged.toFloat()
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(from, engaged.toFloat()).apply {
            duration = 300L
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                marker = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = slot + pad
        val height = slot * gears.size.coerceAtLeast(1) + pad * 2f
        setMeasuredDimension(
            resolveSize(width.toInt(), widthMeasureSpec),
            resolveSize(height.toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (gears.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val radius = w / 2f

        // the console: dark glass with a hairline, like the rest of the cards
        shape.color = ThemeKit.withAlpha(ground, 0.55f)
        box.set(0f, 0f, w, h)
        canvas.drawRoundRect(box, radius, radius, shape)
        line.color = 0x1FFFFFFF
        line.strokeWidth = ThemeKit.dp(context, 1f).toFloat()
        canvas.drawRoundRect(box, radius, radius, line)

        // the gate the lever runs in
        val first = pad + slot / 2f
        val last = pad + slot * (gears.size - 1) + slot / 2f
        line.color = ThemeKit.withAlpha(dim, 0.35f)
        line.strokeWidth = ThemeKit.dp(context, 2f).toFloat()
        if (gears.size > 1) canvas.drawLine(cx, first, cx, last, line)

        // the marker, lit in the Drive Mode's glow
        if (engaged >= 0) {
            val y = pad + slot * marker + slot / 2f
            val half = slot * 0.42f
            shape.color = ThemeKit.withAlpha(glow, 0.22f)
            canvas.drawCircle(cx, y, half * 1.35f, shape)
            shape.color = glow
            box.set(cx - half, y - half, cx + half, y + half)
            canvas.drawRoundRect(box, half * 0.55f, half * 0.55f, shape)
        }

        // letters on top; the engaged one is cut out of the marker in ground colour
        val metrics = letters.fontMetrics
        val lift = (metrics.ascent + metrics.descent) / 2f
        for (i in gears.indices) {
            val y = pad + slot * i + slot / 2f
            val near = engaged >= 0 && kotlin.math.abs(marker - i) < 0.5f
            letters.color = if (near) ground else if (i == engaged) ink else dim
            canvas.drawText(gears[i], cx, y - lift, letters)
        }
    }
}
