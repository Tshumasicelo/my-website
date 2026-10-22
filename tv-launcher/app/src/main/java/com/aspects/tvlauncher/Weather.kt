package com.aspects.tvlauncher

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Current conditions from Open-Meteo, which needs no account and no API key -
 * the only kind of weather service a sideloaded app in a public repo can use
 * without leaking a credential.
 *
 * Location is a place name you type once in Settings, never the TV's IP or
 * GPS: the launcher should not be guessing where your living room is.
 */
object Weather {

    data class Place(val name: String, val latitude: Double, val longitude: Double)

    /** @param code a WMO weather interpretation code, as Open-Meteo reports it. */
    data class Now(
        val code: Int,
        val temperatureC: Double,
        val isDay: Boolean,
        val fetchedAt: Long
    ) {
        val kind: Kind get() = kindOf(code)
    }

    /** Six looks are all a glance from the sofa needs; WMO defines about thirty. */
    enum class Kind { CLEAR, CLOUD, FOG, RAIN, SNOW, STORM }

    /** Weather changes slowly, and every request is a wakeup on a 1 GB box. */
    const val REFRESH_MS = 30L * 60L * 1000L

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"

    fun kindOf(code: Int): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2, 3 -> Kind.CLOUD
        45, 48 -> Kind.FOG
        in 51..67, in 80..82 -> Kind.RAIN
        in 71..77, 85, 86 -> Kind.SNOW
        in 95..99 -> Kind.STORM
        else -> Kind.CLOUD
    }

    fun labelRes(kind: Kind): Int = when (kind) {
        Kind.CLEAR -> R.string.weather_clear
        Kind.CLOUD -> R.string.weather_cloud
        Kind.FOG -> R.string.weather_fog
        Kind.RAIN -> R.string.weather_rain
        Kind.SNOW -> R.string.weather_snow
        Kind.STORM -> R.string.weather_storm
    }

    fun glyphRes(now: Now): Int = when (now.kind) {
        Kind.CLEAR -> if (now.isDay) R.drawable.ic_weather_sun else R.drawable.ic_weather_moon
        Kind.CLOUD, Kind.FOG -> R.drawable.ic_weather_cloud
        Kind.RAIN -> R.drawable.ic_weather_rain
        Kind.SNOW -> R.drawable.ic_weather_snow
        Kind.STORM -> R.drawable.ic_weather_storm
    }

    /** Blocking; call from a background thread. Null when offline or refused. */
    fun current(place: Place): Now? = runCatching {
        val url = String.format(
            Locale.US,
            "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
            FORECAST, place.latitude, place.longitude
        )
        val current = JSONObject(fetch(url) ?: return@runCatching null)
            .optJSONObject("current") ?: return@runCatching null
        Now(
            code = current.optInt("weather_code", 0),
            temperatureC = current.optDouble("temperature_2m", 0.0),
            isDay = current.optInt("is_day", 1) == 1,
            fetchedAt = System.currentTimeMillis()
        )
    }.getOrElse {
        Log.d("AspectsTV", "weather failed: " + it.message)
        null
    }

    /** Blocking. Turns "Durban" into a place with coordinates, or null. */
    fun find(query: String): Place? = runCatching {
        val url = GEOCODE + "?count=1&format=json&name=" + URLEncoder.encode(query.trim(), "UTF-8")
        val hit = JSONObject(fetch(url) ?: return@runCatching null)
            .optJSONArray("results")
            ?.optJSONObject(0) ?: return@runCatching null
        val name = listOfNotNull(
            hit.optString("name").takeIf { it.isNotBlank() },
            hit.optString("country_code").takeIf { it.isNotBlank() }
        ).joinToString(", ")
        Place(name, hit.getDouble("latitude"), hit.getDouble("longitude"))
    }.getOrElse {
        Log.d("AspectsTV", "geocode failed: " + it.message)
        null
    }

    private fun fetch(from: String): String? {
        val connection = (URL(from).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != 200) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
