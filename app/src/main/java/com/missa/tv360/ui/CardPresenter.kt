package com.missa.tv360.ui

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.Video

/**
 * Présentateur des cartes de contenu (ImageCardView Leanback).
 * Fonds et focus aux couleurs de la marque (jaune sélection / bleu carte).
 */
class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageAdjustViewBounds(true)
            setMainImageScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
            setBackgroundColor(ContextCompat.getColor(parent.context, R.color.missa_card_bg))
            // Contraste et retour de focus visibles depuis le canapé.
            cameraDistance = 12_000 * resources.displayMetrics.density
            setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate()
                    .scaleX(if (focused) 1.07f else 1f)
                    .scaleY(if (focused) 1.07f else 1f)
                    .setDuration(150)
                    .start()
                view.elevation = if (focused) 12f * resources.displayMetrics.density else 2f
            }
        }
        return ViewHolder(card)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as Video
        val card = viewHolder.view as ImageCardView
        card.titleText = if (video.isSeries) video.seriesTitle else video.title
        card.contentText = video.subtitle()
        card.contentDescription = buildString {
            append(video.title)
            append(". ").append(video.subtitle())
            if (video.isLive) append(". En direct")
        }
        card.badgeImage = if (video.isLive) {
            ContextCompat.getDrawable(card.context, R.drawable.ic_live_badge)
        } else null
        // Vignette : distante (catalogue du dépôt) si présente, sinon drawable local.
        val fallback = if (video.cardRes != 0) video.cardRes else defaultCardFor(video.kind)
        // Always reset recycled cards immediately; the remote image may take a moment.
        ContextCompat.getDrawable(card.context, fallback)
            ?.let { card.mainImageView.setImageDrawable(it) }
        val remote = video.cardUrl
        if (!remote.isNullOrBlank()) {
            card.tag = remote
            ThumbnailLoader.load(card, remote)
        } else {
            card.tag = null
        }
    }

    /** Drawable local par défaut (items du catalogue distant sans vignette). */
    private fun defaultCardFor(kind: com.missa.tv360.data.ContentKind): Int =
        when (kind) {
            com.missa.tv360.data.ContentKind.LIVE_TV -> R.drawable.card_nasa
            com.missa.tv360.data.ContentKind.LIVE_SPORT -> R.drawable.card_sport
            com.missa.tv360.data.ContentKind.MOVIE -> R.drawable.card_phantom
            com.missa.tv360.data.ContentKind.SERIES -> R.drawable.card_series
            com.missa.tv360.data.ContentKind.SHORT -> R.drawable.card_bunny
            com.missa.tv360.data.ContentKind.RADIO -> R.drawable.card_radio
            com.missa.tv360.data.ContentKind.PODCAST -> R.drawable.card_podcast
            com.missa.tv360.data.ContentKind.MUSIC -> R.drawable.card_music
            com.missa.tv360.data.ContentKind.KIDS -> R.drawable.card_fun
            com.missa.tv360.data.ContentKind.GAME -> R.drawable.card_game
        }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.tag = null
        card.badgeImage = null
        card.mainImage = null
        card.contentDescription = null
        card.titleText = null
        card.contentText = null
    }
}
