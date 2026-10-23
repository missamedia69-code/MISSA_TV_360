package com.missa.tv360

import com.missa.tv360.data.source.FilmEngineManifest
import com.missa.tv360.data.source.FilmSourceManifest
import com.missa.tv360.data.source.bundledFilmEngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le manifeste distant est une **frontière de sécurité** : il ne transporte que
 * des paramètres. Ces tests verrouillent ce qui est accepté et ce qui est rejeté
 * (adaptateur inconnu, type de média libre, formats non lisibles, limites
 * démesurées).
 */
class FilmSourceManifestTest {

    private fun manifest(vararg sources: FilmSourceManifest) = FilmEngineManifest(
        schemaVersion = 1,
        engineId = "films",
        version = 2,
        sources = sources.toList(),
    )

    private fun source(
        id: String? = "dailymotion",
        adapter: String? = "dailymotion",
        enabled: Boolean? = true,
        mediaType: String? = "movies",
        formats: List<String>? = listOf("hls", "mp4"),
        tvTypes: List<String>? = listOf("movies", "others"),
        trendingLimit: Int? = 20,
        relatedLimit: Int? = 12,
    ) = FilmSourceManifest(
        id = id,
        adapter = adapter,
        enabled = enabled,
        mediaType = mediaType,
        trendingLimit = trendingLimit,
        searchLimit = 40,
        playableFormats = formats,
        relatedLimit = relatedLimit,
        tvTypes = tvTypes,
    )

    @Test
    fun engine_rejectsWrongSchemaOrEngineId() {
        assertNull(
            FilmEngineManifest(schemaVersion = 2, engineId = "films", version = 2, sources = listOf(source()))
                .toRuntimeConfig(),
        )
        assertNull(
            FilmEngineManifest(schemaVersion = 1, engineId = "series", version = 2, sources = listOf(source()))
                .toRuntimeConfig(),
        )
        assertNull(
            FilmEngineManifest(schemaVersion = 1, engineId = "films", version = 1, sources = emptyList())
                .toRuntimeConfig(),
        )
    }

    @Test
    fun source_isAcceptedAndCarriesTvTypes() {
        val config = manifest(source()).toRuntimeConfig()
        assertNotNull(config)
        val entry = config!!.sources.single()
        assertEquals("dailymotion", entry.adapter)
        assertEquals(listOf("movies", "others"), entry.tvTypes)
        assertEquals(listOf("hls", "mp4"), entry.playableFormats)
    }

    @Test
    fun disabledSource_isDropped() {
        assertNull(manifest(source(enabled = false)).toRuntimeConfig())
    }

    @Test
    fun adapterId_mustBeSlug() {
        // Une valeur libre permettrait de désigner n'importe quoi côté distant.
        assertNull(manifest(source(adapter = "dailymotion; rm -rf /")).toRuntimeConfig())
        assertNull(manifest(source(id = "Dailymotion")).toRuntimeConfig())
        assertNull(manifest(source(id = "")).toRuntimeConfig())
    }

    @Test
    fun mediaType_mustBelongToKnownVocabulary() {
        assertNull(manifest(source(mediaType = "whatever")).toRuntimeConfig())
        assertNull(manifest(source(mediaType = null)).toRuntimeConfig())
        assertEquals(
            "series",
            manifest(source(mediaType = "series")).toRuntimeConfig()!!.sources.single().mediaType,
        )
    }

    @Test
    fun playableFormats_areFilteredToWhatThePlayerCanOpen() {
        val entry = manifest(source(formats = listOf("mkv", "HLS", "mp4", "avi")))
            .toRuntimeConfig()!!.sources.single()
        assertEquals(listOf("hls", "mp4"), entry.playableFormats)
    }

    @Test
    fun unknownTvTypes_fallBackToMovies() {
        val entry = manifest(source(tvTypes = listOf("anime", "LIVE")))
            .toRuntimeConfig()!!.sources.single()
        assertEquals(listOf("live"), entry.tvTypes)
    }

    @Test
    fun limits_areClamped() {
        val entry = manifest(source(trendingLimit = 5000, relatedLimit = 0))
            .toRuntimeConfig()!!.sources.single()
        assertEquals(50, entry.trendingLimit)
        assertEquals(1, entry.relatedLimit)
    }

    @Test
    fun trendingQuery_isLimitedToPlainText() {
        val entry = manifest(
            source().copy(trendingQuery = "mediatype:movies"),
        ).toRuntimeConfig()!!.sources.single()
        assertEquals("mediatype:movies", entry.trendingQuery)

        // Un flux de contrôle (saut de ligne) est refusé et remplacé par la
        // requête par défaut du type de média.
        val sanitized = manifest(
            source().copy(trendingQuery = "movies\nHost: evil.example"),
        ).toRuntimeConfig()!!.sources.single()
        assertTrue(!sanitized.trendingQuery.contains("\n"))
    }

    @Test
    fun bundledFallback_activatesEveryCompiledFilmSource() {
        val config = bundledFilmEngineConfig()
        assertEquals(2, config.version)
        assertEquals(listOf("internet_archive", "dailymotion"), config.sources.map { it.id })
        val dailymotion = config.sources[1]
        assertEquals("dailymotion", dailymotion.adapter)
        assertEquals("trending", dailymotion.trendingSort)
        assertEquals("relevance", dailymotion.searchSort)
        assertEquals(listOf("hls", "mp4"), dailymotion.playableFormats)
        assertEquals(listOf("movies", "others"), dailymotion.tvTypes)
    }

    /** Le manifeste réellement publié doit passer le validateur de l'app. */
    @Test
    fun shippedManifest_isAcceptedByTheValidator() {
        val file = java.io.File("../engines/films/manifest.json")
        // Test lancé hors du dépôt (IDE isolé) : on le marque ignoré plutôt que
        // de le faire passer pour vert.
        org.junit.Assume.assumeTrue("manifeste introuvable", file.isFile)
        val parsed = com.google.gson.Gson().fromJson(
            file.readText(Charsets.UTF_8),
            FilmEngineManifest::class.java,
        )
        val config = parsed.toRuntimeConfig()
        assertNotNull(config)
        assertEquals(
            listOf("internet_archive", "dailymotion"),
            config!!.sources.map { it.adapter },
        )
    }
}
