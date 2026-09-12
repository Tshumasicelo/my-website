package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** The clock as an instrument dial, for people who would rather read a needle. */
class ClockGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var glow: Int = 0xFFFFFFFF.toInt()
    var accent: Int = 0xFFFF2D34.toInt()
    var dim: Int = 0xFF9AA1AE.toInt()

    private var hours = 0f
    private var minutes = 0f

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setTime(calendar: Calendar) {
        minutes = calendar.get(Calendar.MINUTE).toFloat()
        hours = (calendar.get(Calendar.HOUR) + minutes / 60f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        if (w <= 0f) return
        val cx = w / 2f
        val cy = w / 2f
        val radius = w / 2f - w * 0.06f

        stroke.color = 0x24FFFFFF.toInt()
        stroke.strokeWidth = w * 0.018f
        canvas.drawCircle(cx, cy, radius, stroke)

        // twelve ticks, the quarters emphasised
        for (i in 0 until 12) {
            val a = Math.PI * 2.0 * (i / 12.0) - Math.PI / 2.0
            val major = i % 3 == 0
            stroke.color = if (major) glow else dim
            stroke.strokeWidth = if (major) w * 0.022f else w * 0.011f
            val inner = radius * if (major) 0.80f else 0.87f
            canvas.drawLine(
                cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * radius * 0.95f, cy + sin(a).toFloat() * radius * 0.95f,
                stroke
            )
        }

        hand(canvas, cx, cy, hours / 12f, radius * 0.52f, w * 0.036f, glow)
        hand(canvas, cx, cy, minutes / 60f, radius * 0.78f, w * 0.024f, glow)

        solid.color = accent
        canvas.drawCircle(cx, cy, w * 0.042f, solid)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, turn: Float, len: Float, width: Float, colour: Int) {
        val a = Math.PI * 2.0 * turn - Math.PI / 2.0
        stroke.color = colour
        stroke.strokeWidth = width
        c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, stroke)
    }
}
