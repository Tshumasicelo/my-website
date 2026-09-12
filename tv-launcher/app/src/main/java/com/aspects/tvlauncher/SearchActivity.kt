package com.aspects.tvlauncher

import android.content.Context
import android.app.Activity
import android.app.ActivityOptions
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Type-to-filter across everything installed, including the apps hidden from the
 * home screen - hiding is about tidying the rows, not about losing the app.
 */
class SearchActivity : Activity() {

    /** Runs before any view exists, which is the only point the size can be set. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(DisplaySize.wrap(base))
    }


    private lateinit var prefs: Prefs
    private lateinit var icons: IconLoader
    private lateinit var results: RecyclerView
    private lateinit var countView: TextView
    private lateinit var adapter: CardAdapter

    private val io = Executors.newSingleThreadExecutor()
    private var everything: List<AppEntry> = emptyList()
    private var mode = DriveMode.NIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        icons = IconLoader(this)
        mode = prefs.mode
        setContentView(R.layout.activity_search)

        findViewById<DriveBackgroundView>(R.id.background).mode = mode

        countView = findViewById(R.id.count)
        countView.setTextColor(mode.dim)

        val query = findViewById<EditText>(R.id.query)
        query.background = ThemeKit.cardSelector(this, mode, 12f)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        adapter = CardAdapter(this, icons, mode, ::open, { false })
        results = findViewById(R.id.results)
        results.layoutManager = GridLayoutManager(this, 5)
        results.itemAnimator = null
        results.setHasFixedSize(true)
        results.adapter = adapter

        load()
    }

    override fun onDestroy() {
        super.onDestroy()
        icons.shutdown()
        io.shutdownNow()
    }

    private fun load() {
        io.execute {
            val catalog = runCatching { AppRepository.load(this) }.getOrNull() ?: return@execute
            val all = (catalog.tv + catalog.other).sortedBy { it.label.lowercase(Locale.getDefault()) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                everything = all
                filter("")
            }
        }
    }

    private fun filter(term: String) {
        val needle = term.trim().lowercase(Locale.getDefault())
        val matches = if (needle.isEmpty()) everything
        else everything.filter { it.label.lowercase(Locale.getDefault()).contains(needle) }

        adapter.submit(
            matches.map {
                CardItem(it.key, it.label, it, R.drawable.ic_app_placeholder, false)
            }
        )
        countView.text = resources.getQuantityString(
            R.plurals.search_results, matches.size, matches.size
        )
    }

    private fun open(item: CardItem, from: View) {
        val app = item.app ?: return
        HomeGuardService.expectExternalLaunch()
        val options = runCatching {
            ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height).toBundle()
        }.getOrNull()
        try {
            startActivity(AppRepository.launchIntent(app), options)
            finish()
        } catch (failed: Exception) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
