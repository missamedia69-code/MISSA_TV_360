package com.missa.tv360.data.source

import com.missa.tv360.data.Video
import kotlinx.coroutines.CancellationException

/** Source de films consultable à la demande par la recherche de l'application. */
interface FilmSource {
    /** Identifiant stable utilisé pour résoudre une fiche issue de cette source. */
    val id: String

    suspend fun search(query: String): List<Video>

    /** Résout un résultat en fiche lisible (flux + métadonnées complètes). */
    suspend fun resolve(result: Video): Video?
}

/**
 * Registre des sources brutes du moteur Films.
 * Ajouter un fournisseur ici permet de combiner ses résultats avec les autres.
 */
object FilmSourceRegistry {
    private val sources: List<FilmSource> = listOf(InternetArchiveFilmSource)

    suspend fun search(query: String): List<Video> {
        val matches = mutableListOf<Video>()
        var sourceFailure: Exception? = null
        for (source in sources) {
            try {
                matches += source.search(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (sourceFailure == null) sourceFailure = error
            }
        }
        if (matches.isEmpty()) sourceFailure?.let { throw it }
        return matches.distinctBy { video -> "${video.sourceId}:${video.id}" }
    }

    suspend fun resolve(result: Video): Video? =
        sources.firstOrNull { source -> source.id == result.sourceId }
            ?.resolve(result)
}
