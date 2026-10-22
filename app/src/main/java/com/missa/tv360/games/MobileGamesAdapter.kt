package com.missa.tv360.games

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.Game
import com.missa.tv360.ui.ThumbnailLoader
import com.missa.tv360.ui.mobile.RatioImageView

class MobileGamesAdapter(
    private val onAction: (Game, GameDownloader.DownloadState?) -> Unit,
) : ListAdapter<Game, MobileGamesAdapter.Holder>(Diff) {
    private var states: Map<String, GameDownloader.DownloadState> = emptyMap()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: RatioImageView = view.findViewById(R.id.mobile_game_image)
        val title: TextView = view.findViewById(R.id.mobile_game_title)
        val description: TextView = view.findViewById(R.id.mobile_game_description)
        val status: TextView = view.findViewById(R.id.mobile_game_status)
        val progress: ProgressBar = view.findViewById(R.id.mobile_game_progress)
        val action: Button = view.findViewById(R.id.mobile_game_action)
    }

    fun updateStates(value: Map<String, GameDownloader.DownloadState>) {
        states = value
        currentList.forEachIndexed { index, game ->
            if (value.containsKey(game.id)) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.mobile_game, parent, false)
        return Holder(view).also { holder ->
            holder.action.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val game = getItem(position)
                    onAction(game, states[game.id])
                }
            }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val game = getItem(position)
        val state = states[game.id]
        holder.image.aspectRatio = 16f / 9f
        holder.image.setImageResource(R.drawable.card_game)
        holder.image.tag = game.id
        game.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ThumbnailLoader.loadArtwork(url) { bitmap ->
                if (holder.image.tag == game.id) holder.image.setImageBitmap(bitmap)
            }
        }
        holder.title.text = game.title
        holder.description.text = game.description
        holder.description.isVisible = game.description.isNotBlank()
        holder.progress.isVisible = state?.running == true
        holder.progress.progress = state?.progress ?: 0
        holder.status.text = when {
            state?.running == true -> holder.action.context.getString(R.string.games_downloading, state.progress)
            !state?.failed.isNullOrBlank() -> state?.failed
            state?.done == true -> holder.action.context.getString(R.string.games_verified)
            else -> listOfNotNull(game.genre, game.humanSize().takeIf { it.isNotBlank() }).joinToString(" · ")
        }
        holder.action.text = holder.action.context.getString(
            when {
                state?.running == true -> R.string.games_cancel
                !state?.failed.isNullOrBlank() -> R.string.games_retry
                state?.done == true && game.packageType.equals("apk", true) -> R.string.games_install
                state?.done == true -> R.string.games_downloaded
                else -> R.string.games_download
            },
        )
        holder.action.isEnabled = !(state?.done == true && !game.packageType.equals("apk", true))
    }

    override fun onViewRecycled(holder: Holder) {
        holder.image.tag = null
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }

    private object Diff : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean = oldItem == newItem
    }
}
