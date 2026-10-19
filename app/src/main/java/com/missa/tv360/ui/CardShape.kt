package com.missa.tv360.ui

import com.missa.tv360.R
import com.missa.tv360.data.ContentKind

enum class CardShape(val widthRes: Int, val heightRes: Int) {
    LANDSCAPE(R.dimen.card16_w, R.dimen.card16_h),
    POSTER(R.dimen.poster_w, R.dimen.poster_h),
    SQUARE(R.dimen.square, R.dimen.square);

    companion object {
        fun forKind(kind: ContentKind) = when (kind) {
            ContentKind.MOVIE, ContentKind.SERIES -> POSTER
            ContentKind.RADIO, ContentKind.PODCAST, ContentKind.MUSIC, ContentKind.GAME -> SQUARE
            else -> LANDSCAPE
        }
    }
}