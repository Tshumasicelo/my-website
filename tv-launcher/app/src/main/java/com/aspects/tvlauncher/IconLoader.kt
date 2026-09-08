package com.aspects.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Icons and TV banners are the single biggest memory cost in a launcher, so they
 * are never held on the model objects - only in a bounded LruCache - and they are
 * decoded off the main thread so a slow PackageManager call cannot stutter the UI.
 */
class IconLoader(context: Context) {

    private val pm = context.applicationContext.packageManager
    private val cache = LruCache<String, Drawable>(60)

    /** Apps we already know ship no 320x180 banner; stops us re-asking every scroll. */
    private val bannerless = Collections.synchronizedSet(HashSet<String>())

    private val io = Executors.newFixedThreadPool(2)
    private val ui = Handler(Looper.getMainLooper())

    fun shutdown() {
        io.shutdownNow()
    }

    fun bindIcon(entry: AppEntry, target: ImageView) {
        val key = "i:" + entry.key
        target.setTag(R.id.icon_tag, key)

        val cached = cache.get(key)
        if (cached != null) {
            target.setImageDrawable(cached)
            return
        }
        target.setImageResource(R.drawable.ic_app_placeholder)

        io.execute {
            val drawable = runCatching {
                pm.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
            }.recoverCatching {
                pm.getApplicationIcon(entry.packageName)
            }.getOrNull() ?: return@execute

            ui.post {
                cache.put(key, drawable)
                if (target.getTag(R.id.icon_tag) == key) target.setImageDrawable(drawable)
            }
        }
    }

    /** [onReady] is called with true only once a real banner is on screen. */
    fun bindBanner(entry: AppEntry, target: ImageView, onReady: (Boolean) -> Unit) {
        val key = "b:" + entry.key
        target.setTag(R.id.icon_tag, key)

        val cached = cache.get(key)
        if (cached != null) {
            target.setImageDrawable(cached)
            onReady(true)
            return
        }

        target.setImageDrawable(null)
        onReady(false)
        if (bannerless.contains(key)) return

        io.execute {
            val drawable = runCatching {
                pm.getActivityBanner(ComponentName(entry.packageName, entry.activityName))
            }.getOrNull() ?: runCatching {
                pm.getApplicationBanner(entry.packageName)
            }.getOrNull()

            if (drawable == null) {
                bannerless.add(key)
                return@execute
            }
            ui.post {
                cache.put(key, drawable)
                // Guard against the ImageView having been recycled onto another app.
                if (target.getTag(R.id.icon_tag) == key) {
                    target.setImageDrawable(drawable)
                    onReady(true)
                }
            }
        }
    }
}
