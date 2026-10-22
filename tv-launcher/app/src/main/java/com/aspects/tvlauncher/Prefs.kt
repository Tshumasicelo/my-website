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

    var adaptiveMode: Boolean
        get() = sp.getBoolean(KEY_ADAPTIVE, false)
        set(value) = sp.edit().putBoolean(KEY_ADAPTIVE, value).apply()

    /**
     * Which Drive Mode is actually in force. Golden Hour takes the sunrise;
     * Night Drive holds the brightest hours because its black ground survives
     * window glare; Sunset Run takes the evening; Wet Neon owns the dark.
     *
     * The weather outranks the clock: if it is raining outside, the screen is
     * wet too, and snow brings the Aurora out whatever the hour.
     */
    fun effectiveModeIndex(hourOfDay: Int, weather: Weather.Now? = null): Int {
        if (!adaptiveMode) return driveMode
        when (weather?.kind) {
            Weather.Kind.RAIN, Weather.Kind.STORM -> return DriveMode.WET.ordinal
            Weather.Kind.SNOW -> return DriveMode.AURORA.ordinal
            else -> {}
        }
        return when (hourOfDay) {
            in 6..8 -> DriveMode.GOLDEN.ordinal
            in 9..15 -> DriveMode.NIGHT.ordinal
            in 16..18 -> DriveMode.SUNSET.ordinal
            else -> DriveMode.WET.ordinal
        }
    }

    // ---------------------------------------------------------- trip computer

    fun recordLaunch(key: String) {
        sp.edit().putInt(KEY_LAUNCHES + key, launchCount(key) + 1).apply()
    }

    fun launchCount(key: String): Int = sp.getInt(KEY_LAUNCHES + key, 0)

    /** Most-launched first. The Most Driven row builds itself from this. */
    fun topLaunched(limit: Int): List<String> = sp.all.entries
        .asSequence()
        .filter { it.key.startsWith(KEY_LAUNCHES) && it.value is Int }
        .sortedByDescending { it.value as Int }
        .take(limit)
        .map { it.key.removePrefix(KEY_LAUNCHES) }
        .toList()

    fun clearLaunches() {
        val editor = sp.edit()
        sp.all.keys.filter { it.startsWith(KEY_LAUNCHES) }.forEach { editor.remove(it) }
        editor.apply()
    }

    // ------------------------------------------------------------------- rows

    /** Ids in display order. Unknown ids are ignored; missing ones are appended. */
    var rowOrder: List<String>
        get() {
            val stored = sp.getString(KEY_ROW_ORDER, null)
                ?.split(',')
                ?.filter { it.isNotBlank() && DEFAULT_ROWS.contains(it) }
                ?: emptyList()
            return stored + DEFAULT_ROWS.filterNot { stored.contains(it) }
        }
        set(value) = sp.edit().putString(KEY_ROW_ORDER, value.joinToString(",")).apply()

    fun moveRow(id: String, delta: Int) {
        val next = rowOrder.toMutableList()
        val from = next.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, next.size - 1)
        if (to == from) return
        next.removeAt(from)
        next.add(to, id)
        rowOrder = next
    }

    fun rowName(id: String, fallback: String): String =
        sp.getString(KEY_ROW_NAME + id, null)?.takeIf { it.isNotBlank() } ?: fallback

    fun setRowName(id: String, name: String?) {
        val editor = sp.edit()
        if (name.isNullOrBlank()) editor.remove(KEY_ROW_NAME + id)
        else editor.putString(KEY_ROW_NAME + id, name.trim())
        editor.apply()
    }

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

    /** Which of the three display sizes the launcher draws at. */
    var displaySize: Int
        get() = sp.getInt(KEY_DISPLAY_SIZE, DisplaySize.LARGE)
        set(value) = sp.edit().putInt(KEY_DISPLAY_SIZE, value).apply()

    /** Minutes of inactivity before the launcher fades to the wallpaper. 0 disables it. */
    var parkedMinutes: Int
        get() = sp.getInt(KEY_PARKED, 0)
        set(value) = sp.edit().putInt(KEY_PARKED, value).apply()

    // ------------------------------------------------------------- showroom

    /** The P-R-N-D strip down the left edge that shows which row you are in. */
    var gearRail: Boolean
        get() = sp.getBoolean(KEY_GEAR_RAIL, true)
        set(value) = sp.edit().putBoolean(KEY_GEAR_RAIL, value).apply()

    /** A light that laps the border of whichever card has focus. */
    var edgeLight: Boolean
        get() = sp.getBoolean(KEY_EDGE_LIGHT, true)
        set(value) = sp.edit().putBoolean(KEY_EDGE_LIGHT, value).apply()

    /** The focused card mirrored on the road surface. Wet Neon only. */
    var reflection: Boolean
        get() = sp.getBoolean(KEY_REFLECTION, true)
        set(value) = sp.edit().putBoolean(KEY_REFLECTION, value).apply()

    /** The screen edges glow in the focused app's colour. */
    var ambientGlow: Boolean
        get() = sp.getBoolean(KEY_AMBIENT, true)
        set(value) = sp.edit().putBoolean(KEY_AMBIENT, value).apply()

    var dashFont: Boolean
        get() = sp.getBoolean(KEY_DASH_FONT, true)
        set(value) = sp.edit().putBoolean(KEY_DASH_FONT, value).apply()

    /** Clock digits roll over like an odometer instead of cutting. */
    var rollingClock: Boolean
        get() = sp.getBoolean(KEY_ROLLING_CLOCK, true)
        set(value) = sp.edit().putBoolean(KEY_ROLLING_CLOCK, value).apply()

    /**
     * Off by default: motion means redrawing the screen many times a second,
     * which this passively cooled box feels. Everything else here is still.
     */
    var livingBackground: Boolean
        get() = sp.getBoolean(KEY_LIVING, false)
        set(value) = sp.edit().putBoolean(KEY_LIVING, value).apply()

    /** The faded-to-wallpaper clock creeps around so it never burns in. */
    var driftingClock: Boolean
        get() = sp.getBoolean(KEY_DRIFT, true)
        set(value) = sp.edit().putBoolean(KEY_DRIFT, value).apply()

    // ---------------------------------------------------------------- weather

    /** The place weather is fetched for, or null when weather is switched off. */
    var weatherPlace: Weather.Place?
        get() {
            val name = sp.getString(KEY_WEATHER_NAME, null) ?: return null
            if (!sp.contains(KEY_WEATHER_LAT)) return null
            return Weather.Place(
                name,
                sp.getFloat(KEY_WEATHER_LAT, 0f).toDouble(),
                sp.getFloat(KEY_WEATHER_LON, 0f).toDouble()
            )
        }
        set(value) {
            val editor = sp.edit()
                .remove(KEY_WEATHER_CODE).remove(KEY_WEATHER_TEMP)
                .remove(KEY_WEATHER_DAY).remove(KEY_WEATHER_AT)
            if (value == null) {
                editor.remove(KEY_WEATHER_NAME).remove(KEY_WEATHER_LAT).remove(KEY_WEATHER_LON)
            } else {
                editor.putString(KEY_WEATHER_NAME, value.name)
                    .putFloat(KEY_WEATHER_LAT, value.latitude.toFloat())
                    .putFloat(KEY_WEATHER_LON, value.longitude.toFloat())
            }
            editor.apply()
        }

    /**
     * The last reading, kept so the chip is right the instant the launcher
     * starts instead of blank until the network answers.
     */
    var lastWeather: Weather.Now?
        get() {
            if (!sp.contains(KEY_WEATHER_AT)) return null
            return Weather.Now(
                sp.getInt(KEY_WEATHER_CODE, 0),
                sp.getFloat(KEY_WEATHER_TEMP, 0f).toDouble(),
                sp.getBoolean(KEY_WEATHER_DAY, true),
                sp.getLong(KEY_WEATHER_AT, 0L)
            )
        }
        set(value) {
            if (value == null) {
                sp.edit().remove(KEY_WEATHER_AT).apply()
                return
            }
            sp.edit()
                .putInt(KEY_WEATHER_CODE, value.code)
                .putFloat(KEY_WEATHER_TEMP, value.temperatureC.toFloat())
                .putBoolean(KEY_WEATHER_DAY, value.isDay)
                .putLong(KEY_WEATHER_AT, value.fetchedAt)
                .apply()
        }

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

    companion object {
        const val ROW_FAVOURITES = "fav"
        const val ROW_DRIVEN = "driven"
        const val ROW_TV = "tv"
        const val ROW_ALL = "all"
        const val ROW_SYSTEM = "sys"

        val DEFAULT_ROWS = listOf(ROW_FAVOURITES, ROW_DRIVEN, ROW_TV, ROW_ALL, ROW_SYSTEM)

        const val KEY_SCHEMA = "schema"
        const val KEY_ADAPTIVE = "adaptive_mode"
        const val KEY_LAUNCHES = "launch."
        const val KEY_ROW_ORDER = "row_order"
        const val KEY_ROW_NAME = "row_name."
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
        const val KEY_DISPLAY_SIZE = "display_size"
        const val KEY_HOME_PROMPT = "home_prompt_dismissed"
        const val KEY_FAVOURITES = "favourites_ordered"
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_GEAR_RAIL = "gear_rail"
        const val KEY_EDGE_LIGHT = "edge_light"
        const val KEY_REFLECTION = "reflection"
        const val KEY_AMBIENT = "ambient_glow"
        const val KEY_DASH_FONT = "dash_font"
        const val KEY_ROLLING_CLOCK = "rolling_clock"
        const val KEY_LIVING = "living_background"
        const val KEY_DRIFT = "drifting_clock"
        const val KEY_WEATHER_NAME = "weather_name"
        const val KEY_WEATHER_LAT = "weather_lat"
        const val KEY_WEATHER_LON = "weather_lon"
        const val KEY_WEATHER_CODE = "weather_code"
        const val KEY_WEATHER_TEMP = "weather_temp"
        const val KEY_WEATHER_DAY = "weather_day"
        const val KEY_WEATHER_AT = "weather_at"
    }
}
