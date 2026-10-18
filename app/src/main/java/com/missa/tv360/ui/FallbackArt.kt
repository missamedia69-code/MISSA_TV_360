package com.missa.tv360.ui

import androidx.annotation.DrawableRes
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind

/** Branded category artwork used when a content item has no usable thumbnail. */
object FallbackArt {

    @DrawableRes
    fun forKind(kind: ContentKind): Int = when (kind) {
        ContentKind.LIVE_TV -> R.drawable.fallback_live_tv
        ContentKind.LIVE_SPORT -> R.drawable.fallback_sport
        ContentKind.MOVIE -> R.drawable.fallback_movie
        ContentKind.SERIES -> R.drawable.fallback_series
        ContentKind.SHORT -> R.drawable.fallback_short
        ContentKind.RADIO -> R.drawable.fallback_radio
        ContentKind.PODCAST -> R.drawable.fallback_podcast
        ContentKind.MUSIC -> R.drawable.fallback_music
        ContentKind.KIDS -> R.drawable.fallback_kids
        ContentKind.GAME -> R.drawable.fallback_game
    }
}
