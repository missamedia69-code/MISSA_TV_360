package com.missa.tv360

import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Game
import com.missa.tv360.data.Video
import com.missa.tv360.ui.CardShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModelTest {
    @Test
    fun sportExcerptKeepsItsCategoryWithoutBecomingLive() {
        val video = Video(
            id = "sport-1",
            title = "Extrait",
            description = "",
            cardRes = 0,
            streamUrl = "https://example.invalid/video.mp4",
            kind = ContentKind.MOVIE,
            originalKind = "sport",
            shapeHint = com.missa.tv360.data.CardShapeHint.LANDSCAPE,
        )
        assertEquals("Sport", video.displayKindLabel)
        assertFalse(video.isLive)
        assertEquals(CardShape.LANDSCAPE, CardShape.forVideo(video))
    }

    @Test
    fun explicitAudioMetadataDrivesAudioBehavior() {
        val music = Video("music", "Musique", "", 0, "stream", ContentKind.MUSIC, audioOnly = true)
        val podcast = Video("podcast", "Podcast", "", 0, "stream", ContentKind.PODCAST)
        assertTrue(music.isAudioOnly)
        assertTrue(podcast.isAudioOnly)
        assertFalse(music.isLive)
    }

    @Test
    fun episodeTitleUsesStructuredMetadata() {
        val episode = Video(
            id = "episode",
            title = "Titre original",
            description = "",
            cardRes = 0,
            streamUrl = "stream",
            kind = ContentKind.SERIES,
            seriesTitle = "Mission Boréale",
            season = 2,
            episode = 4,
        )
        assertEquals("Mission Boréale — S2 E4", episode.seriesDisplayTitle())
    }

    @Test
    fun sourceHeadersAndShapeHintSurviveModelCopy() {
        val video = Video(
            id = "source",
            title = "Source",
            description = "",
            cardRes = 0,
            streamUrl = "",
            shapeHint = com.missa.tv360.data.CardShapeHint.LANDSCAPE,
            streamHeaders = mapOf("Referer" to "https://source.invalid/"),
        )
        val resolved = video.copy(streamUrl = "https://cdn.invalid/video.mp4")
        assertEquals("https://source.invalid/", resolved.streamHeaders["Referer"])
        assertEquals(CardShape.LANDSCAPE, CardShape.forVideo(resolved))
    }

    @Test
    fun gameSizeIsHumanReadableWithoutInventingMissingSize() {
        assertEquals("", Game("empty", "Jeu", downloadUrl = "").humanSize())
        assertTrue(Game("game", "Jeu", downloadUrl = "", sizeBytes = 2L * 1024 * 1024).humanSize().contains("Mo"))
    }
}
