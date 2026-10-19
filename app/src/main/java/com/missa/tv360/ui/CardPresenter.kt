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
            // Keep the original tile footprint (the legacy cards were 320×180).
            setMainImageAdjustViewBounds(false)
            setMainImageDimensions(320, 180)
            setMainImageScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
            setBackgroundResource(R.drawable.card_focus_state)
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
                BrandCardFocus.apply(view as ImageCardView, focused)
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
        BrandCardFocus.apply(card, card.hasFocus())
        card.contentDescription = buildString {
            append(video.title)
            append(". ").append(video.subtitle())
            if (video.isLive) append(". En direct")
        }
        card.badgeImage = if (video.isLive) {
            ContextCompat.getDrawable(card.context, R.drawable.ic_live_badge)
        } else null
        // The type-specific fallback stays visible while loading and when the URL is blank or fails.
        ContextCompat.getDrawable(card.context, fondParDefaut(video.kind.name))
            ?.let { card.mainImageView.setImageDrawable(it) }
        val remote = video.cardUrl
        if (!remote.isNullOrBlank()) {
            card.tag = remote
            ThumbnailLoader.load(card, remote)
        } else {
            card.tag = null
        }
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
