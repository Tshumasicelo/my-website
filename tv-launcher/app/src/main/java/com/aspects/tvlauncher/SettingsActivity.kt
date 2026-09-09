package com.aspects.tvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private var mode = DriveMode.NIGHT
    private var guardEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        mode = prefs.mode
        setContentView(R.layout.activity_settings)

        findViewById<DriveBackgroundView>(R.id.background).mode = mode
        findViewById<TextView>(R.id.heading).setTextColor(mode.ink)

        val about = findViewById<TextView>(R.id.about)
        about.setTextColor(mode.dim)
        about.text = about()

        container = findViewById(R.id.container)
        guardEnabled = HomeGuardService.isEnabled(this)
        build()
        container.post { container.getChildAt(0)?.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just switched the service on in Accessibility.
        val now = HomeGuardService.isEnabled(this)
        if (now != guardEnabled) {
            guardEnabled = now
            build()
        }
    }

    private fun build() {
        container.removeAllViews()

        addRow(
            R.drawable.ic_home,
            getString(R.string.home_takeover),
            stateLabel(guardEnabled),
            getString(if (guardEnabled) R.string.home_takeover_on else R.string.home_takeover_off),
            guardEnabled
        ) {
            try {
                startActivity(HomeGuardService.settingsIntent())
            } catch (failed: Exception) {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
            }
        }

        // Cycling on OK beats a nested swatch strip: one button, no second focus
        // axis to get lost in with a D-pad.
        addRow(R.drawable.ic_star, getString(R.string.drive_mode), mode.label, mode.blurb) {
            prefs.driveMode = DriveMode.next(prefs.driveMode)
            recreate()
        }

        addToggle(R.drawable.ic_signal, getString(R.string.ignition), null, prefs.ignition) {
            prefs.ignition = it
        }
        addToggle(R.drawable.ic_clock, getString(R.string.clock_24h), null, prefs.clock24h) {
            prefs.clock24h = it
        }
        addToggle(R.drawable.ic_clock, getString(R.string.clock_gauge), null, prefs.clockGauge) {
            prefs.clockGauge = it
        }
        addToggle(R.drawable.ic_memory, getString(R.string.show_stats), null, prefs.showStats) {
            prefs.showStats = it
        }
        addToggle(R.drawable.ic_apps, getString(R.string.show_sideloaded), null, prefs.showSideloaded) {
            prefs.showSideloaded = it
        }
        addToggle(R.drawable.ic_tune, getString(R.string.show_system_row), null, prefs.showSystemRow) {
            prefs.showSystemRow = it
        }

        addCycler(
            R.drawable.ic_display, getString(R.string.parked_mode),
            getString(R.string.parked_hint), PARKED_STEPS
        ) { prefs.parkedMinutes }

        val hiddenCount = prefs.hidden.size
        addRow(
            R.drawable.ic_info,
            getString(R.string.hidden_apps),
            resources.getQuantityString(R.plurals.hidden_count, hiddenCount, hiddenCount),
            getString(R.string.hidden_hint)
        ) {
            if (hiddenCount == 0) {
                Toast.makeText(this, R.string.hidden_none, Toast.LENGTH_SHORT).show()
            } else {
                prefs.clearHidden()
                Toast.makeText(this, R.string.hidden_restored, Toast.LENGTH_SHORT).show()
                build()
            }
        }

        addRow(R.drawable.ic_home, getString(R.string.set_default_home), "", null) {
            openHomePicker()
        }
    }

    // ------------------------------------------------------------------ rows

    private fun addRow(
        glyphRes: Int,
        title: String,
        value: String,
        subtitle: String?,
        highlight: Boolean = true,
        onClick: () -> Unit
    ) {
        val row = inflateRow(glyphRes, title, value, subtitle)
        row.findViewById<TextView>(R.id.value)
            .setTextColor(if (highlight) mode.glow else mode.dim)
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    private fun addToggle(
        glyphRes: Int,
        title: String,
        subtitle: String?,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        var state = initial
        val row = inflateRow(glyphRes, title, stateLabel(state), subtitle)
        val value = row.findViewById<TextView>(R.id.value)
        value.setTextColor(if (state) mode.glow else mode.dim)
        row.setOnClickListener {
            state = !state
            onChange(state)
            value.text = stateLabel(state)
            value.setTextColor(if (state) mode.glow else mode.dim)
        }
        container.addView(row)
    }

    /** Steps through a fixed list of minute values on each press. */
    private fun addCycler(
        glyphRes: Int,
        title: String,
        subtitle: String?,
        steps: IntArray,
        current: () -> Int
    ) {
        val row = inflateRow(glyphRes, title, minutesLabel(current()), subtitle)
        val value = row.findViewById<TextView>(R.id.value)
        value.setTextColor(if (current() > 0) mode.glow else mode.dim)
        row.setOnClickListener {
            val at = steps.indexOf(current()).let { if (it < 0) 0 else it }
            val next = steps[(at + 1) % steps.size]
            prefs.parkedMinutes = next
            value.text = minutesLabel(next)
            value.setTextColor(if (next > 0) mode.glow else mode.dim)
        }
        container.addView(row)
    }

    private fun inflateRow(
        glyphRes: Int,
        title: String,
        value: String,
        subtitle: String?
    ): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting, container, false)
        row.background = ThemeKit.cardSelector(this, mode, 16f)
        row.outlineProvider = ThemeKit.roundedOutline(this, 16f)
        row.clipToOutline = true

        val glyph = row.findViewById<ImageView>(R.id.glyph)
        glyph.setImageResource(glyphRes)
        glyph.setColorFilter(mode.glow)

        val titleView = row.findViewById<TextView>(R.id.title)
        titleView.text = title
        titleView.setTextColor(mode.ink)

        val subtitleView = row.findViewById<TextView>(R.id.subtitle)
        if (subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
            subtitleView.setTextColor(mode.dim)
        }

        val valueView = row.findViewById<TextView>(R.id.value)
        valueView.text = value
        valueView.setTextColor(mode.glow)
        return row
    }

    private fun stateLabel(on: Boolean): String =
        getString(if (on) R.string.state_on else R.string.state_off)

    private fun minutesLabel(minutes: Int): String =
        if (minutes <= 0) getString(R.string.state_off)
        else getString(R.string.minutes, minutes)

    /** Opens the system home-app picker; we can only ever ask, never switch. */
    private fun openHomePicker() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (primary: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (fallback: Exception) {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun about(): String = buildString {
        append(getString(R.string.app_name)).append("  v").append(BuildConfig.VERSION_NAME)
        append("  (build ").append(BuildConfig.VERSION_CODE).append(')')
        append('\n')
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append('\n')
        append("Android ").append(Build.VERSION.RELEASE)
        append("  (API ").append(Build.VERSION.SDK_INT).append(')')
        append('\n')
        append("Build ").append(Build.DISPLAY)
    }

    private companion object {
        val PARKED_STEPS = intArrayOf(0, 15, 30, 60, 90)
    }
}
