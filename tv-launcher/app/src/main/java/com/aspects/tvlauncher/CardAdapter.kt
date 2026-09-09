package com.aspects.tvlauncher

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * One card on the home screen. [app] is null for System row tiles, which draw a
 * bundled vector glyph instead of an installed app's icon.
 */
data class CardItem(
    val id: String,
    val label: String,
    val app: AppEntry?,
    val glyphRes: Int,
    val wide: Boolean
)

class CardAdapter(
    private val ctx: Context,
    private val icons: IconLoader,
    private val mode: DriveMode,
    private val onClick: (CardItem, View) -> Unit,
    private val onLongClick: (CardItem) -> Boolean,
    private val onFocus: (CardItem) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<CardItem> = emptyList()

    fun submit(next: List<CardItem>) {
        items = next
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position].wide) TYPE_WIDE else TYPE_TILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(ctx)
        return if (viewType == TYPE_WIDE) {
            WideHolder(decorate(inflater.inflate(R.layout.item_banner, parent, false)))
        } else {
            TileHolder(decorate(inflater.inflate(R.layout.item_tile, parent, false)))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        holder.itemView.setOnClickListener { onClick(item, holder.itemView) }
        holder.itemView.setOnLongClickListener { onLongClick(item) }
        attachFocus(holder.itemView, item)
        when (holder) {
            is WideHolder -> bindWide(holder, item)
            is TileHolder -> bindTile(holder, item)
        }
    }

    private fun bindWide(holder: WideHolder, item: CardItem) {
        holder.fallbackLabel.text = item.label
        val app = item.app
        if (app == null) {
            holder.banner.setImageDrawable(null)
            holder.fallbackIcon.setImageResource(item.glyphRes)
            holder.fallback.visibility = View.VISIBLE
            return
        }
        icons.bindIcon(app, holder.fallbackIcon)
        icons.bindBanner(app, holder.banner) { hasBanner ->
            holder.fallback.visibility = if (hasBanner) View.GONE else View.VISIBLE
        }
    }

    private fun bindTile(holder: TileHolder, item: CardItem) {
        holder.label.text = item.label
        val app = item.app
        if (app == null) {
            holder.icon.setImageResource(item.glyphRes)
            holder.icon.setColorFilter(mode.glow)
        } else {
            holder.icon.clearColorFilter()
            icons.bindIcon(app, holder.icon)
        }
    }

    /**
     * Focus has to read from three metres away, so it lands as four things at
     * once: an accent ring, a lift, a scale-up, and a highlight sweeping across
     * the card. On Android 9+ the elevation shadow is tinted to the Drive Mode's
     * glow, which is what turns the lift into a bloom rather than a grey blur.
     */
    private fun decorate(view: View): View {
        view.background = ThemeKit.cardSelector(ctx, mode)
        view.outlineProvider = ThemeKit.roundedOutline(ctx, 14f)
        view.clipToOutline = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = mode.glow
            view.outlineAmbientShadowColor = mode.glow
        }

        view.findViewById<View?>(R.id.gloss)?.rotation = 14f
        return view
    }

    /**
     * Set at bind time rather than in decorate(), because the backdrop needs to
     * know which app just took focus and a view only carries one focus listener.
     */
    private fun attachFocus(view: View, item: CardItem) {
        val lift = ThemeKit.dp(ctx, 14f).toFloat()
        val gloss = view.findViewById<View?>(R.id.gloss)
        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                .setDuration(160L)
                .start()
            v.elevation = if (hasFocus) lift else 0f
            if (hasFocus) {
                sweep(gloss, v.width)
                onFocus(item)
            } else {
                gloss?.visibility = View.INVISIBLE
            }
        }
    }

    private fun sweep(gloss: View?, width: Int) {
        if (gloss == null || width <= 0) return
        gloss.animate().cancel()
        gloss.translationX = -width.toFloat()
        gloss.alpha = 0f
        gloss.visibility = View.VISIBLE
        gloss.animate()
            .translationX(width * 1.4f)
            .alpha(1f)
            .setDuration(620L)
            .withEndAction {
                gloss.alpha = 0f
                gloss.visibility = View.INVISIBLE
            }
            .start()
    }

    private class WideHolder(view: View) : RecyclerView.ViewHolder(view) {
        val banner: ImageView = view.findViewById(R.id.banner)
        val fallback: LinearLayout = view.findViewById(R.id.fallback)
        val fallbackIcon: ImageView = view.findViewById(R.id.fallbackIcon)
        val fallbackLabel: TextView = view.findViewById(R.id.fallbackLabel)
    }

    private class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    private companion object {
        const val TYPE_WIDE = 0
        const val TYPE_TILE = 1
        const val FOCUS_SCALE = 1.09f
    }
}
