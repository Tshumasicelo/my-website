package com.aspects.tvlauncher

import android.content.Context
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Reorders and renames the home rows.
 *
 * Move up / move down rather than drag-and-drop: dragging needs a pointer, and
 * the only pointer here is a D-pad. Two presses beat a gesture nobody can make.
 */
class RowsActivity : Activity() {

    /** Runs before any view exists, which is the only point the size can be set. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(DisplaySize.wrap(base))
    }


    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private var mode = DriveMode.NIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        mode = DriveMode.byIndex(prefs.driveMode)
        setContentView(R.layout.activity_settings)

        findViewById<DriveBackgroundView>(R.id.background).mode = mode

        val heading = findViewById<TextView>(R.id.heading)
        heading.setTextColor(mode.ink)
        heading.text = getString(R.string.rows_title)

        val about = findViewById<TextView>(R.id.about)
        about.setTextColor(mode.dim)
        about.text = getString(R.string.rows_hint)

        container = findViewById(R.id.container)
        build()
        container.post { container.getChildAt(0)?.requestFocus() }
    }

    private fun build() {
        container.removeAllViews()
        val order = prefs.rowOrder
        order.forEachIndexed { index, id ->
            val fallback = defaultName(id)
            val name = prefs.rowName(id, fallback)
            val row = inflate(
                glyphFor(id),
                name,
                getString(R.string.rows_position, index + 1),
                if (name == fallback) null else getString(R.string.rows_renamed_from, fallback)
            )
            row.setOnClickListener { options(id, index, order.size) }
            container.addView(row)
        }
    }

    private fun options(id: String, index: Int, count: Int) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        if (index > 0) {
            labels.add(getString(R.string.rows_move_up))
            actions.add { prefs.moveRow(id, -1); build() }
        }
        if (index < count - 1) {
            labels.add(getString(R.string.rows_move_down))
            actions.add { prefs.moveRow(id, 1); build() }
        }
        labels.add(getString(R.string.rows_rename))
        actions.add { rename(id) }

        if (prefs.rowName(id, defaultName(id)) != defaultName(id)) {
            labels.add(getString(R.string.rows_reset_name))
            actions.add { prefs.setRowName(id, null); build() }
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(prefs.rowName(id, defaultName(id)))
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun rename(id: String) {
        val input = EditText(this)
        input.setText(prefs.rowName(id, defaultName(id)))
        input.setSingleLine()
        input.setTextColor(0xFFFFFFFF.toInt())
        val pad = ThemeKit.dp(this, 20f)
        input.setPadding(pad, pad, pad, pad)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.rows_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setRowName(id, input.text.toString())
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun inflate(glyphRes: Int, title: String, value: String, subtitle: String?): View {
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

    private fun defaultName(id: String): String = when (id) {
        Prefs.ROW_FAVOURITES -> getString(R.string.row_favourites)
        Prefs.ROW_DRIVEN -> getString(R.string.row_driven)
        Prefs.ROW_TV -> getString(R.string.row_tv_apps)
        Prefs.ROW_ALL -> getString(R.string.row_all_apps)
        else -> getString(R.string.row_system)
    }

    private fun glyphFor(id: String): Int = when (id) {
        Prefs.ROW_FAVOURITES -> R.drawable.ic_star
        Prefs.ROW_DRIVEN -> R.drawable.ic_signal
        Prefs.ROW_TV -> R.drawable.ic_display
        Prefs.ROW_ALL -> R.drawable.ic_apps
        else -> R.drawable.ic_tune
    }
}
