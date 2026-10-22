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
    ),

    // Appended, never inserted: the stored preference is an ordinal, so the
    // three originals have to keep the numbers they were saved under.

    AURORA(
        id = "aurora",
        label = "Aurora Highway",
        blurb = "Northern lights over a mountain road. Calmest.",
        ground = 0xFF03101A.toInt(),
        glow = 0xFF5CFFC8.toInt(),
        accent = 0xFFB46CFF.toInt(),
        ink = 0xFFEFFFFA.toInt(),
        dim = 0xFF8FB5AE.toInt()
    ),
    TRACK(
        id = "track",
        label = "Track Day",
        blurb = "Carbon weave, racing stripes, pit-lane yellow. Sharpest.",
        ground = 0xFF0B0B0D.toInt(),
        glow = 0xFFFFD60A.toInt(),
        accent = 0xFFFF3B30.toInt(),
        ink = 0xFFFFFFFF.toInt(),
        dim = 0xFFA6A6AE.toInt()
    ),
    KAIDO(
        id = "kaido",
        label = "Tokyo Kaido",
        blurb = "Neon skyline, elevated highway, light trails. Loudest.",
        ground = 0xFF0C0517.toInt(),
        glow = 0xFF4DF0FF.toInt(),
        accent = 0xFFFF3EA5.toInt(),
        ink = 0xFFF6F0FF.toInt(),
        dim = 0xFFB3A2CC.toInt()
    ),
    GOLDEN(
        id = "golden",
        label = "Golden Hour",
        blurb = "Sunrise down a long empty road. Warmest.",
        ground = 0xFF1B1120.toInt(),
        glow = 0xFFFFD9A0.toInt(),
        accent = 0xFFFF8A3D.toInt(),
        ink = 0xFFFFF7EC.toInt(),
        dim = 0xFFD8B898.toInt()
    );

    companion object {
        fun byIndex(index: Int): DriveMode = values()[index.coerceIn(0, values().size - 1)]
    }
}
