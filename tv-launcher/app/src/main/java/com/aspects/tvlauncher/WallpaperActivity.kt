package com.aspects.tvlauncher

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * Picks a background from whatever is on the TV.
 *
 * Deliberately not ACTION_OPEN_DOCUMENT: the system document picker is patchy on
 * television firmware and frequently impossible to drive with a D-pad. Querying
 * MediaStore ourselves means one grid, big focusable tiles, and it works the
 * same on every set. All external volumes are scanned, so a USB stick full of
 * wallpapers shows up alongside internal storage.
 */
class WallpaperActivity : Activity() {

    private data class Item(val uri: Uri?, val name: String)

    private lateinit var prefs: Prefs
    private lateinit var grid: RecyclerView
    private lateinit var hint: TextView
    private val adapter = Adapter()

    private val io = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())
    private var mode = DriveMode.NIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        mode = prefs.mode
        setContentView(R.layout.activity_wallpaper)

        findViewById<DriveBackgroundView>(R.id.background).mode = mode
        findViewById<TextView>(R.id.heading).setTextColor(mode.ink)
        hint = findViewById(R.id.hint)
        hint.setTextColor(mode.dim)

        grid = findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.itemAnimator = null
        grid.adapter = adapter

        if (hasPermission()) loadImages() else requestPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
        ui.removeCallbacksAndMessages(null)
    }

    // ----------------------------------------------------------- permission

    private fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasPermission(): Boolean =
        checkSelfPermission(permissionName()) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        hint.text = getString(R.string.wallpaper_permission)
        requestPermissions(arrayOf(permissionName()), REQUEST_READ)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadImages()
        } else {
            hint.text = getString(R.string.wallpaper_denied)
            adapter.submit(listOf(Item(null, getString(R.string.wallpaper_none))))
        }
    }

    // --------------------------------------------------------------- images

    private fun loadImages() {
        hint.text = getString(R.string.wallpaper_scanning)
        io.execute {
            val found = runCatching { query() }.getOrDefault(emptyList())
            ui.post {
                if (isFinishing || isDestroyed) return@post
                adapter.submit(listOf(Item(null, getString(R.string.wallpaper_none))) + found)
                hint.text = if (found.isEmpty()) {
                    getString(R.string.wallpaper_empty)
                } else {
                    resources.getQuantityString(R.plurals.wallpaper_found, found.size, found.size)
                }
            }
        }
    }

    /** Every external volume, so a USB stick is picked up as well as internal storage. */
    private fun query(): List<Item> {
        val out = ArrayList<Item>()
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(this).toList()
        } else {
            listOf(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        for (volume in volumes) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(volume)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            runCatching {
                contentResolver.query(
                    collection, projection, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    while (cursor.moveToNext() && out.size < MAX_IMAGES) {
                        out += Item(
                            ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                            cursor.getString(nameColumn) ?: ""
                        )
                    }
                }
            }
        }
        return out
    }

    private fun thumbnail(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.loadThumbnail(uri, Size(320, 180), null)
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    }.getOrNull()

    private fun choose(item: Item) {
        val uri = item.uri
        if (uri == null) {
            Wallpaper.clear(this)
            Toast.makeText(this, R.string.wallpaper_cleared, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        hint.text = getString(R.string.wallpaper_saving)
        io.execute {
            val ok = Wallpaper.save(this, uri)
            ui.post {
                if (isFinishing || isDestroyed) return@post
                Toast.makeText(
                    this,
                    if (ok) R.string.wallpaper_applied else R.string.wallpaper_failed,
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) finish() else hint.text = getString(R.string.wallpaper_failed)
            }
        }
    }

    // -------------------------------------------------------------- adapter

    private inner class Adapter : RecyclerView.Adapter<Holder>() {

        private var items: List<Item> = emptyList()

        fun submit(next: List<Item>) {
            items = next
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(this@WallpaperActivity)
                .inflate(R.layout.item_wallpaper, parent, false)
            view.background = ThemeKit.cardSelector(this@WallpaperActivity, mode, 12f)
            view.outlineProvider = ThemeKit.roundedOutline(this@WallpaperActivity, 12f)
            view.clipToOutline = true
            view.setOnFocusChangeListener { v, hasFocus ->
                v.animate()
                    .scaleX(if (hasFocus) 1.07f else 1f)
                    .scaleY(if (hasFocus) 1.07f else 1f)
                    .setDuration(150L)
                    .start()
            }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.itemView.setOnClickListener { choose(item) }

            val uri = item.uri
            if (uri == null) {
                holder.thumb.setImageDrawable(null)
                holder.thumb.setBackgroundColor(ThemeKit.withAlpha(mode.glow, 0.10f))
                return
            }
            holder.thumb.setBackgroundColor(0)
            holder.thumb.setImageDrawable(null)
            holder.thumb.tag = uri
            io.execute {
                val bitmap = thumbnail(uri) ?: return@execute
                ui.post {
                    if (holder.thumb.tag == uri) holder.thumb.setImageBitmap(bitmap)
                }
            }
        }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val name: TextView = view.findViewById(R.id.name)
    }

    private companion object {
        const val REQUEST_READ = 41
        const val MAX_IMAGES = 200
    }
}
