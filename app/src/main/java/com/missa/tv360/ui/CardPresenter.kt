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
            setBackgroundColor(
                ContextCompat.getColor(parent.context, R.color.missa_card_bg)
            )
        }
        return ViewHolder(card)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as Video
        val card = viewHolder.view as ImageCardView
        card.titleText = if (video.isSeries) "${video.seriesTitle}" else video.title
        card.contentText = video.subtitle() + if (video.isSeries) "" else ""
        card.badgeImage = if (video.isLive) {
            ContextCompat.getDrawable(card.context, R.drawable.ic_live_badge)
        } else null
        // Vignette : distante (catalogue du dépôt) si présente, sinon drawable local.
        val remote = video.cardUrl
        if (!remote.isNullOrBlank()) {
            card.tag = remote
            ThumbnailLoader.load(card, remote)
        } else {
            card.tag = null
            val fallback = if (video.cardRes != 0) video.cardRes else defaultCardFor(video.kind)
            ContextCompat.getDrawable(card.context, fallback)
                ?.let { card.mainImageView.setImageDrawable(it) }
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
        card.titleText = null
        card.contentText = null
    }
}
