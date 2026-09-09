package com.aspects.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Many TV firmwares wire the remote's HOME key directly to the bundled launcher
 * rather than routing it through Android's home-app resolution. On those sets,
 * choosing a different home screen in Settings genuinely changes nothing when you
 * press HOME - the key was never asking.
 *
 * This service watches for the OEM launcher arriving in the foreground and brings
 * ASPECTS TV forward in its place. It is the same workaround every third-party TV
 * launcher uses, and it is the only one available without root.
 *
 * It reads nothing. canRetrieveWindowContent is false, so all it ever learns is
 * which package just opened a window.
 */
class HomeGuardService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var lastTakeover = 0L
    private var lastRefresh = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshLaunchers()
        lastRefresh = SystemClock.elapsedRealtime()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRefresh > REFRESH_MS) {
            refreshLaunchers()
            lastRefresh = now
        }

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!launcherPackages.contains(pkg)) return

        // One HOME press can emit several window events; without this we would
        // fight the OEM launcher in a loop.
        if (now - lastTakeover < DEBOUNCE_MS) return
        lastTakeover = now

        runCatching {
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }

    override fun onInterrupt() = Unit

    /** Every package that claims CATEGORY_HOME, except ourselves. */
    @Suppress("DEPRECATION")
    private fun refreshLaunchers() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPackages = runCatching {
            packageManager.queryIntentActivities(home, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val DEBOUNCE_MS = 1200L
        private const val REFRESH_MS = 300_000L

        fun isEnabled(context: Context): Boolean = runCatching {
            val component = ComponentName(context, HomeGuardService::class.java)
            val long = component.flattenToString()
            val short = component.flattenToShortString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return@runCatching false
            enabled.split(':').any { it.equals(long, true) || it.equals(short, true) }
        }.getOrDefault(false)

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
