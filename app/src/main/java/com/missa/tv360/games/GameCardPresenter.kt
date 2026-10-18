package com.missa.tv360.games

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.Game

/**
 * Presentateur des cartes de jeu telechargeable (ImageCardView Leanback).
 * Affiche titre, genre + taille, et un badge selon le type de paquet.
 */
class GameCardPresenter : Presenter() {

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
        val game = item as Game
        val card = viewHolder.view as ImageCardView
        card.titleText = game.title
        val meta = buildString {
            if (!game.genre.isNullOrBlank()) append(game.genre) else append("Jeu")
            if (game.sizeBytes > 0) append(" • ").append(game.humanSize())
            if (!game.versionName.isNullOrBlank()) append(" • v").append(game.versionName)
        }
        card.contentText = meta
        // Pas de vignette distante dans la v1 : on garde le fond de marque.
        ContextCompat.getDrawable(card.context, R.drawable.card_game)
            ?.let { card.mainImageView.setImageDrawable(it) }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.badgeImage = null
        card.mainImage = null
        card.titleText = null
        card.contentText = null
    }
}
