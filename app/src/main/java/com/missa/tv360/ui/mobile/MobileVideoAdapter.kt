package com.missa.tv360.ui.mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore
import com.missa.tv360.ui.CardShape
import com.missa.tv360.ui.FocusMotion
import com.missa.tv360.ui.ThumbnailLoader
import com.missa.tv360.ui.afficheParDefaut

/** Carte tactile réutilisée par les rangées et les grilles mobiles. */
class MobileVideoAdapter(
    private val fillWidth: Boolean = false,
    private val onClick: (Video) -> Unit,
    @LayoutRes private val itemLayoutRes: Int = R.layout.mobile_video_card,
) : ListAdapter<Video, MobileVideoAdapter.Holder>(Diff) {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.mobile_card_root)
        val artContainer: FrameLayout = view.findViewById(R.id.mobile_art_container)
        val image: RatioImageView = view.findViewById(R.id.mobile_card_image)
        val live: TextView = view.findViewById(R.id.mobile_card_live)
        val progress: ProgressBar = view.findViewById(R.id.mobile_card_progress)
        val title: TextView = view.findViewById(R.id.mobile_card_title)
        val subtitle: TextView = view.findViewById(R.id.mobile_card_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
        return Holder(view).also { holder ->
            holder.root.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
            }
            holder.root.setOnFocusChangeListener { v, focused -> FocusMotion.apply(v, focused) }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val video = getItem(position)
        val shape = CardShape.forVideo(video)
        val width = when {
            fillWidth -> ViewGroup.LayoutParams.MATCH_PARENT
            shape == CardShape.POSTER -> 122.dp(holder)
            shape == CardShape.SQUARE -> 144.dp(holder)
            else -> 204.dp(holder)
        }
        holder.root.layoutParams = holder.root.layoutParams.apply {
            this.width = if (fillWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        holder.artContainer.layoutParams = holder.artContainer.layoutParams.apply { this.width = width }
        holder.image.layoutParams = holder.image.layoutParams.apply { this.width = width }
        holder.image.aspectRatio = shape.ratio
        holder.title.layoutParams = holder.title.layoutParams.apply { this.width = width }
        holder.subtitle.layoutParams = holder.subtitle.layoutParams.apply { this.width = width }
        holder.progress.layoutParams = holder.progress.layoutParams.apply { this.width = width }
        holder.title.text = video.title
        holder.subtitle.text = video.subtitle()
        holder.subtitle.isVisible = video.subtitle().isNotBlank()
        holder.live.isVisible = video.isLive
        val fraction = WatchProgressStore.fraction(holder.root.context, video.id)
        holder.progress.isVisible = fraction != null
        holder.progress.progress = ((fraction ?: 0f) * 100).toInt()
        holder.root.contentDescription = buildString {
            append(video.title)
            if (video.subtitle().isNotBlank()) append(". ").append(video.subtitle())
            fraction?.let { append(". Progression : ").append((it * 100).toInt()).append(" %") }
        }
        holder.image.tag = video.id
        holder.image.setImageResource(if (video.cardRes != 0) video.cardRes else afficheParDefaut(video.kind))
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadArtwork(video.cardUrl) { bitmap ->
                if (holder.image.tag == video.id) holder.image.setImageBitmap(bitmap)
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.image.tag = null
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    private fun Int.dp(holder: Holder): Int =
        (this * holder.root.resources.displayMetrics.density).toInt()

    private object Diff : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean = oldItem == newItem
    }
}
