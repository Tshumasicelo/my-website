package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider

/**
 * Surfaces are built in code because the Drive Mode is a runtime setting, and a
 * static selector drawable cannot follow it. Everything here is translucent: the
 * painted background is the point, so cards behave like glass over it rather
 * than opaque panels sitting on top.
 */
object ThemeKit {

    private const val HAIRLINE = 0x1AFFFFFF
    private const val GLASS = 0x0EFFFFFF

    fun dp(ctx: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, ctx.resources.displayMetrics
    ).toInt()

    fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255f).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    /** Mixes [overlay] into [base] by [ratio]. Alpha is ignored; callers add it. */
    fun blend(base: Int, overlay: Int, ratio: Float): Int {
        val mix = ratio.coerceIn(0f, 1f)
        val keep = 1f - mix
        return Color.rgb(
            (Color.red(base) * keep + Color.red(overlay) * mix).toInt(),
            (Color.green(base) * keep + Color.green(overlay) * mix).toInt(),
            (Color.blue(base) * keep + Color.blue(overlay) * mix).toInt()
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
     * A fresh selector per view on purpose: one shared StateListDrawable would
     * light every card at once, because drawable state is per instance.
     */
    fun cardSelector(
        ctx: Context,
        mode: DriveMode,
        radiusDp: Float = 14f,
        glowOverride: Int = 0
    ): StateListDrawable {
        val glow = if (glowOverride == 0) mode.glow else glowOverride
        val r = dp(ctx, radiusDp).toFloat()
        val focused = rounded(
            withAlpha(glow, 0.14f), glow, dp(ctx, 3f), r
        )
        val idle = rounded(GLASS, HAIRLINE, dp(ctx, 1f), r)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(IntArray(0), idle)
        }
    }

    fun chip(ctx: Context): GradientDrawable =
        rounded(0x33000000, HAIRLINE, dp(ctx, 1f), dp(ctx, 22f).toFloat())

    fun panel(ctx: Context, mode: DriveMode): GradientDrawable =
        rounded(withAlpha(mode.accent, 0.16f), withAlpha(mode.accent, 0.55f), dp(ctx, 1f), dp(ctx, 12f).toFloat())

    fun scrim(mode: DriveMode): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(withAlpha(mode.ground, 0.86f), withAlpha(mode.ground, 0.55f))
    )

    fun roundedOutline(ctx: Context, radiusDp: Float): ViewOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(ctx, radiusDp).toFloat())
            }
        }
}
