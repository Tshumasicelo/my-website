package com.aspects.tvlauncher

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks the tv-latest release what the newest build number is. The workflow
 * publishes a tiny version.json alongside the APK precisely so the launcher can
 * answer this without parsing an APK or hitting a rate-limited API.
 */
object UpdateChecker {

    private const val ENDPOINT =
        "https://github.com/Tshumasicelo/my-website/releases/download/tv-latest/version.json"

    /** @return the published versionCode, or null if we could not find out. */
    fun latestVersionCode(): Int? = runCatching {
        val body = fetch(ENDPOINT, 3) ?: return@runCatching null
        JSONObject(body).optInt("versionCode", -1).takeIf { it > 0 }
    }.getOrElse {
        Log.d("AspectsTV", "update check failed: " + it.message)
        null
    }

    /** GitHub bounces release downloads to a CDN host, so follow redirects by hand. */
    private fun fetch(from: String, hops: Int): String? {
        if (hops <= 0) return null
        val connection = (URL(from).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location") ?: return null
                return fetch(next, hops - 1)
            }
            if (code != 200) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
