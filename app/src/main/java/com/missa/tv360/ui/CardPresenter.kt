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
        }
        // Leanback can wrap the card in ShadowOverlayContainer; style the captured
        // ImageCardView rather than casting the callback's wrapper view.
        card.setOnFocusChangeListener { view, focused ->
            // Zoom « actif » 1,05 / 160 ms (§5) : pas d'élévation au focus,
            // une ombre portée reste une grande ombre à éviter (§12).
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f)
                .setDuration(160)
                .start()
            BrandCardFocus.apply(card, focused)
        }
        return ViewHolder(card)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val video = item as? Video ?: return
        val card = viewHolder.view as ImageCardView
        val (width, height) = dimensionsFor(video.kind.name)
        card.setMainImageDimensions(width, height)
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
        // Dessin de secours immédiat (évite les tuiles vides pendant le chargement distant).
        val fallback = ContextCompat.getDrawable(card.context, fondParDefaut(video.kind.name))
        card.mainImageView?.let {
            it.setImageDrawable(fallback)
            card.mainImage = fallback
        }
        val remote = video.cardUrl
        if (!remote.isNullOrBlank()) {
            card.tag = remote
            ThumbnailLoader.load(card, remote, fallback)
        } else {
            card.tag = null
        }
    }

    private fun dimensionsFor(kind: String): Pair<Int, Int> = when (kind.lowercase()) {
        "live_tv", "sport", "short", "kids" -> 320 to 180   // 16:9
        "movie", "series" -> 213 to 320                     // 2:3
        "radio", "podcast", "music", "game" -> 240 to 240   // 1:1
        else -> 320 to 180
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
