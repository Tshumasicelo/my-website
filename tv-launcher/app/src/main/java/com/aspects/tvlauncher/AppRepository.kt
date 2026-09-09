package com.aspects.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isTvApp: Boolean
) {
    /** Stable identity for favourites and the icon cache. */
    val key: String get() = "$packageName/$activityName"
}

data class AppCatalog(val tv: List<AppEntry>, val other: List<AppEntry>)

object AppRepository {

    /**
     * Two passes. LEANBACK_LAUNCHER finds proper TV apps (Netflix, YouTube...).
     * LAUNCHER then picks up everything sideloaded that the stock Android TV
     * home screen refuses to show at all - which is half the point of this app.
     */
    @Suppress("DEPRECATION")
    fun load(ctx: Context): AppCatalog {
        val pm = ctx.packageManager
        val self = ctx.packageName

        fun query(category: String): List<ResolveInfo> = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.getOrDefault(emptyList())

        val tvPackages = HashSet<String>()
        val tv = ArrayList<AppEntry>()
        for (info in query(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == self) continue
            if (!tvPackages.add(activity.packageName)) continue
            tv += toEntry(info, pm, true)
        }

        val seen = HashSet<String>()
        val other = ArrayList<AppEntry>()
        for (info in query(Intent.CATEGORY_LAUNCHER)) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == self) continue
            if (tvPackages.contains(activity.packageName)) continue
            if (!seen.add(activity.packageName)) continue
            if (isPreinstalled(activity.applicationInfo)) continue
            other += toEntry(info, pm, false)
        }

        val byLabel = compareBy<AppEntry> { it.label.lowercase() }
        return AppCatalog(tv.sortedWith(byLabel), other.sortedWith(byLabel))
    }

    /**
     * Firmware ships a pile of non-TV system apps carrying a LAUNCHER filter -
     * Clock, Disclaimer, Customization, TV Services - none of which belong on a
     * home screen. Anything the user actually sideloaded is never FLAG_SYSTEM.
     */
    private fun isPreinstalled(app: ApplicationInfo?): Boolean {
        if (app == null) return false
        val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return system && !updated
    }

    private fun toEntry(info: ResolveInfo, pm: PackageManager, isTvApp: Boolean): AppEntry {
        val activity = info.activityInfo
        val label = runCatching { info.loadLabel(pm).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: activity.packageName
        return AppEntry(activity.packageName, activity.name, label, isTvApp)
    }

    /** Explicit component, so the target's own intent filters never get in the way. */
    fun launchIntent(entry: AppEntry): Intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(entry.packageName, entry.activityName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}
