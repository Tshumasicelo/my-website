package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider

/** One accent colour the launcher can be themed with. */
data class Accent(val name: String, val color: Int)

object Accents {
    val ALL = listOf(
        Accent("Indigo", 0xFF5B4BFF.toInt()),
        Accent("Cyan", 0xFF00E5C0.toInt()),
        Accent("Amber", 0xFFFFB020.toInt()),
        Accent("Rose", 0xFFFF4D8D.toInt()),
        Accent("Green", 0xFF22C55E.toInt()),
        Accent("Ice", 0xFF7DD3FC.toInt())
    )

    fun at(index: Int): Accent = ALL[index.coerceIn(0, ALL.size - 1)]

    fun next(index: Int): Int = (index + 1) % ALL.size
}

/**
 * Every themed surface is built in code rather than in XML, because the accent
 * colour is a runtime setting and a static selector drawable cannot follow it.
 */
object ThemeKit {

    private const val SURFACE = 0xFF12141C.toInt()
    private const val SURFACE_HI = 0xFF1A1D28.toInt()
    private const val HAIRLINE = 0x1FFFFFFF

    fun dp(ctx: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, ctx.resources.displayMetrics
    ).toInt()

    fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255f).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun blend(base: Int, overlay: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(base) * inv + Color.red(overlay) * ratio).toInt(),
            (Color.green(base) * inv + Color.green(overlay) * ratio).toInt(),
            (Color.blue(base) * inv + Color.blue(overlay) * ratio).toInt()
        )
    }

    private fun rounded(fill: Int, strokeColor: Int, strokeWidth: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(strokeWidth, strokeColor)
        }

    /**
     * A fresh selector per view on purpose: a single StateListDrawable instance
     * shared across cards would light every card up at once, because drawable
     * state is per-instance.
     */
    fun cardSelector(ctx: Context, accent: Int, radiusDp: Float = 14f): StateListDrawable {
        val r = dp(ctx, radiusDp).toFloat()
        val focused = rounded(blend(SURFACE_HI, accent, 0.16f), accent, dp(ctx, 3f), r)
        val idle = rounded(SURFACE, HAIRLINE, dp(ctx, 1f), r)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(IntArray(0), idle)
        }
    }

    fun chip(ctx: Context): GradientDrawable =
        rounded(0xFF11131A.toInt(), HAIRLINE, dp(ctx, 1f), dp(ctx, 22f).toFloat())

    fun headerGlow(accent: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(withAlpha(accent, 0.22f), withAlpha(accent, 0.06f), Color.TRANSPARENT)
    )

    fun roundedOutline(ctx: Context, radiusDp: Float): ViewOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(ctx, radiusDp).toFloat())
            }
        }
}
