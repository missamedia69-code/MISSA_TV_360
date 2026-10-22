package com.missa.tv360

import com.missa.tv360.data.source.ExtractorLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le contrat d'agrégateur repose sur ce type : plusieurs liens par contenu,
 * un choix déterministe, et des en-têtes qui ne peuvent pas être injectés.
 */
class ExtractorLinkTest {

    @Test
    fun best_picksHighestQuality() {
        val links = listOf(
            ExtractorLink(url = "https://cdn/sd.mp4", quality = 480),
            ExtractorLink(url = "https://cdn/full-hd.mp4", quality = 1080),
            ExtractorLink(url = "https://cdn/hd.mp4", quality = 720),
        )
        assertEquals("https://cdn/full-hd.mp4", ExtractorLink.best(links)?.url)
    }

    @Test
    fun best_prefersMp4OverHlsAtSameQuality() {
        val links = listOf(
            ExtractorLink(url = "https://cdn/master.m3u8", quality = 720, format = "hls"),
            ExtractorLink(url = "https://cdn/720.mp4", quality = 720, format = "mp4"),
        )
        assertEquals("https://cdn/720.mp4", ExtractorLink.best(links)?.url)
    }

    @Test
    fun best_ignoresBlankUrlsAndEmptyLists() {
        assertEquals(
            "https://cdn/ok.mp4",
            ExtractorLink.best(listOf(ExtractorLink(url = ""), ExtractorLink(url = "https://cdn/ok.mp4")))?.url,
        )
        assertNull(ExtractorLink.best(emptyList()))
    }

    @Test
    fun label_isReadable() {
        assertEquals("1080p", ExtractorLink(url = "u", quality = 1080).label)
        assertEquals("Auto", ExtractorLink(url = "u", format = "hls").label)
        assertEquals("SD", ExtractorLink(url = "u").label)
    }

    @Test
    fun sanitizeHeaders_rejectsInjectionAndKeepsValidOnes() {
        val headers = ExtractorLink.sanitizeHeaders(
            mapOf(
                "Referer" to "https://www.dailymotion.com/",
                "User-Agent" to "MISSA/1.0",
                // Injection d'en-tête : CRLF dans la valeur.
                "X-Evil" to "a\r\nX-Injected: 1",
                // Nom invalide : deux-points et espace.
                "Bad:Name" to "value",
                "" to "orphan",
            ),
        )
        assertEquals(setOf("Referer", "User-Agent"), headers.keys)
        assertTrue(headers.getValue("Referer").startsWith("https://"))
    }
}
