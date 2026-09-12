package com.aspects.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * ASPECTS TV forward in its place. It reads nothing:
 * canRetrieveWindowContent is false, so all it ever learns is which package
 * opened a window.
 *
 * Displacing windows is a blunt instrument, so it is fenced in four ways: only
 * genuine launchers are eligible, our own deliberate launches are exempt, a
 * breaker trips if it ever fires repeatedly, and it can be switched off from
 * inside the app without reaching system Settings.
 */
class HomeGuardService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var lastTakeover = 0L
    private var lastRefresh = 0L

    private var windowStart = 0L
    private var countInWindow = 0
    private var pausedUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshLaunchers()
        lastRefresh = SystemClock.elapsedRealtime()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
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

        // We just sent the user somewhere on purpose - Settings, an app, the home
        // picker. Dragging them straight back would make those screens unusable.
        if (now < exemptUntil) return

        // One HOME press emits several window events; without this we would fight
        // the OEM launcher in a loop.
        if (now - lastTakeover < DEBOUNCE_MS) return
        if (!breakerAllows(now)) return

        lastTakeover = now
        runCatching {
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        // Skip the system transition; the OEM launcher is already
                        // on screen, so an extra animation only lengthens the flash.
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            )
        }
    }

    override fun onInterrupt() = Unit

    /**
     * If takeovers ever come in a burst, something on this firmware is fighting
     * back. Stand down for a minute rather than trap the user in a loop they
     * cannot escape with a remote.
     */
    private fun breakerAllows(now: Long): Boolean {
        if (now < pausedUntil) return false
        if (now - windowStart > BREAKER_WINDOW_MS) {
            windowStart = now
            countInWindow = 0
        }
        countInWindow++
        if (countInWindow > BREAKER_MAX) {
            pausedUntil = now + BREAKER_PAUSE_MS
            countInWindow = 0
            return false
        }
        return true
    }

    /**
     * Packages holding a real home-screen activity.
     *
     * The priority filter is the important part. Android's Settings app ships a
     * hidden FallbackHome activity - the blank screen shown during boot before a
     * launcher is ready - registered for CATEGORY_HOME at priority -1000. Without
     * this filter the Settings package counts as a launcher, and every attempt to
     * open Settings gets bounced straight back to the home screen.
     */
    @Suppress("DEPRECATION")
    private fun refreshLaunchers() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPackages = runCatching {
            packageManager.queryIntentActivities(home, 0)
                .asSequence()
                .filter { it.priority >= 0 }
                .filterNot { it.activityInfo?.name.orEmpty().contains("FallbackHome", true) }
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it != packageName }
                .filterNot { NEVER_DISPLACE.contains(it) }
                .toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val DEBOUNCE_MS = 1200L
        private const val REFRESH_MS = 300_000L
        private const val BREAKER_WINDOW_MS = 10_000L
        private const val BREAKER_MAX = 4
        private const val BREAKER_PAUSE_MS = 60_000L

        /**
         * Never displaced even if they somehow claim CATEGORY_HOME. Losing access
         * to Settings or the package installer would leave no way back.
         */
        private val NEVER_DISPLACE = setOf(
            "com.android.settings",
            "com.android.tv.settings",
            "com.google.android.tv.settings",
            "com.android.systemui",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        @Volatile
        private var instance: HomeGuardService? = null

        @Volatile
        private var exemptUntil = 0L

        /**
         * Called immediately before the launcher deliberately opens something.
         * Window events during this grace period are left alone.
         */
        fun expectExternalLaunch(millis: Long = 5000L) {
            exemptUntil = SystemClock.elapsedRealtime() + millis
        }

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

        /**
         * The escape hatch. A service can switch itself off, so the takeover can
         * always be stopped from inside ASPECTS TV even if system Settings has
         * somehow become unreachable.
         */
        fun stopTakeover(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            val service = instance ?: return false
            return runCatching {
                service.disableSelf()
                instance = null
                true
            }.getOrDefault(false)
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
