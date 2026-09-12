package com.aspects.tvlauncher

/**
 * A complete visual identity for the launcher. Every mode is drawn, never
 * photographed: on a 1 GB box a single 1080p wallpaper would cost more memory
 * than the entire rest of the app.
 */
enum class DriveMode(
    val id: String,
    val label: String,
    val blurb: String,
    val ground: Int,
    val glow: Int,
    val accent: Int,
    val ink: Int,
    val dim: Int
) {
    NIGHT(
        id = "night",
        label = "Night Drive",
        blurb = "Gloss black, angel-eye white. Best in bright rooms.",
        ground = 0xFF050507.toInt(),
        glow = 0xFFFFFFFF.toInt(),
        accent = 0xFFFF2D34.toInt(),
        ink = 0xFFFFFFFF.toInt(),
        dim = 0xFF9AA1AE.toInt()
    ),
    WET(
        id = "wet",
        label = "Wet Neon",
        blurb = "Rain navy and taillight red. Most cinematic.",
        ground = 0xFF06101A.toInt(),
        glow = 0xFF39C6E8.toInt(),
        accent = 0xFFFF1F3D.toInt(),
        ink = 0xFFEFF7FB.toInt(),
        dim = 0xFF8AA3B2.toInt()
    ),
    SUNSET(
        id = "sunset",
        label = "Sunset Run",
        blurb = "Synthwave dusk over a mirror plane. Boldest.",
        ground = 0xFF2B1236.toInt(),
        glow = 0xFFFFC7DF.toInt(),
        accent = 0xFFFF4D8D.toInt(),
        ink = 0xFFFFF2F8.toInt(),
        dim = 0xFFD8AEC6.toInt()
    );

    companion object {
        fun byIndex(index: Int): DriveMode = values()[index.coerceIn(0, values().size - 1)]
        fun next(index: Int): Int = (index + 1) % values().size
    }
}
