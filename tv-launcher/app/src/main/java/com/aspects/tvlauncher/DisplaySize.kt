package com.aspects.tvlauncher

import android.content.Context
import android.content.res.Configuration

/**
 * Three sizes for the whole launcher, applied the way Android's own Display Size
 * setting works: by overriding the density an activity resolves its resources
 * against, rather than by resizing every view by hand.
 *
 * That distinction matters. Scaling views individually means every dimension
 * needs its own multiplier, and anything missed drifts out of proportion - a
 * tile shrinks but its label does not, a gauge shrinks but its margin does not.
 * Overriding the density moves one number, and tile widths, gauge diameters,
 * overscan margins, corner radii and text all follow it together. The layout
 * keeps its proportions at every size because nothing is scaled twice.
 */
object DisplaySize {

    const val LARGE = 0
    const val MEDIUM = 1
    const val SMALL = 2

    /** Large is the size everything was designed at, so it is exactly 1. */
    private val SCALES = floatArrayOf(1.00f, 0.86f, 0.74f)

    fun scale(index: Int): Float = SCALES.getOrElse(index) { 1f }

    fun labelRes(index: Int): Int = when (index) {
        MEDIUM -> R.string.size_medium
        SMALL -> R.string.size_small
        else -> R.string.size_large
    }

    fun next(index: Int): Int = (index + 1) % SCALES.size

    /**
     * Wraps a context so everything inflated from it resolves at the chosen
     * size. Called from attachBaseContext, which runs before any view exists -
     * the size has to be settled before the first layout is inflated, because
     * a view already measured at one density will not re-measure at another.
     */
    fun wrap(base: Context): Context {
        val factor = scale(Prefs(base).displaySize)
        if (factor == 1f) return base
        val config = Configuration(base.resources.configuration)
        config.densityDpi = (base.resources.displayMetrics.densityDpi * factor).toInt()
        return base.createConfigurationContext(config)
    }
}
