package com.missa.tv360.ui

import com.missa.tv360.R
import com.missa.tv360.data.ContentKind

/**
 * Familles de formes de tuiles régularisées selon le type de contenu.
 *
 * - [LANDSCAPE] (16:9) : TV Direct, Sport, Films courts, Jeunesse
 * - [POSTER] (2:3) : Films, Séries
 * - [SQUARE] (1:1) : Radio, Podcasts, Musique, Jeux vidéo
 */
enum class CardShape(
    val widthRes: Int,
    val heightRes: Int,
    val gridWidthRes: Int,
    val gridHeightRes: Int,
    val gridColumns: Int,
) {
    LANDSCAPE(
        R.dimen.card16_w, R.dimen.card16_h,
        R.dimen.grid_landscape_w, R.dimen.grid_landscape_h,
        gridColumns = 4,
    ),
    POSTER(
        R.dimen.poster_w, R.dimen.poster_h,
        R.dimen.grid_poster_w, R.dimen.grid_poster_h,
        gridColumns = 6,
    ),
    SQUARE(
        R.dimen.square, R.dimen.square,
        R.dimen.grid_square_w, R.dimen.grid_square_h,
        gridColumns = 5,
    );

    companion object {
        fun forKind(kind: ContentKind): CardShape = when (kind) {
            ContentKind.MOVIE, ContentKind.SERIES -> POSTER
            ContentKind.RADIO, ContentKind.PODCAST, ContentKind.MUSIC, ContentKind.GAME -> SQUARE
            else -> LANDSCAPE
        }

        /**
         * Forme effective d'un contenu : l'indication issue du genre d'API
         * prime sur le kind normalisé (un extrait sportif a un kind MOVIE mais
         * reste une tuile paysage).
         */
        fun forVideo(video: com.missa.tv360.data.Video): CardShape =
            when (video.shapeHint) {
                com.missa.tv360.data.CardShapeHint.LANDSCAPE -> LANDSCAPE
                com.missa.tv360.data.CardShapeHint.POSTER -> POSTER
                com.missa.tv360.data.CardShapeHint.SQUARE -> SQUARE
                null -> forKind(video.kind)
            }
    }
}
