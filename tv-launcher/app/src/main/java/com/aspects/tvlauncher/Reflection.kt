package com.aspects.tvlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * The focused card mirrored on wet asphalt, for Wet Neon.
 *
 * It is a snapshot, not a live copy: when a card takes focus its lower part is
 * drawn once, upside down and at half resolution, into a small bitmap with the
 * fade already baked in - about 50 KB. A RecyclerView item decoration then
 * paints that bitmap under the card on every frame the row draws, which is
 * what keeps it glued to the card as the row scrolls. Stretching the
 * half-size image back up softens it, which is exactly how a reflection in a
 * puddle looks anyway.
 *
 * One instance is shared by every row; only the row holding focus draws it.
 */
class Reflection(private val focusScale: Float) : RecyclerView.ItemDecoration() {

    private var card: View? = null
    private var host: RecyclerView? = null
    private var image: Bitmap? = null

    private val draw = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fade = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val clip = Path()
    private val dst = RectF()

    val active: Boolean get() = card != null

    fun show(target: View) {
        val list = target.parent as? RecyclerView ?: return clear()
        val before = host
        card = target
        host = list
        snapshot()
        if (before != null && before !== list) before.invalidate()
        list.invalidate()
    }

    /** The card changed how it looks - its ring took the app's colour, say. */
    fun refresh() {
        if (card == null) return
        snapshot()
        host?.invalidate()
    }

    fun clear() {
        val before = host
        card = null
        host = null
        before?.invalidate()
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val target = card ?: return
        if (parent !== host || target.parent !== parent) return
        val bitmap = image ?: return

        // laid out for the card's focused size, not whatever frame of its
        // scale-up animation the row happened to be drawn on
        val wide = target.width * focusScale
        val tall = target.height * focusScale
        val cx = target.left + target.translationX + target.width / 2f
        val bottom = target.top + target.translationY + target.height / 2f + tall / 2f
        val gap = target.height * 0.05f
        dst.set(cx - wide / 2f, bottom + gap, cx + wide / 2f, bottom + gap + tall * DEPTH)
        c.drawBitmap(bitmap, null, dst, draw)
    }

    private fun snapshot() {
        val target = card ?: return
        val w = target.width
        val h = target.height
        if (w <= 0 || h <= 0) return

        val bw = max(1, (w * SCALE).toInt())
        val bh = max(1, (h * DEPTH * SCALE).toInt())
        val bitmap = image?.takeIf { it.width == bw && it.height == bh }
            ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { image = it }
        bitmap.eraseColor(0)

        val canvas = Canvas(bitmap)
        val drawn = runCatching {
            canvas.save()
            // flip about the card's bottom edge, so the bitmap's first row is
            // the card's last
            canvas.scale(SCALE, -SCALE)
            canvas.translate(0f, -h.toFloat())
            // outline clipping belongs to the render node and does not apply to
            // a software draw, so the rounded corners are put back by hand
            clip.rewind()
            val radius = ThemeKit.dp(target.context, EdgeLight.RADIUS_DP).toFloat()
            clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            target.draw(canvas)
            canvas.restore()
        }.isSuccess
        if (!drawn) {
            image = null
            return
        }

        // fade into the road: the reflection is faint at the card and gone
        // well before its own bottom edge
        fade.shader = LinearGradient(
            0f, 0f, 0f, bh.toFloat(),
            intArrayOf(0x6B000000, 0x1F000000, 0x00000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), fade)
    }

    private companion object {
        /** Half resolution: smaller, and softer once stretched back up. */
        const val SCALE = 0.5f

        /** How much of the card's height the reflection shows. */
        const val DEPTH = 0.42f
    }
}
