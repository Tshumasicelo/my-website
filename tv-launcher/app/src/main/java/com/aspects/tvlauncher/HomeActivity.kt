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
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.roundToInt

class HomeActivity : Activity() {

    /** Runs before any view exists, which is the only point the size can be set. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(DisplaySize.wrap(base))
    }


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
    private lateinit var clockView: OdometerClockView
    private lateinit var clockGauge: ClockGaugeView
    private lateinit var dateView: TextView
    private lateinit var statusView: TextView
    private lateinit var emptyView: TextView
    private lateinit var gaugeRam: GaugeView
    private lateinit var gaugeDisk: GaugeView
    private lateinit var gaugeNet: GaugeView
    private lateinit var lamps: LinearLayout
    private lateinit var bloom: View
    private lateinit var ambient: AmbientGlowView
    private lateinit var gearRail: GearRailView
    private lateinit var weatherRow: View
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherText: TextView

    /** The size the views were measured at; a change means we must lay out again. */
    private var builtAtSize = DisplaySize.LARGE
    private lateinit var parked: View
    private lateinit var parkedClock: OdometerClockView
    private lateinit var parkedGroup: View
    private lateinit var parkedWeather: TextView
    private lateinit var homePrompt: LinearLayout

    private lateinit var favRow: Row
    private lateinit var drivenRow: Row
    private lateinit var tvRow: Row
    private lateinit var allRow: Row
    private lateinit var sysRow: Row
    private val rows: List<Row> get() = listOf(favRow, drivenRow, tvRow, allRow, sysRow)

    /** Shared by every row; only the row holding focus draws it. */
    private val reflection = Reflection(CardAdapter.FOCUS_SCALE)

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

    /** The focused app's colour, once its artwork has been read; 0 until then. */
    private var focusedTint = 0
    private var builtFont: Boolean? = null

    private var weather: Weather.Now? = null
    private var weatherFor: Weather.Place? = null
    private var weatherBusy = false
    private var weatherTriedAt = 0L

    private var driftX = 1f
    private var driftY = 0.62f

    private val backdropRunnable = Runnable {
        val entry = pendingBackdrop ?: return@Runnable
        art.load(entry) { showBackdrop(entry, it) }
    }

    /** Ten seconds, not one: fewer wakeups matters on a passively cooled 1 GB box. */
    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            maybeRefreshWeather()
            applyModeIfChanged()
            if (prefs.showStats) refreshStats()
            ui.postDelayed(this, TICK_MS)
        }
    }

    private val parkRunnable = Runnable { enterParked() }

    /**
     * Creeps the parked clock along, a density-pixel at a time, bouncing off
     * the safe area. Twice a second is plenty for something meant to move too
     * slowly to watch, and it keeps the screen idle in between - which is the
     * point of a screen that has faded out.
     */
    private val driftRunnable = object : Runnable {
        override fun run() {
            if (parked.visibility != View.VISIBLE || !prefs.driftingClock) return
            val step = ThemeKit.dp(this@HomeActivity, 1f).toFloat()
            val roomX = ((parked.width - parkedGroup.width) / 2f -
                resources.getDimension(R.dimen.overscan_h)).coerceAtLeast(0f)
            val roomY = ((parked.height - parkedGroup.height) / 2f -
                resources.getDimension(R.dimen.overscan_v)).coerceAtLeast(0f)
            var x = parkedGroup.translationX + driftX * step
            var y = parkedGroup.translationY + driftY * step
            if (x > roomX || x < -roomX) {
                driftX = -driftX
                x = x.coerceIn(-roomX, roomX)
            }
            if (y > roomY || y < -roomY) {
                driftY = -driftY
                y = y.coerceIn(-roomY, roomY)
            }
            parkedGroup.translationX = x
            parkedGroup.translationY = y
            ui.postDelayed(this, DRIFT_MS)
        }
    }

    private val packageWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshApps()
    }

    // ------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        builtAtSize = prefs.displaySize
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
        ambient = findViewById(R.id.ambient)
        gearRail = findViewById(R.id.gearRail)
        weatherRow = findViewById(R.id.weather)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherText = findViewById(R.id.weatherText)
        parked = findViewById(R.id.parked)
        parkedClock = findViewById(R.id.parkedClock)
        parkedGroup = findViewById(R.id.parkedGroup)
        parkedWeather = findViewById(R.id.parkedWeather)
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

        weatherFor = prefs.weatherPlace
        weather = prefs.lastWeather

        applyModeIfChanged()
        applyShowroom()
        if (prefs.ignition) startIgnition()
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        // A measured view will not re-measure at a new density, so the whole
        // activity has to be built again rather than merely refreshed.
        if (builtAtSize != prefs.displaySize) {
            recreate()
            return
        }
        applyShowroom()
        maybeRefreshWeather()
        applyModeIfChanged()
        renderWeather()
        applyWallpaper()
        refreshApps()
        updateClock()
        updateHomePrompt()
        // Coming back from an app does not fire a focus change, so the card that
        // still holds focus has to be lit again by hand.
        focusedCard?.takeIf { it.hasFocus() }?.let { applyGlow(it, focusedTint) }
        ui.removeCallbacks(ticker)
        ui.post(ticker)
        registerPackageWatcher()
        schedulePark()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(parkRunnable)
        snapOutOfParked()
        // Nobody can see it behind an app, so stop it asking for frames.
        EdgeLight.detach(focusedCard)
        runCatching { unregisterReceiver(packageWatcher) }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        gaugeRam.stopAnimation()
        gaugeDisk.stopAnimation()
        gaugeNet.stopAnimation()
        EdgeLight.detach(focusedCard)
        reflection.clear()
        focusedCard = null
        icons.shutdown()
        art.shutdown()
        io.shutdownNow()
    }

    /**
     * The key that wakes the screen should only wake the screen. Without this it
     * would also land on whatever card had focus when the launcher faded, so
     * pressing a direction to bring the rows back could open an app instead.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Key events can arrive before onCreate has bound the views, and
        // touching a lateinit field before then throws.
        val parkedNow = ::parked.isInitialized && parked.visibility == View.VISIBLE
        if (parkedNow && event.action == KeyEvent.ACTION_DOWN && wakesTheScreen(event.keyCode)) {
            leaveParked()
            schedulePark()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Volume and power belong to the television, not to us. */
    private fun wakesTheScreen(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER -> false
        else -> true
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
        prefs.effectiveModeIndex(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), usableWeather())

    private fun applyModeIfChanged() {
        val wanted = currentModeIndex()
        if (builtMode == wanted) return
        builtMode = wanted
        mode = DriveMode.byIndex(builtMode)

        background.mode = mode
        window.setBackgroundDrawable(GradientDrawable().apply { setColor(mode.ground) })

        clockView.textColor = mode.ink
        parkedClock.textColor = mode.ink
        parkedWeather.setTextColor(mode.ink)
        weatherText.setTextColor(mode.dim)
        weatherIcon.setColorFilter(mode.glow)
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
        // A veil, not a sheet. The old value hid the very picture this screen
        // exists to show; this is just enough to keep the clock legible over a
        // bright photo.
        parked.setBackgroundColor(ThemeKit.withAlpha(mode.ground, 0.22f))

        rows.forEach { it.title.setTextColor(mode.dim) }

        gearRail.glow = mode.glow
        gearRail.ink = mode.ink
        gearRail.dim = mode.dim
        gearRail.ground = mode.ground

        // The reflection only makes sense on a wet road, and the edge glow
        // has to pick up the new mode's colour on the next focus.
        if (mode != DriveMode.WET) reflection.clear()
        ambient.off()

        // Card backgrounds are baked when a holder is created, so the adapters
        // have to be rebuilt for a new Drive Mode to reach recycled views.
        clearBackdrop()
        wallpaperStamp = -1L
        favRow.rebuild(); drivenRow.rebuild(); tvRow.rebuild(); allRow.rebuild(); sysRow.rebuild()
    }

    // -------------------------------------------------------------- ignition

    private fun startIgnition() {
        igniting = true
        gearRail.alpha = 0f
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
            gearRail.animate().alpha(1f).setDuration(560L).start()
        }, 680L)
        ui.postDelayed({ if (igniting) finishIgnition() }, 1500L)
    }

    private fun finishIgnition() {
        igniting = false
        content.animate().cancel()
        bloom.animate().cancel()
        gearRail.animate().cancel()
        gearRail.alpha = 1f
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

    /**
     * Fades the launcher away and leaves the picture.
     *
     * Everything that belongs to the launcher goes: the rows, the cluster, the
     * focused app's artwork. What stays is the wallpaper, or the painted Drive
     * Mode background when no wallpaper is set, with the clock over it.
     *
     * The fade out is slow and the fade back is quick on purpose. Leaving should
     * feel like the screen settling; returning should feel immediate, because by
     * then you have already pressed a key and are waiting on it.
     */
    private fun enterParked() {
        if (isFinishing || isDestroyed) return
        if (parked.visibility == View.VISIBLE) return
        updateClock()

        parked.animate().cancel()
        parked.visibility = View.VISIBLE
        parked.alpha = 0f
        parked.animate()
            .alpha(1f)
            .setDuration(FADE_OUT_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        fadeTo(scroller, 0f, FADE_OUT_MS)
        fadeTo(backdrop, 0f, FADE_OUT_MS)
        fadeTo(scrim, 0f, FADE_OUT_MS)
        fadeTo(gearRail, 0f, FADE_OUT_MS)
        fadeTo(ambient, 0f, FADE_OUT_MS)
        EdgeLight.detach(focusedCard)

        setBrightness(PARKED_BRIGHTNESS)
        ui.removeCallbacks(driftRunnable)
        if (prefs.driftingClock) ui.postDelayed(driftRunnable, DRIFT_MS)
    }

    private fun leaveParked() {
        if (parked.visibility != View.VISIBLE) return

        parked.animate().cancel()
        parked.animate()
            .alpha(0f)
            .setDuration(FADE_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { parked.visibility = View.GONE }
            .start()

        fadeTo(scroller, 1f, FADE_IN_MS)
        fadeTo(gearRail, 1f, FADE_IN_MS)
        fadeTo(ambient, 1f, FADE_IN_MS)
        ui.removeCallbacks(driftRunnable)
        focusedCard?.takeIf { it.hasFocus() }?.let { applyGlow(it, focusedTint) }
        // The artwork belongs to whichever card holds focus, so it is put back
        // to its resting state rather than to whatever alpha it happened to
        // have when the screen faded.
        clearBackdrop()

        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    /**
     * Snaps out of the faded state instead of animating. onPause cannot rely on
     * an animation finishing, and a fade left half-run would have the launcher
     * come back invisible.
     */
    private fun snapOutOfParked() {
        if (!::parked.isInitialized) return
        parked.animate().cancel()
        parked.visibility = View.GONE
        parked.alpha = 0f
        scroller.animate().cancel()
        scroller.alpha = 1f
        gearRail.animate().cancel()
        gearRail.alpha = 1f
        ambient.animate().cancel()
        ambient.alpha = 1f
        ui.removeCallbacks(driftRunnable)
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun fadeTo(view: View, alpha: Float, duration: Long) {
        view.animate().cancel()
        view.animate()
            .alpha(alpha)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setBrightness(value: Float) {
        runCatching {
            val attrs = window.attributes
            attrs.screenBrightness = value
            window.attributes = attrs
        }
    }

    // ------------------------------------------------------------------ rows

    private inner class Row(val title: TextView, val list: RecyclerView) {

        private var adapter: CardAdapter? = null
        private var items: List<CardItem> = emptyList()

        init {
            list.layoutManager =
                LinearLayoutManager(this@HomeActivity, RecyclerView.HORIZONTAL, false)
            list.setHasFixedSize(true)
            list.itemAnimator = null
            list.setItemViewCacheSize(6)
            list.addItemDecoration(reflection)
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
     *
     * The artwork is fetched whenever any effect needs the app's colour - the
     * edge light and the ambient glow as much as the backdrop - and only shown
     * full-screen when the backdrop itself is switched on.
     */
    private fun onCardFocus(item: CardItem, view: View) {
        focusedCard = view
        focusedKey = item.app?.key
        focusedTint = item.app?.let { art.cachedTint(it) } ?: 0
        applyGlow(view, focusedTint)
        // An app whose colour is still loading keeps the current glow rather
        // than dipping to the neutral wash and back, which would pulse the
        // screen edges while you hold a direction.
        if (focusedTint != 0 || item.app == null) glowAmbient(focusedTint)
        engageGear(view)
        // Posted: the focus listener runs before the card refreshes its drawable
        // state, so a snapshot taken now would miss the focus ring.
        if (prefs.reflection && mode == DriveMode.WET) {
            ui.post { if (focusedCard === view) reflection.show(view) }
        } else {
            reflection.clear()
        }

        ui.removeCallbacks(backdropRunnable)
        val app = item.app
        val wantsArt = prefs.backdrop || prefs.ambientGlow || prefs.edgeLight
        if (!wantsArt || app == null) {
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
            // No artwork at all: settle the edges on the neutral wash.
            if (focusedKey == entry.key) glowAmbient(0)
            clearBackdrop()
            return
        }

        // The artwork arrives after focus moved, so everything coloured by the
        // app is repainted now that its colour is finally known - including
        // "no colour", or the edges would keep the previous app's.
        if (focusedKey == entry.key) {
            if (found.tint != 0) {
                focusedTint = found.tint
                focusedCard?.let { applyGlow(it, found.tint) }
            }
            glowAmbient(found.tint)
        }
        if (!prefs.backdrop) return

        backdrop.setImageBitmap(found.bitmap)

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

    /**
     * Repaints one card's ring, bloom and edge light. Only ever the focused
     * card, so the fresh drawable costs nothing worth measuring.
     */
    private fun applyGlow(view: View, tint: Int) {
        val glow = if (tint == 0) mode.glow else tint
        val lit = prefs.edgeLight
        view.background = ThemeKit.cardSelector(this, mode, 14f, glow, if (lit) 0.35f else 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = glow
            view.outlineAmbientShadowColor = glow
        }
        // Artwork can land after focus has moved off the cards altogether, onto
        // the home prompt say; a light on a card nobody is on would never stop.
        if (lit && view.hasFocus()) EdgeLight.attach(view, glow) else EdgeLight.detach(view)
        if (reflection.active) ui.post { reflection.refresh() }
    }

    /** Strong for an app with a colour of its own, a faint wash of the mode otherwise. */
    private fun glowAmbient(tint: Int) {
        if (!prefs.ambientGlow) {
            ambient.off()
            return
        }
        ambient.glowTo(
            if (tint == 0) ThemeKit.withAlpha(mode.glow, 0.14f)
            else ThemeKit.withAlpha(tint, 0.42f)
        )
    }

    // -------------------------------------------------------------- gear rail

    /** Rows that are showing, in the order they appear on screen. */
    private fun visibleRows(): List<Row> = rows
        .filter { it.list.visibility == View.VISIBLE }
        .sortedBy { content.indexOfChild(it.list) }

    /** One letter per showing row, taken from its title so renamed rows follow. */
    private fun updateGears() {
        gearRail.setGears(
            visibleRows().map {
                it.title.text.toString().trim().take(1).uppercase(Locale.getDefault())
            }
        )
        focusedCard?.let { engageGear(it) }
    }

    private fun engageGear(card: View) {
        val list = card.parent as? RecyclerView ?: return
        val index = visibleRows().indexOfFirst { it.list === list }
        if (index >= 0) gearRail.engage(index)
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
        updateGears()
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
            TILE_DISPLAY -> launch(Intent(this, WallpaperActivity::class.java))
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
        clockView.setText(time)
        parkedClock.setText(time)
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

    // --------------------------------------------------------------- showroom

    /** Everything Settings can switch on or off, re-read on every return here. */
    private fun applyShowroom() {
        background.living = prefs.livingBackground
        clockView.rolling = prefs.rollingClock
        parkedClock.rolling = prefs.rollingClock

        // The rail lives in the overscan gutter; the rows step aside a little so
        // a focused card, scaled up, never slides underneath it.
        val rail = prefs.gearRail
        gearRail.visibility = if (rail) View.VISIBLE else View.GONE
        val inset = if (rail) ThemeKit.dp(this, RAIL_INSET_DP) else 0
        if (content.paddingStart != inset) {
            content.setPaddingRelative(
                inset, content.paddingTop, content.paddingEnd, content.paddingBottom
            )
        }

        if (!prefs.ambientGlow) ambient.off()
        if (!prefs.reflection) reflection.clear()
        if (!prefs.edgeLight) EdgeLight.detach(focusedCard)

        applyTypography()
        updateGears()
    }

    /**
     * Orbitron and Rajdhani run smaller than Roboto at the same point size -
     * Orbitron wider, Rajdhani shorter - so each switch carries its own sizes
     * rather than reusing the system font's.
     */
    private fun applyTypography() {
        val on = prefs.dashFont
        if (builtFont == on) return
        builtFont = on

        val display = Fonts.display(this, on)
        val label = Fonts.label(this, on)
        val labelBold = Fonts.labelBold(this, on)

        clockView.setTypeface(display)
        parkedClock.setTypeface(display)
        clockView.setTextSizePx(sp(if (on) 50f else 56f))
        parkedClock.setTextSizePx(sp(if (on) 86f else 96f))

        dateView.typeface = label
        dateView.textSize = if (on) 17f else 15f
        statusView.typeface = label
        statusView.textSize = if (on) 14f else 12f
        weatherText.typeface = label
        weatherText.textSize = if (on) 16f else 14f
        parkedWeather.typeface = label
        parkedWeather.textSize = if (on) 21f else 18f

        rows.forEach {
            it.title.typeface = labelBold
            it.title.textSize = if (on) 16f else 14f
        }
        listOf(gaugeRam, gaugeDisk, gaugeNet).forEach { it.face = if (on) labelBold else null }
        gearRail.setTypeface(labelBold)
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    // ---------------------------------------------------------------- weather

    /** The reading, unless it is so old it would be lying about the sky. */
    private fun usableWeather(): Weather.Now? = weather?.takeIf {
        prefs.weatherPlace != null &&
            System.currentTimeMillis() - it.fetchedAt < WEATHER_STALE_MS
    }

    private fun maybeRefreshWeather() {
        val place = prefs.weatherPlace
        if (place != weatherFor) {
            // A new place, or none: whatever we had belongs to somewhere else.
            weatherFor = place
            weather = null
            weatherTriedAt = 0L
            renderWeather()
        }
        if (place == null) return

        val now = System.currentTimeMillis()
        val have = weather
        if (have != null && now - have.fetchedAt < Weather.REFRESH_MS) return
        if (weatherBusy || now - weatherTriedAt < WEATHER_RETRY_MS) return
        weatherBusy = true
        weatherTriedAt = now

        io.execute {
            val fresh = Weather.current(place)
            ui.post {
                weatherBusy = false
                if (isFinishing || isDestroyed || fresh == null) return@post
                if (prefs.weatherPlace != place) return@post
                weather = fresh
                prefs.lastWeather = fresh
                renderWeather()
                // Adaptive mode may want the rain now.
                applyModeIfChanged()
            }
        }
    }

    private fun renderWeather() {
        val now = usableWeather()
        val place = prefs.weatherPlace
        if (now == null || place == null) {
            weatherRow.visibility = View.GONE
            parkedWeather.visibility = View.GONE
            return
        }
        val line = getString(
            R.string.weather_line,
            now.temperatureC.roundToInt(),
            getString(Weather.labelRes(now.kind)),
            place.name.substringBefore(',').uppercase(Locale.getDefault())
        )
        weatherIcon.setImageResource(Weather.glyphRes(now))
        weatherIcon.setColorFilter(mode.glow)
        weatherText.text = line
        weatherRow.visibility = View.VISIBLE
        parkedWeather.text = line
        parkedWeather.visibility = View.VISIBLE
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

        /** How far the rows step right to clear the gear rail. */
        const val RAIL_INSET_DP = 12f

        const val DRIFT_MS = 500L

        /** After a failed fetch; the regular refresh is Weather.REFRESH_MS. */
        const val WEATHER_RETRY_MS = 5L * 60L * 1000L

        /** Older than this and the chip hides rather than show yesterday's sky. */
        const val WEATHER_STALE_MS = 3L * 60L * 60L * 1000L

        /** Slow going, quick coming back: leaving should settle, returning should not. */
        const val FADE_OUT_MS = 1600L
        const val FADE_IN_MS = 380L

        /**
         * Dim, but nowhere near off. The point of this screen is that the
         * picture stays visible, so the backlight only drops far enough to
         * spare the panel and the electricity bill.
         */
        const val PARKED_BRIGHTNESS = 0.45f

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
