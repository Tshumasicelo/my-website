package com.aspects.tvlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconLoader
    private lateinit var art: Backdrop

    private lateinit var background: DriveBackgroundView
    private lateinit var wallpaperView: ImageView
    private lateinit var wallpaperDim: View
    private lateinit var backdrop: ImageView
    private lateinit var scrim: View
    private lateinit var scroller: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var clockView: TextView
    private lateinit var clockGauge: ClockGaugeView
    private lateinit var dateView: TextView
    private lateinit var statusView: TextView
    private lateinit var emptyView: TextView
    private lateinit var gaugeRam: GaugeView
    private lateinit var gaugeDisk: GaugeView
    private lateinit var gaugeNet: GaugeView
    private lateinit var lamps: LinearLayout
    private lateinit var bloom: View
    private lateinit var parked: View
    private lateinit var parkedClock: TextView
    private lateinit var homePrompt: LinearLayout

    private lateinit var favRow: Row
    private lateinit var drivenRow: Row
    private lateinit var tvRow: Row
    private lateinit var allRow: Row
    private lateinit var sysRow: Row

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var mode = DriveMode.NIGHT
    private var builtMode = -1
    private var igniting = false
    private var updateReady = false
    private var lastStats: Stats? = null
    private var pendingBackdrop: AppEntry? = null
    private var focusedCard: View? = null
    private var focusedKey: String? = null
    private var appliedRowOrder: List<String>? = null
    private var wallpaperBitmap: Bitmap? = null
    private var wallpaperStamp = -1L

    private val backdropRunnable = Runnable {
        val entry = pendingBackdrop ?: return@Runnable
        art.load(entry) { showBackdrop(entry, it) }
    }

    /** Ten seconds, not one: fewer wakeups matters on a passively cooled 1 GB box. */
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            applyModeIfChanged()
            if (prefs.showStats) refreshStats()
            ui.postDelayed(this, TICK_MS)
        }
    }

    private val parkRunnable = Runnable { enterParked() }

    private val packageWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    // ------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        icons = IconLoader(this)
        art = Backdrop(this)
        setContentView(R.layout.activity_home)

        background = findViewById(R.id.background)
        wallpaperView = findViewById(R.id.wallpaper)
        wallpaperDim = findViewById(R.id.wallpaperDim)
        backdrop = findViewById(R.id.backdrop)
        scrim = findViewById(R.id.scrim)
        scroller = findViewById(R.id.scroller)
        content = findViewById(R.id.content)
        clockView = findViewById(R.id.clock)
        clockGauge = findViewById(R.id.clockGauge)
        dateView = findViewById(R.id.date)
        statusView = findViewById(R.id.status)
        emptyView = findViewById(R.id.empty)
        gaugeRam = findViewById(R.id.gaugeRam)
        gaugeDisk = findViewById(R.id.gaugeDisk)
        gaugeNet = findViewById(R.id.gaugeNet)
        lamps = findViewById(R.id.lamps)
        bloom = findViewById(R.id.bloom)
        parked = findViewById(R.id.parked)
        parkedClock = findViewById(R.id.parkedClock)
        homePrompt = findViewById(R.id.homePrompt)

        favRow = Row(findViewById(R.id.titleFav), findViewById(R.id.rowFav))
        drivenRow = Row(findViewById(R.id.titleDriven), findViewById(R.id.rowDriven))
        tvRow = Row(findViewById(R.id.titleTv), findViewById(R.id.rowTv))
        allRow = Row(findViewById(R.id.titleAll), findViewById(R.id.rowAll))
        sysRow = Row(findViewById(R.id.titleSys), findViewById(R.id.rowSys))

        homePrompt.setOnClickListener { launchSettings(Settings.ACTION_HOME_SETTINGS) }
        homePrompt.setOnLongClickListener {
            prefs.homePromptDismissed = true
            homePrompt.visibility = View.GONE
            true
        }

        applyModeIfChanged()
        if (prefs.ignition) startIgnition()
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        applyModeIfChanged()
        applyWallpaper()
        refreshApps()
        updateClock()
        updateHomePrompt()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
        registerPackageWatcher()
        schedulePark()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(parkRunnable)
        leaveParked()
        runCatching { unregisterReceiver(packageWatcher) }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        gaugeRam.stopAnimation()
        gaugeDisk.stopAnimation()
        gaugeNet.stopAnimation()
        focusedCard = null
        icons.shutdown()
        art.shutdown()
        io.shutdownNow()
    }

    /** Fires on every key press and touch: our skip button and our idle reset. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (igniting) finishIgnition()
        if (parked.visibility == View.VISIBLE) leaveParked()
        schedulePark()
    }

    /** A home screen has nowhere to go Back to, so Back returns you to the top. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        scroller.smoothScrollTo(0, 0)
    }

    // ---------------------------------------------------------------- theming

    /** With Adaptive on this changes by itself, so the ticker re-checks it. */
    private fun currentModeIndex(): Int =
        prefs.effectiveModeIndex(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    private fun applyModeIfChanged() {
        val wanted = currentModeIndex()
        if (builtMode == wanted) return
        builtMode = wanted
        mode = DriveMode.byIndex(builtMode)

        background.mode = mode
        window.setBackgroundDrawable(GradientDrawable().apply { setColor(mode.ground) })

        clockView.setTextColor(mode.ink)
        parkedClock.setTextColor(mode.ink)
        dateView.setTextColor(mode.dim)
        statusView.setTextColor(mode.dim)
        emptyView.setTextColor(mode.dim)

        listOf(gaugeRam, gaugeDisk, gaugeNet).forEach {
            it.glow = mode.glow
            it.accent = mode.accent
            it.dim = mode.dim
        }
        clockGauge.glow = mode.glow
        clockGauge.accent = mode.accent
        clockGauge.dim = mode.dim

        gaugeRam.label = getString(R.string.gauge_ram)
        gaugeDisk.label = getString(R.string.gauge_disk)
        gaugeNet.label = getString(R.string.gauge_net)

        homePrompt.background = ThemeKit.panel(this, mode)
        parked.setBackgroundColor(ThemeKit.withAlpha(mode.ground, 0.94f))

        listOf(R.id.titleFav, R.id.titleTv, R.id.titleAll, R.id.titleSys).forEach {
            findViewById<TextView>(it).setTextColor(mode.dim)
        }

        // Card backgrounds are baked when a holder is created, so the adapters
        // have to be rebuilt for a new Drive Mode to reach recycled views.
        clearBackdrop()
        wallpaperStamp = -1L
        favRow.rebuild(); drivenRow.rebuild(); tvRow.rebuild(); allRow.rebuild(); sysRow.rebuild()
    }

    // -------------------------------------------------------------- ignition

    private fun startIgnition() {
        igniting = true
        content.alpha = 0f
        content.translationY = ThemeKit.dp(this, 22f).toFloat()

        bloom.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ThemeKit.withAlpha(mode.glow, 0f),
                ThemeKit.withAlpha(mode.glow, 0.10f),
                ThemeKit.withAlpha(mode.glow, 0.80f)
            )
        )
        bloom.alpha = 0f
        bloom.visibility = View.VISIBLE

        ui.postDelayed(
            { if (igniting) { gaugeRam.sweep(); gaugeDisk.sweep(); gaugeNet.sweep() } },
            220L
        )
        ui.postDelayed({
            if (!igniting) return@postDelayed
            bloom.animate().alpha(0.9f).setDuration(240L).withEndAction {
                bloom.animate().alpha(0f).setDuration(520L).start()
            }.start()
        }, 300L)
        ui.postDelayed({
            if (!igniting) return@postDelayed
            content.animate().alpha(1f).translationY(0f).setDuration(560L).start()
        }, 680L)
        ui.postDelayed({ if (igniting) finishIgnition() }, 1500L)
    }

    private fun finishIgnition() {
        igniting = false
        content.animate().cancel()
        bloom.animate().cancel()
        content.alpha = 1f
        content.translationY = 0f
        bloom.alpha = 0f
        bloom.visibility = View.GONE
    }

    // ------------------------------------------------------------ parked mode

    private fun schedulePark() {
        ui.removeCallbacks(parkRunnable)
        val minutes = prefs.parkedMinutes
        if (minutes > 0) ui.postDelayed(parkRunnable, minutes * 60_000L)
    }

    private fun enterParked() {
        if (isFinishing || isDestroyed) return
        updateClock()
        parked.visibility = View.VISIBLE
        parked.alpha = 0f
        parked.animate().alpha(1f).setDuration(900L).start()
        setBrightness(0.05f)
    }

    private fun leaveParked() {
        if (parked.visibility != View.VISIBLE) return
        parked.animate().cancel()
        parked.visibility = View.GONE
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun setBrightness(value: Float) {
        runCatching {
            val attrs = window.attributes
            attrs.screenBrightness = value
            window.attributes = attrs
        }
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
                this@HomeActivity, icons, mode,
                this@HomeActivity::onCardClick,
                this@HomeActivity::onCardLongClick,
                this@HomeActivity::onCardFocus
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

    /**
     * Focus moves faster than artwork loads while you hold a direction, so the
     * load is deferred briefly and superseded by whatever you land on.
     */
    private fun onCardFocus(item: CardItem, view: View) {
        focusedCard = view
        focusedKey = item.app?.key
        applyGlow(view, item.app?.let { art.cachedTint(it) } ?: 0)

        ui.removeCallbacks(backdropRunnable)
        val app = item.app
        if (!prefs.backdrop || app == null) {
            clearBackdrop()
            return
        }
        pendingBackdrop = app
        ui.postDelayed(backdropRunnable, BACKDROP_DELAY_MS)
    }

    private fun showBackdrop(entry: AppEntry, found: Backdrop.Art?) {
        if (isFinishing || isDestroyed) return
        if (pendingBackdrop?.key != entry.key) return
        if (found == null) {
            clearBackdrop()
            return
        }

        backdrop.setImageBitmap(found.bitmap)

        // The artwork arrives after focus moved, so the ring is repainted now
        // that its colour is finally known.
        if (found.tint != 0 && focusedKey == entry.key) {
            focusedCard?.let { applyGlow(it, found.tint) }
        }

        // Held well back from full strength: this sits under a clock and four
        // rows of text, and legibility beats spectacle on a screen read from
        // across a room.
        val tinted =
            if (found.tint == 0) mode.ground else ThemeKit.blend(mode.ground, found.tint, 0.34f)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ThemeKit.withAlpha(mode.ground, 0.74f),
                ThemeKit.withAlpha(tinted, 0.66f),
                ThemeKit.withAlpha(mode.ground, 0.93f)
            )
        )

        backdrop.animate().alpha(1f).setDuration(380L).start()
        scrim.animate().alpha(1f).setDuration(380L).start()
    }

    /** Repaints one card's ring and bloom. Only ever the focused card, so the
     *  fresh drawable costs nothing worth measuring. */
    private fun applyGlow(view: View, tint: Int) {
        val glow = if (tint == 0) mode.glow else tint
        view.background = ThemeKit.cardSelector(this, mode, 14f, glow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = glow
            view.outlineAmbientShadowColor = glow
        }
    }

    private fun clearBackdrop() {
        pendingBackdrop = null
        backdrop.animate().alpha(0f).setDuration(320L).start()
        scrim.animate().alpha(0f).setDuration(320L).start()
    }

    /**
     * Decoding is skipped unless the file actually changed - onResume runs every
     * time you come back from an app, and re-decoding a 1.8 MB bitmap each time
     * would be a needless stall on this hardware.
     */
    private fun applyWallpaper() {
        val stamp = Wallpaper.stamp(this)
        val dim = ThemeKit.withAlpha(mode.ground, prefs.wallpaperDim / 100f)

        if (stamp == wallpaperStamp) {
            if (wallpaperBitmap != null) wallpaperDim.setBackgroundColor(dim)
            return
        }
        wallpaperStamp = stamp

        val previous = wallpaperBitmap
        val next = if (stamp == 0L) null else Wallpaper.load(this)
        wallpaperBitmap = next

        if (next == null) {
            wallpaperView.setImageDrawable(null)
            wallpaperView.visibility = View.GONE
            wallpaperDim.visibility = View.GONE
        } else {
            wallpaperView.setImageBitmap(next)
            wallpaperView.visibility = View.VISIBLE
            wallpaperDim.setBackgroundColor(dim)
            wallpaperDim.visibility = View.VISIBLE
        }
        // Safe now: the view is already drawing the replacement.
        previous?.recycle()
    }

    private fun refreshApps() {
        io.execute {
            val catalog = runCatching { AppRepository.load(this) }.getOrNull() ?: return@execute
            ui.post { if (!isFinishing && !isDestroyed) render(catalog) }
        }
    }

    private fun render(catalog: AppCatalog) {
        val favourites = prefs.favourites
        val hidden = prefs.hidden
        val everything = catalog.tv + catalog.other
        val byKey = everything.associateBy { it.key }

        applyRowTitles()
        applyRowOrder()

        // favourites keep the order you pinned them in
        favRow.submit(favourites.mapNotNull { byKey[it] }.map { card(it) })

        // the trip computer: whatever you actually open most, ranked
        drivenRow.submit(
            prefs.topLaunched(DRIVEN_LIMIT)
                .mapNotNull { byKey[it] }
                .filterNot { hidden.contains(it.key) }
                .map { card(it) }
        )

        tvRow.submit(catalog.tv.filterNot { hidden.contains(it.key) }.map { card(it) })
        allRow.submit(
            if (prefs.showSideloaded) {
                // Firmware clutter is dropped here rather than in the repository,
                // so search can still reach these apps.
                catalog.other
                    .filterNot { it.preinstalled || hidden.contains(it.key) }
                    .map { card(it) }
            } else emptyList()
        )
        sysRow.submit(if (prefs.showSystemRow) systemTiles() else emptyList())

        emptyView.visibility = if (everything.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Real TV apps get a wide 16:9 banner card; sideloaded apps get an icon tile. */
    private fun applyRowTitles() {
        findViewById<TextView>(R.id.titleFav).text =
            prefs.rowName(Prefs.ROW_FAVOURITES, getString(R.string.row_favourites))
        findViewById<TextView>(R.id.titleDriven).text =
            prefs.rowName(Prefs.ROW_DRIVEN, getString(R.string.row_driven))
        findViewById<TextView>(R.id.titleTv).text =
            prefs.rowName(Prefs.ROW_TV, getString(R.string.row_tv_apps))
        findViewById<TextView>(R.id.titleAll).text =
            prefs.rowName(Prefs.ROW_ALL, getString(R.string.row_all_apps))
        findViewById<TextView>(R.id.titleSys).text =
            prefs.rowName(Prefs.ROW_SYSTEM, getString(R.string.row_system))
    }

    /**
     * Rows are declared in one fixed order in the layout and shuffled here, which
     * keeps the XML readable and means reordering never rebuilds an adapter.
     */
    private fun applyRowOrder() {
        val order = prefs.rowOrder
        if (order == appliedRowOrder) return
        appliedRowOrder = order

        val views = mapOf(
            Prefs.ROW_FAVOURITES to Pair<View, View>(
                findViewById(R.id.titleFav), findViewById(R.id.rowFav)
            ),
            Prefs.ROW_DRIVEN to Pair<View, View>(
                findViewById(R.id.titleDriven), findViewById(R.id.rowDriven)
            ),
            Prefs.ROW_TV to Pair<View, View>(
                findViewById(R.id.titleTv), findViewById(R.id.rowTv)
            ),
            Prefs.ROW_ALL to Pair<View, View>(
                findViewById(R.id.titleAll), findViewById(R.id.rowAll)
            ),
            Prefs.ROW_SYSTEM to Pair<View, View>(
                findViewById(R.id.titleSys), findViewById(R.id.rowSys)
            )
        )

        val indices = views.values
            .flatMap { listOf(content.indexOfChild(it.first), content.indexOfChild(it.second)) }
            .filter { it >= 0 }
        val base = indices.minOrNull() ?: return

        views.values.forEach { (title, list) ->
            content.removeView(title)
            content.removeView(list)
        }
        var at = base
        order.forEach { id ->
            val pair = views[id] ?: return@forEach
            content.addView(pair.first, at++)
            content.addView(pair.second, at++)
        }
    }

    private fun card(entry: AppEntry): CardItem =
        CardItem(entry.key, entry.label, entry, R.drawable.ic_app_placeholder, entry.isTvApp)

    private fun systemTiles(): List<CardItem> = listOf(
        tile(TILE_SEARCH, R.string.tile_search, R.drawable.ic_search),
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

    private fun onCardClick(item: CardItem, from: View) {
        val app = item.app
        if (app != null) {
            prefs.recordLaunch(app.key)
            launchApp(AppRepository.launchIntent(app), from)
            return
        }
        when (item.id) {
            TILE_SEARCH -> launch(Intent(this, SearchActivity::class.java))
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

    /** The app grows out of the tile you selected rather than cutting to it. */
    private fun launchApp(intent: Intent, from: View) {
        HomeGuardService.expectExternalLaunch()
        val options = runCatching {
            ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height).toBundle()
        }.getOrNull()
        try {
            startActivity(intent, options)
        } catch (failed: Exception) {
            launch(intent)
        }
    }

    private fun onCardLongClick(item: CardItem): Boolean {
        val app = item.app ?: return false
        val pinned = prefs.favourites.contains(app.key)

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        labels.add(getString(R.string.action_open))
        actions.add { launch(AppRepository.launchIntent(app)) }

        labels.add(getString(if (pinned) R.string.action_unpin else R.string.action_pin))
        actions.add { prefs.toggleFavourite(app.key); refreshApps() }

        if (pinned) {
            labels.add(getString(R.string.action_move_left))
            actions.add { prefs.moveFavourite(app.key, -1); refreshApps() }
            labels.add(getString(R.string.action_move_right))
            actions.add { prefs.moveFavourite(app.key, 1); refreshApps() }
        }

        labels.add(getString(R.string.action_hide))
        actions.add { prefs.toggleHidden(app.key); refreshApps() }

        labels.add(getString(R.string.action_info))
        actions.add {
            launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        labels.add(getString(R.string.action_uninstall))
        actions.add {
            launch(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
        return true
    }

    /**
     * Never calls resolveActivity() first: on Android 11 that is filtered by
     * package visibility and would report Settings as missing. Starting an
     * intent is not filtered, so we try it and fall back.
     */
    private fun launchSettings(action: String) {
        // Without this the guard service would see Settings open and bounce us
        // straight back here.
        HomeGuardService.expectExternalLaunch()
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
        HomeGuardService.expectExternalLaunch()
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
        val time = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        clockView.text = time
        parkedClock.text = time
        dateView.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())

        if (prefs.clockGauge) {
            clockGauge.visibility = View.VISIBLE
            clockGauge.setTime(Calendar.getInstance())
        } else {
            clockGauge.visibility = View.GONE
        }
    }

    private fun refreshStats() {
        io.execute {
            val stats = runCatching { SystemStats.read(this) }.getOrNull() ?: return@execute
            ui.post { if (!isFinishing && !isDestroyed) renderCluster(stats) }
        }
    }

    private fun renderCluster(stats: Stats) {
        lastStats = stats
        val show = prefs.showStats
        findViewById<View>(R.id.cluster).visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return

        if (stats.ramTotalMb > 0L) {
            gaugeRam.reading = stats.ramUsedMb.toString()
            gaugeRam.setValue(stats.ramUsedMb.toFloat() / stats.ramTotalMb.toFloat(), !igniting)
        }
        if (stats.storageTotalGb > 0.0) {
            val used = stats.storageTotalGb - stats.storageFreeGb
            gaugeDisk.reading = String.format(Locale.US, "%.0fG", stats.storageFreeGb)
            gaugeDisk.setValue((used / stats.storageTotalGb).toFloat(), !igniting)
        }

        // Throughput has no natural maximum, so the dial is scaled to something a
        // television actually reaches. Anything above it simply reads full.
        gaugeNet.reading = String.format(Locale.US, "%.1f", stats.rxMbps)
        gaugeNet.setValue((stats.rxMbps / NET_FULL_SCALE).toFloat(), !igniting)

        // Deliberately no IP address here. It is a home screen, not a diagnostic
        // readout, and the number is in Settings for when you actually want it.
        val parts = ArrayList<String>()
        parts.add(stats.network)
        stats.cpuTempC?.let { parts.add(String.format(Locale.US, "%.0f°C", it)) }
        parts.add(getString(R.string.up_for, stats.uptime))
        statusView.text = parts.joinToString("  ·  ")
        statusView.setTextColor(mode.dim)

        renderLamps(stats)
    }

    /**
     * Dash telltales. Dark until something needs saying, which is the whole point
     * of a warning lamp: you notice the one that lights, not the row of them.
     * Semantic colours are fixed rather than themed - red means the same thing in
     * every car ever built.
     */
    private fun renderLamps(stats: Stats) {
        val ramFraction =
            if (stats.ramTotalMb > 0L) stats.ramUsedMb.toFloat() / stats.ramTotalMb.toFloat()
            else 0f

        val states = listOf(
            Triple(R.drawable.ic_memory, LAMP_RED, ramFraction > 0.85f),
            Triple(
                R.drawable.ic_storage, LAMP_AMBER,
                stats.storageTotalGb > 0.0 && stats.storageFreeGb < 1.0
            ),
            Triple(R.drawable.ic_signal, LAMP_BLUE, stats.network == "Offline"),
            Triple(R.drawable.ic_thermo, LAMP_RED, (stats.cpuTempC ?: 0.0) > 75.0),
            Triple(R.drawable.ic_info, LAMP_GREEN, updateReady)
        )

        if (lamps.childCount != states.size) {
            lamps.removeAllViews()
            val size = ThemeKit.dp(this, 17f)
            for (state in states) {
                val lamp = ImageView(this)
                lamp.setImageResource(state.first)
                val params = LinearLayout.LayoutParams(size, size)
                params.marginStart = ThemeKit.dp(this, 12f)
                lamp.layoutParams = params
                lamps.addView(lamp)
            }
        }

        for (index in states.indices) {
            val lamp = lamps.getChildAt(index) as? ImageView ?: continue
            val lit = states[index].third
            lamp.setColorFilter(if (lit) states[index].second else mode.dim)
            lamp.alpha = if (lit) 1f else 0.16f
        }
    }

    // ------------------------------------------------------------ housekeeping

    private fun updateHomePrompt() {
        if (prefs.homePromptDismissed) {
            homePrompt.visibility = View.GONE
            return
        }
        // Two independent mechanisms can land HOME here: being the registered
        // default home app, or capturing the key with the guard service. Only one
        // has to be true. Asking about the default alone left the prompt showing
        // on TVs where the takeover was already doing the job.
        if (HomeGuardService.isEnabled(this)) {
            homePrompt.visibility = View.GONE
            return
        }
        val isDefault = runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == packageName
        }.getOrDefault(true)
        homePrompt.visibility = if (isDefault) View.GONE else View.VISIBLE
    }

    private fun checkForUpdate() {
        io.execute {
            val latest = UpdateChecker.latestVersionCode() ?: return@execute
            if (latest <= BuildConfig.VERSION_CODE) return@execute
            ui.post {
                if (isFinishing || isDestroyed) return@post
                updateReady = true
                lastStats?.let { renderCluster(it) }
            }
        }
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
        const val BACKDROP_DELAY_MS = 220L
        const val NET_FULL_SCALE = 25.0
        const val DRIVEN_LIMIT = 8

        // Warning-lamp colours are semantic, not themed.
        const val LAMP_RED = 0xFFFF3B30.toInt()
        const val LAMP_AMBER = 0xFFFFB020.toInt()
        const val LAMP_GREEN = 0xFF34C759.toInt()
        const val LAMP_BLUE = 0xFF4FA8FF.toInt()

        const val TILE_SEARCH = "tile.search"
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
