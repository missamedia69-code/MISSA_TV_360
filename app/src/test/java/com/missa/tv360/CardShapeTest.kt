package com.missa.tv360

import com.missa.tv360.data.ContentKind
import com.missa.tv360.ui.CardShape
import org.junit.Assert.assertEquals
import org.junit.Test

class CardShapeTest {
    @Test
    fun forKind_returnsPosterForMoviesAndSeries() {
        assertEquals(CardShape.POSTER, CardShape.forKind(ContentKind.MOVIE))
        assertEquals(CardShape.POSTER, CardShape.forKind(ContentKind.SERIES))
    }

    @Test
    fun forKind_returnsSquareForAudioAndGames() {
        assertEquals(CardShape.SQUARE, CardShape.forKind(ContentKind.RADIO))
        assertEquals(CardShape.SQUARE, CardShape.forKind(ContentKind.PODCAST))
        assertEquals(CardShape.SQUARE, CardShape.forKind(ContentKind.MUSIC))
        assertEquals(CardShape.SQUARE, CardShape.forKind(ContentKind.GAME))
    }

    @Test
    fun forKind_returnsLandscapeOtherwise() {
        assertEquals(CardShape.LANDSCAPE, CardShape.forKind(ContentKind.LIVE_TV))
        assertEquals(CardShape.LANDSCAPE, CardShape.forKind(ContentKind.SHORT))
    }
}