package com.missa.tv360.ui

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore

/** Présentateur TV unique : ratios partagés, titre unique, focus et état accessibles. */
class MissaCardPresenter(
    private val fixedWidthRes: Int = 0,
    private val fixedHeightRes: Int = 0,
    private val useGridDimensions: Boolean = false,
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        (parent as? RecyclerView)?.itemAnimator = null
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_missa, parent, false)
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        v.setOnFocusChangeListener { view, focused ->
            FocusMotion.apply(view, focused)
            view.findViewById<TextView>(R.id.card_title).setTextColor(
                ContextCompat.getColor(
                    view.context,
                    if (focused) R.color.missa_yellow_focus else R.color.missa_text_secondary,
                ),
            )
            val ring = view.findViewById<View>(R.id.card_focus_ring)
            if (focused) ring.setBackgroundResource(R.drawable.bg_card_focus_ring)
            else ring.background = null
        }
        return ViewHolder(v)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val video = item as? Video ?: return
        val v = vh.view
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            val gap = dpToPx(16)
            params.setMargins(gap, 0, gap, 0)
            v.layoutParams = params
        }

        val shape = CardShape.forVideo(video)
        val image = v.findViewById<ImageView>(R.id.card_image)
        // Les cartes normales gardent leur recadrage. La tuile « Tout voir »
        // utilise FIT_XY pour que son pictogramme conserve sa taille intrinsèque.
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val w = v.resources.getDimensionPixelSize(
            fixedWidthRes.takeIf { it != 0 } ?: if (useGridDimensions) shape.gridWidthRes else shape.widthRes,
        )
        val h = v.resources.getDimensionPixelSize(
            fixedHeightRes.takeIf { it != 0 } ?: if (useGridDimensions) shape.gridHeightRes else shape.heightRes,
        )
        image.layoutParams = image.layoutParams.apply { width = w; height = h }
        val ring = v.findViewById<View>(R.id.card_focus_ring)
        ring.layoutParams = ring.layoutParams.apply { width = w; height = h }
        ring.background = null

        v.findViewById<TextView>(R.id.card_title).apply {
            text = video.title
            setTextColor(ContextCompat.getColor(context, R.color.missa_text_secondary))
            isVisible = video.title.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
        }
        val subtitle = video.subtitle()
        v.findViewById<TextView>(R.id.card_subtitle).apply {
            text = subtitle
            isVisible = video.moreTargetRowId == null && subtitle.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
        }
        v.findViewById<TextView>(R.id.badge_live).isVisible = video.isLive
        val progress = v.findViewById<ProgressBar>(R.id.card_progress)
        val fraction = WatchProgressStore.fraction(v.context, video.id)
        progress.isVisible = fraction != null
        progress.progress = ((fraction ?: 0f) * 100).toInt()
        progress.layoutParams = progress.layoutParams.apply { width = w }

        v.tag = video.id
        if (video.moreTargetRowId != null) {
            image.scaleType = ImageView.ScaleType.FIT_XY
            image.setImageResource(R.drawable.card_more)
            v.contentDescription = v.context.getString(R.string.row_more_description, video.description)
            return
        }

        v.contentDescription = buildString {
            append(video.title)
            if (subtitle.isNotBlank()) append(". ").append(subtitle)
            fraction?.let { append(". Progression : ").append((it * 100).toInt()).append(" %") }
        }
        image.setImageResource(
            if (video.cardRes != 0) video.cardRes else afficheParDefaut(video.kind),
        )
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadArtwork(video.cardUrl) { bmp ->
                if (v.tag == video.id) image.setImageBitmap(bmp)
            }
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        vh.view.tag = null
        vh.view.findViewById<ImageView>(R.id.card_image).setImageDrawable(null)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * Resources.getSystem().displayMetrics.density).toInt()
}
