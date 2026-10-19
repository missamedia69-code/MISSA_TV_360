package com.missa.tv360.data.source

import com.missa.tv360.data.Video
import kotlinx.coroutines.CancellationException

/** Adaptateur d'une source brute consultée par le moteur Films natif de l'app. */
interface FilmSource {
    /** Identifiant de l'adaptateur natif, déclaré comme `adapter` dans le manifeste. */
    val adapterId: String

    suspend fun trending(config: FilmSourceConfig): List<Video>
    suspend fun search(query: String, config: FilmSourceConfig): List<Video>
    suspend fun related(video: Video, config: FilmSourceConfig): List<Video>
    suspend fun resolve(result: Video, config: FilmSourceConfig): Video?
}

/**
 * Registre du moteur Films : agrège les sources activées par le paquet GitHub.
 * Les fichiers distants ne sont que des paramètres validés, jamais du code exécutable.
 */
object FilmSourceRegistry {
    private val adapters: List<FilmSource> = listOf(InternetArchiveFilmSource)

    suspend fun trending(engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.trending(config) }

    suspend fun search(query: String, engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.search(query, config) }

    suspend fun related(video: Video, engine: FilmEngineConfig): List<Video> =
        collect(engine) { source, config -> source.related(video, config) }

    suspend fun resolve(result: Video, engine: FilmEngineConfig): Video? {
        val config = engine.sources.firstOrNull { it.id == result.sourceId } ?: return null
        val source = adapters.firstOrNull { it.adapterId == config.adapter } ?: return null
        return source.resolve(result, config)
    }

    private suspend fun collect(
        engine: FilmEngineConfig,
        load: suspend (FilmSource, FilmSourceConfig) -> List<Video>,
    ): List<Video> {
        val results = mutableListOf<Video>()
        var sourceFailure: Exception? = null
        for (config in engine.sources) {
            val source = adapters.firstOrNull { it.adapterId == config.adapter } ?: continue
            try {
                results += load(source, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (sourceFailure == null) sourceFailure = error
            }
        }
        if (results.isEmpty()) sourceFailure?.let { throw it }
        return results.distinctBy { video -> "${video.sourceId}:${video.id}" }
    }
}
