package com.aspects.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconLoader

    private lateinit var scroller: ScrollView
    private lateinit var glow: View
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var chips: LinearLayout
    private lateinit var emptyView: TextView

    private lateinit var favRow: Row
    private lateinit var tvRow: Row
    private lateinit var allRow: Row
    private lateinit var sysRow: Row

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var accent = Accents.at(0).color
    private var builtAccent = -1

    /** Ten seconds, not one: fewer wakeups matters on a passively cooled 1 GB box. */
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            if (prefs.showStats) refreshStats() else chips.removeAllViews()
            ui.postDelayed(this, TICK_MS)
        }
    }

    private val packageWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        icons = IconLoader(this)
        setContentView(R.layout.activity_home)

        scroller = findViewById(R.id.scroller)
        glow = findViewById(R.id.glow)
        clockView = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        chips = findViewById(R.id.chips)
        emptyView = findViewById(R.id.empty)

        favRow = Row(findViewById(R.id.titleFav), findViewById(R.id.rowFav))
        tvRow = Row(findViewById(R.id.titleTv), findViewById(R.id.rowTv))
        allRow = Row(findViewById(R.id.titleAll), findViewById(R.id.rowAll))
        sysRow = Row(findViewById(R.id.titleSys), findViewById(R.id.rowSys))
    }

    override fun onResume() {
        super.onResume()
        applyAccentIfChanged()
        refreshApps()
        updateClock()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
        registerPackageWatcher()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        runCatching { unregisterReceiver(packageWatcher) }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        icons.shutdown()
        io.shutdownNow()
    }

    /** A home screen has nowhere to go Back to, so Back just returns you to the top. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        scroller.smoothScrollTo(0, 0)
    }

    // ---------------------------------------------------------------- theming

    private fun applyAccentIfChanged() {
        if (builtAccent == prefs.accent) return
        builtAccent = prefs.accent
        accent = Accents.at(builtAccent).color
        glow.background = ThemeKit.headerGlow(accent)
        // Card backgrounds are baked when a holder is created, so the adapters
        // have to be rebuilt for a new accent to reach already-recycled views.
        favRow.rebuild()
        tvRow.rebuild()
        allRow.rebuild()
        sysRow.rebuild()
    }

    // ------------------------------------------------------------------ rows

    private inner class Row(private val title: TextView, private val list: RecyclerView) {

        private var adapter: CardAdapter? = null
        private var items: List<CardItem> = emptyList()

        init {
            list.layoutManager =
                LinearLayoutManager(this@HomeActivity, RecyclerView.HORIZONTAL, false)
            list.setHasFixedSize(true)
            list.itemAnimator = null
            list.setItemViewCacheSize(6)
        }

        fun rebuild() {
            val next = CardAdapter(
                this@HomeActivity,
                icons,
                accent,
                this@HomeActivity::onCardClick,
                this@HomeActivity::onCardLongClick
            )
            adapter = next
            list.adapter = next
            next.submit(items)
            applyVisibility()
        }

        fun submit(next: List<CardItem>) {
            items = next
            adapter?.submit(next)
            applyVisibility()
        }

        private fun applyVisibility() {
            val visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            title.visibility = visibility
            list.visibility = visibility
        }
    }

    private fun refreshApps() {
        io.execute {
            val catalog = runCatching { AppRepository.load(this) }.getOrNull() ?: return@execute
            ui.post { if (!isFinishing && !isDestroyed) render(catalog) }
        }
    }

    private fun render(catalog: AppCatalog) {
        val favourites = prefs.favourites
        val everything = catalog.tv + catalog.other

        favRow.submit(everything.filter { favourites.contains(it.key) }.map { card(it) })
        tvRow.submit(catalog.tv.map { card(it) })
        allRow.submit(
            if (prefs.showSideloaded) catalog.other.map { card(it) } else emptyList()
        )
        sysRow.submit(if (prefs.showSystemRow) systemTiles() else emptyList())

        emptyView.visibility = if (everything.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Real TV apps get a wide 16:9 banner card; sideloaded apps get an icon tile. */
    private fun card(entry: AppEntry): CardItem =
        CardItem(entry.key, entry.label, entry, R.drawable.ic_app_placeholder, entry.isTvApp)

    private fun systemTiles(): List<CardItem> = listOf(
        tile(TILE_THEME, R.string.tile_launcher, R.drawable.ic_tune),
        tile(TILE_HOME, R.string.tile_home, R.drawable.ic_home),
        tile(TILE_SETTINGS, R.string.tile_settings, R.drawable.ic_settings),
        tile(TILE_NETWORK, R.string.tile_network, R.drawable.ic_signal),
        tile(TILE_DISPLAY, R.string.tile_display, R.drawable.ic_display),
        tile(TILE_APPS, R.string.tile_apps, R.drawable.ic_apps),
        tile(TILE_STORAGE, R.string.tile_storage, R.drawable.ic_storage),
        tile(TILE_DATETIME, R.string.tile_datetime, R.drawable.ic_clock),
        tile(TILE_DEVELOPER, R.string.tile_developer, R.drawable.ic_code)
    )

    private fun tile(id: String, labelRes: Int, glyphRes: Int): CardItem =
        CardItem(id, getString(labelRes), null, glyphRes, false)

    // --------------------------------------------------------------- actions

    private fun onCardClick(item: CardItem) {
        val app = item.app
        if (app != null) {
            launch(AppRepository.launchIntent(app))
            return
        }
        when (item.id) {
            TILE_THEME -> launch(Intent(this, SettingsActivity::class.java))
            TILE_HOME -> launchSettings(Settings.ACTION_HOME_SETTINGS)
            TILE_SETTINGS -> launchSettings(Settings.ACTION_SETTINGS)
            TILE_NETWORK -> launchSettings(Settings.ACTION_WIFI_SETTINGS)
            TILE_DISPLAY -> launchSettings(Settings.ACTION_DISPLAY_SETTINGS)
            TILE_APPS -> launchSettings(Settings.ACTION_APPLICATION_SETTINGS)
            TILE_STORAGE -> launchSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            TILE_DATETIME -> launchSettings(Settings.ACTION_DATE_SETTINGS)
            TILE_DEVELOPER -> launchSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
    }

    private fun onCardLongClick(item: CardItem): Boolean {
        val app = item.app ?: return false
        val pinned = prefs.favourites.contains(app.key)
        val actions = arrayOf(
            getString(R.string.action_open),
            getString(if (pinned) R.string.action_unpin else R.string.action_pin),
            getString(R.string.action_info),
            getString(R.string.action_uninstall)
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> launch(AppRepository.launchIntent(app))
                    1 -> {
                        prefs.toggleFavourite(app.key)
                        refreshApps()
                    }
                    2 -> launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    3 -> launch(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .show()
        return true
    }

    /**
     * Never uses resolveActivity() first: on Android 11 that is filtered by package
     * visibility and would report the Settings app as missing. Starting the intent
     * is not filtered, so we simply try it and fall back.
     */
    private fun launchSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (primary: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (fallback: Exception) {
                toast()
            }
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (failed: Exception) {
            toast()
        }
    }

    private fun toast() =
        Toast.makeText(this, getString(R.string.cant_open), Toast.LENGTH_SHORT).show()

    // ----------------------------------------------------------- header data

    private fun updateClock() {
        val now = Date()
        val pattern = if (prefs.clock24h) "HH:mm" else "h:mm a"
        clockView.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())
    }

    private fun refreshStats() {
        io.execute {
            val stats = runCatching { SystemStats.read(this) }.getOrNull() ?: return@execute
            ui.post { if (!isFinishing && !isDestroyed) renderChips(stats) }
        }
    }

    private fun renderChips(stats: Stats) {
        chips.removeAllViews()
        if (!prefs.showStats) return

        addChip(R.drawable.ic_signal, stats.ip?.let { "${stats.network}  $it" } ?: stats.network)
        if (stats.ramTotalMb > 0L) {
            addChip(R.drawable.ic_memory, "${stats.ramUsedMb} / ${stats.ramTotalMb} MB")
        }
        if (stats.storageTotalGb > 0.0) {
            addChip(
                R.drawable.ic_storage,
                String.format(Locale.US, "%.1f GB free", stats.storageFreeGb)
            )
        }
        stats.cpuTempC?.let {
            addChip(R.drawable.ic_thermo, String.format(Locale.US, "%.0f°C", it))
        }
        addChip(R.drawable.ic_clock, "up " + stats.uptime)
    }

    private fun addChip(glyphRes: Int, value: String) {
        val padH = ThemeKit.dp(this, 14f)
        val padV = ThemeKit.dp(this, 8f)

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = ThemeKit.chip(this)
        pill.setPadding(padH, padV, padH, padV)
        val pillParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        pillParams.marginStart = ThemeKit.dp(this, 10f)
        pill.layoutParams = pillParams

        val glyphSize = ThemeKit.dp(this, 15f)
        val glyph = ImageView(this)
        glyph.setImageResource(glyphRes)
        glyph.setColorFilter(accent)
        glyph.layoutParams = LinearLayout.LayoutParams(glyphSize, glyphSize)

        val label = TextView(this)
        label.setText(value)
        label.setTextColor(0xFFCBD2DE.toInt())
        label.textSize = 13f
        val labelParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        labelParams.marginStart = ThemeKit.dp(this, 8f)
        label.layoutParams = labelParams

        pill.addView(glyph)
        pill.addView(label)
        chips.addView(pill)
    }

    private fun registerPackageWatcher() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        runCatching {
            // Android 14 requires an explicit export flag on every runtime receiver.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageWatcher, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(packageWatcher, filter)
            }
        }
    }

    private companion object {
        const val TICK_MS = 10_000L

        const val TILE_THEME = "tile.theme"
        const val TILE_HOME = "tile.home"
        const val TILE_SETTINGS = "tile.settings"
        const val TILE_NETWORK = "tile.network"
        const val TILE_DISPLAY = "tile.display"
        const val TILE_APPS = "tile.apps"
        const val TILE_STORAGE = "tile.storage"
        const val TILE_DATETIME = "tile.datetime"
        const val TILE_DEVELOPER = "tile.developer"
    }
}
