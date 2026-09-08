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
    private var accent = Accents.at(0).color

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        accent = Accents.at(prefs.accent).color
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.glow).background = ThemeKit.headerGlow(accent)
        container = findViewById(R.id.container)
        findViewById<TextView>(R.id.about).text = about()

        build()
        container.post { container.getChildAt(0)?.requestFocus() }
    }

    private fun build() {
        container.removeAllViews()

        // Cycling through accents on OK beats a nested swatch strip: one button,
        // no second focus axis to get lost in with a D-pad.
        addRow(
            R.drawable.ic_star,
            getString(R.string.accent_colour),
            Accents.at(prefs.accent).name
        ) {
            prefs.accent = Accents.next(prefs.accent)
            recreate()
        }

        addToggle(R.drawable.ic_clock, getString(R.string.clock_24h), prefs.clock24h) {
            prefs.clock24h = it
        }
        addToggle(R.drawable.ic_apps, getString(R.string.show_sideloaded), prefs.showSideloaded) {
            prefs.showSideloaded = it
        }
        addToggle(R.drawable.ic_tune, getString(R.string.show_system_row), prefs.showSystemRow) {
            prefs.showSystemRow = it
        }
        addToggle(R.drawable.ic_memory, getString(R.string.show_stats), prefs.showStats) {
            prefs.showStats = it
        }

        addRow(R.drawable.ic_home, getString(R.string.set_default_home), "") {
            openHomePicker()
        }
    }

    private fun addRow(glyphRes: Int, title: String, value: String, onClick: () -> Unit) {
        val row = inflateRow(glyphRes, title, value)
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    private fun addToggle(
        glyphRes: Int,
        title: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        var state = initial
        val row = inflateRow(glyphRes, title, stateLabel(state))
        val value = row.findViewById<TextView>(R.id.value)
        value.setTextColor(if (state) accent else MUTED)
        row.setOnClickListener {
            state = !state
            onChange(state)
            value.text = stateLabel(state)
            value.setTextColor(if (state) accent else MUTED)
        }
        container.addView(row)
    }

    private fun inflateRow(glyphRes: Int, title: String, value: String): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting, container, false)
        row.background = ThemeKit.cardSelector(this, accent, 16f)
        row.outlineProvider = ThemeKit.roundedOutline(this, 16f)
        row.clipToOutline = true

        val glyph = row.findViewById<ImageView>(R.id.glyph)
        glyph.setImageResource(glyphRes)
        glyph.setColorFilter(accent)

        row.findViewById<TextView>(R.id.title).text = title

        val valueView = row.findViewById<TextView>(R.id.value)
        valueView.text = value
        valueView.setTextColor(accent)
        return row
    }

    private fun stateLabel(on: Boolean): String =
        getString(if (on) R.string.state_on else R.string.state_off)

    /** Opens the system's home-app picker so you can make this the real home screen. */
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
        append('\n')
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append('\n')
        append("Android ").append(Build.VERSION.RELEASE)
        append("  (API ").append(Build.VERSION.SDK_INT).append(')')
        append('\n')
        append("Build ").append(Build.DISPLAY)
    }

    private companion object {
        const val MUTED = 0xFF8B93A2.toInt()
    }
}
