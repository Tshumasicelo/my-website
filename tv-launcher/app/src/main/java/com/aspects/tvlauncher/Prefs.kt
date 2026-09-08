package com.aspects.tvlauncher

import android.content.Context

/** Thin typed wrapper over SharedPreferences. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("aspects_tv", Context.MODE_PRIVATE)

    var accent: Int
        get() = sp.getInt(KEY_ACCENT, 0)
        set(value) = sp.edit().putInt(KEY_ACCENT, value).apply()

    var clock24h: Boolean
        get() = sp.getBoolean(KEY_CLOCK_24H, true)
        set(value) = sp.edit().putBoolean(KEY_CLOCK_24H, value).apply()

    var showSideloaded: Boolean
        get() = sp.getBoolean(KEY_SIDELOADED, true)
        set(value) = sp.edit().putBoolean(KEY_SIDELOADED, value).apply()

    var showSystemRow: Boolean
        get() = sp.getBoolean(KEY_SYSTEM_ROW, true)
        set(value) = sp.edit().putBoolean(KEY_SYSTEM_ROW, value).apply()

    var showStats: Boolean
        get() = sp.getBoolean(KEY_STATS, true)
        set(value) = sp.edit().putBoolean(KEY_STATS, value).apply()

    /** Always returns a copy: the set handed back by getStringSet must not be mutated. */
    var favourites: Set<String>
        get() = HashSet(sp.getStringSet(KEY_FAVOURITES, emptySet()) ?: emptySet())
        set(value) = sp.edit().putStringSet(KEY_FAVOURITES, HashSet(value)).apply()

    fun toggleFavourite(key: String) {
        val next = HashSet(favourites)
        if (!next.remove(key)) next.add(key)
        favourites = next
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_CLOCK_24H = "clock_24h"
        const val KEY_SIDELOADED = "show_sideloaded"
        const val KEY_SYSTEM_ROW = "show_system_row"
        const val KEY_STATS = "show_stats"
        const val KEY_FAVOURITES = "favourites"
    }
}
