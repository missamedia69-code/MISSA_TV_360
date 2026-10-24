package com.missa.tv360.games

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.Game
import com.missa.tv360.ui.BrandCardFocus
import com.missa.tv360.ui.ThumbnailLoader
import com.missa.tv360.ui.fondParDefaut

/**
 * Presentateur des cartes de jeu telechargeable (ImageCardView Leanback).
 * Affiche titre, genre + taille, et un badge selon le type de paquet.
 */
class GameCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            // Match the legacy game-card image size; source fallback PNGs are 1280×720.
            setMainImageAdjustViewBounds(false)
            setMainImageDimensions(240, 240)
            setMainImageScaleType(android.widget.ImageView.ScaleType.CENTER_CROP)
            setBackgroundResource(R.drawable.card_focus_state)
        }
        // Leanback may pass its ShadowOverlayContainer wrapper to the listener.
        card.setOnFocusChangeListener { view, focused ->
            // Zoom « actif » 1,05 / 160 ms (§10) : aucune élévation (§12 évite
            // les grandes ombres et garde une famille de focus cohérente).
            com.missa.tv360.ui.FocusMotion.apply(view, focused)
            BrandCardFocus.apply(card, focused)
        }
        return ViewHolder(card)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val game = item as Game
        val card = viewHolder.view as ImageCardView
        card.titleText = game.title
        card.contentDescription = buildString {
            append(game.title)
            if (!game.genre.isNullOrBlank()) append(". ").append(game.genre)
            if (game.sizeBytes > 0) append(". ").append(game.humanSize())
        }
        val meta = buildString {
            if (!game.genre.isNullOrBlank()) append(game.genre) else append("Jeu")
            if (game.sizeBytes > 0) append(" • ").append(game.humanSize())
            if (!game.versionName.isNullOrBlank()) append(" • v").append(game.versionName)
        }
        card.contentText = meta
        BrandCardFocus.apply(card, card.hasFocus())
        // Vignette du catalogue si fournie, sinon visuel de marque.
        ContextCompat.getDrawable(card.context, fondParDefaut("jeux_video"))
            ?.let { card.mainImageView?.setImageDrawable(it) }
        val remote = game.thumbnailUrl
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
