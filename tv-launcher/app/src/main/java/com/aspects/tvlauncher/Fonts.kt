package com.aspects.tvlauncher

import android.content.Context
import android.graphics.Typeface

/**
 * The dashboard typefaces. Orbitron for the big digits, because it is what a
 * digital speedometer looks like; Rajdhani for everything smaller, because it
 * stays narrow and legible at the sizes a status line uses.
 *
 * Both are subset to Latin and shipped in assets rather than res/font, which
 * needs API 26 natively. Orbitron is a variable font cut down to a single
 * static bold, so it renders identically on every Android version.
 *
 * Loaded once per process: a Typeface maps the file and is safe to share.
 */
object Fonts {

    private var display: Typeface? = null
    private var label: Typeface? = null
    private var labelBold: Typeface? = null

    /** Clock digits. Falls back to the system bold if the asset cannot be read. */
    fun display(ctx: Context, enabled: Boolean): Typeface =
        if (!enabled) Typeface.DEFAULT_BOLD
        else display ?: load(ctx, "fonts/Orbitron-Bold.ttf", Typeface.DEFAULT_BOLD).also { display = it }

    fun label(ctx: Context, enabled: Boolean): Typeface =
        if (!enabled) Typeface.DEFAULT
        else label ?: load(ctx, "fonts/Rajdhani-SemiBold.ttf", Typeface.DEFAULT).also { label = it }

    fun labelBold(ctx: Context, enabled: Boolean): Typeface =
        if (!enabled) Typeface.DEFAULT_BOLD
        else labelBold ?: load(ctx, "fonts/Rajdhani-Bold.ttf", Typeface.DEFAULT_BOLD).also { labelBold = it }

    private fun load(ctx: Context, path: String, fallback: Typeface): Typeface =
        runCatching { Typeface.createFromAsset(ctx.applicationContext.assets, path) }
            .getOrDefault(fallback)
}
