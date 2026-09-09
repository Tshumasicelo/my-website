package com.aspects.tvlauncher

import android.content.Context

/** Thin typed wrapper over SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aspects_tv", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    /**
     * Changing a default only affects fresh installs - an existing device has
     * the old value written to disk and would keep it forever. Anything that has
     * to change for people already running the app belongs here.
     */
    private fun migrate() {
        if (sp.getInt(KEY_SCHEMA, 1) >= 2) return
        sp.edit()
            // v2 hides the All Apps row: firmware packs it with system apps
            // (Clock, Disclaimer, TV Services) that clutter a home screen.
            .putBoolean(KEY_SIDELOADED, false)
            .putInt(KEY_SCHEMA, 2)
            .apply()
    }

    var driveMode: Int
        get() = sp.getInt(KEY_DRIVE_MODE, 0)
        set(value) = sp.edit().putInt(KEY_DRIVE_MODE, value).apply()

    val mode: DriveMode get() = DriveMode.byIndex(driveMode)

    var clock24h: Boolean
        get() = sp.getBoolean(KEY_CLOCK_24H, true)
        set(value) = sp.edit().putBoolean(KEY_CLOCK_24H, value).apply()

    var clockGauge: Boolean
        get() = sp.getBoolean(KEY_CLOCK_GAUGE, false)
        set(value) = sp.edit().putBoolean(KEY_CLOCK_GAUGE, value).apply()

    var showSideloaded: Boolean
        get() = sp.getBoolean(KEY_SIDELOADED, false)
        set(value) = sp.edit().putBoolean(KEY_SIDELOADED, value).apply()

    var showSystemRow: Boolean
        get() = sp.getBoolean(KEY_SYSTEM_ROW, true)
        set(value) = sp.edit().putBoolean(KEY_SYSTEM_ROW, value).apply()

    var showStats: Boolean
        get() = sp.getBoolean(KEY_STATS, true)
        set(value) = sp.edit().putBoolean(KEY_STATS, value).apply()

    /** Percent of the Drive Mode ground laid over your wallpaper, for legibility. */
    var wallpaperDim: Int
        get() = sp.getInt(KEY_WALLPAPER_DIM, 55)
        set(value) = sp.edit().putInt(KEY_WALLPAPER_DIM, value.coerceIn(0, 85)).apply()

    var backdrop: Boolean
        get() = sp.getBoolean(KEY_BACKDROP, true)
        set(value) = sp.edit().putBoolean(KEY_BACKDROP, value).apply()

    var ignition: Boolean
        get() = sp.getBoolean(KEY_IGNITION, true)
        set(value) = sp.edit().putBoolean(KEY_IGNITION, value).apply()

    /** Minutes of inactivity before Parked mode dims the screen. 0 disables it. */
    var parkedMinutes: Int
        get() = sp.getInt(KEY_PARKED, 0)
        set(value) = sp.edit().putInt(KEY_PARKED, value).apply()

    var homePromptDismissed: Boolean
        get() = sp.getBoolean(KEY_HOME_PROMPT, false)
        set(value) = sp.edit().putBoolean(KEY_HOME_PROMPT, value).apply()

    /**
     * Favourites are ordered, not a set: the whole point of pinning is deciding
     * what sits leftmost. Stored newline-joined because SharedPreferences has no
     * ordered-collection type.
     */
    var favourites: List<String>
        get() = sp.getString(KEY_FAVOURITES, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = sp.edit().putString(KEY_FAVOURITES, value.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, HashSet(value)).apply()

    fun toggleFavourite(key: String) {
        val next = favourites.toMutableList()
        if (!next.remove(key)) next.add(key)
        favourites = next
    }

    fun moveFavourite(key: String, delta: Int) {
        val next = favourites.toMutableList()
        val from = next.indexOf(key)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, next.size - 1)
        if (to == from) return
        next.removeAt(from)
        next.add(to, key)
        favourites = next
    }

    fun toggleHidden(key: String) {
        val next = HashSet(hidden)
        if (!next.remove(key)) next.add(key)
        hidden = next
    }

    fun clearHidden() {
        hidden = emptySet()
    }

    private companion object {
        const val KEY_SCHEMA = "schema"
        const val KEY_DRIVE_MODE = "drive_mode"
        const val KEY_CLOCK_24H = "clock_24h"
        const val KEY_CLOCK_GAUGE = "clock_gauge"
        const val KEY_SIDELOADED = "show_sideloaded"
        const val KEY_SYSTEM_ROW = "show_system_row"
        const val KEY_STATS = "show_stats"
        const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        const val KEY_BACKDROP = "backdrop"
        const val KEY_IGNITION = "ignition"
        const val KEY_PARKED = "parked_minutes"
        const val KEY_HOME_PROMPT = "home_prompt_dismissed"
        const val KEY_FAVOURITES = "favourites_ordered"
        const val KEY_HIDDEN = "hidden_apps"
    }
}
