package com.aspects.tvlauncher

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

data class Stats(
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val network: String,
    val ip: String?,
    val cpuTempC: Double?,
    val uptime: String,
    /** Download throughput since the previous sample, in megabits per second. */
    val rxMbps: Double
)

/**
 * Everything here is best-effort. Budget TV boxes expose a different subset of
 * /sys on every chipset, so each reader is wrapped and simply disappears from
 * the header when the box will not answer.
 */
object SystemStats {

    private var lastRxBytes = -1L
    private var lastRxAt = 0L

    fun read(ctx: Context): Stats {
        val (usedMb, totalMb) = ram(ctx)
        val (freeGb, totalGb) = storage()
        return Stats(
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            storageFreeGb = freeGb,
            storageTotalGb = totalGb,
            network = network(ctx),
            ip = localIp(),
            cpuTempC = cpuTemp(),
            uptime = uptime(),
            rxMbps = throughput()
        )
    }

    /**
     * Throughput is a rate, and the counter is a total since boot, so it can only
     * be measured against the previous sample. The first call after launch has
     * nothing to compare against and reports zero rather than a spike.
     */
    private fun throughput(): Double {
        val bytes = runCatching { TrafficStats.getTotalRxBytes() }.getOrDefault(-1L)
        if (bytes < 0L) return 0.0

        val now = SystemClock.elapsedRealtime()
        val previousBytes = lastRxBytes
        val previousAt = lastRxAt
        lastRxBytes = bytes
        lastRxAt = now

        if (previousBytes < 0L) return 0.0
        val seconds = (now - previousAt) / 1000.0
        if (seconds <= 0.0) return 0.0
        val delta = (bytes - previousBytes).coerceAtLeast(0L)
        return delta * 8.0 / 1_000_000.0 / seconds
    }

    private fun ram(ctx: Context): Pair<Long, Long> = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem / (1024L * 1024L)
        val available = info.availMem / (1024L * 1024L)
        (total - available) to total
    }.getOrDefault(0L to 0L)

    private fun storage(): Pair<Double, Double> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val gb = 1024.0 * 1024.0 * 1024.0
        val free = stat.availableBlocksLong.toDouble() * stat.blockSizeLong.toDouble() / gb
        val total = stat.blockCountLong.toDouble() * stat.blockSizeLong.toDouble() / gb
        free to total
    }.getOrDefault(0.0 to 0.0)

    @Suppress("DEPRECATION")
    private fun network(ctx: Context): String = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val active = cm.activeNetwork ?: return@runCatching "Offline"
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                else -> "Online"
            }
        } else {
            val info = cm.activeNetworkInfo
            if (info == null || !info.isConnected) "Offline" else info.typeName ?: "Online"
        }
    }.getOrDefault("Unknown")

    /**
     * Read straight off the network interfaces rather than via WifiManager: since
     * Android 10 the SSID needs a location permission, and an IP address tells you
     * more when you are trying to reach the TV over ADB anyway.
     */
    private fun localIp(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        for (nif in interfaces) {
            if (nif.isLoopback || !nif.isUp) continue
            for (address in nif.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return@runCatching address.hostAddress
                }
            }
        }
        null
    }.getOrNull()

    private fun cpuTemp(): Double? {
        for (zone in 0..9) {
            val raw = runCatching {
                File("/sys/class/thermal/thermal_zone$zone/temp").readText().trim()
            }.getOrNull() ?: continue
            val value = raw.toDoubleOrNull() ?: continue
            // Some kernels report milli-degrees, some plain degrees.
            val celsius = if (value > 1000) value / 1000.0 else value
            if (celsius in 10.0..120.0) return celsius
        }
        return null
    }

    private fun uptime(): String {
        val seconds = SystemClock.elapsedRealtime() / 1000L
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
