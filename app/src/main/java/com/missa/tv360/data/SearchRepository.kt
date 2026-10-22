package com.missa.tv360.data

import android.content.Context
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Recherche locale multi-catégories enrichie par le moteur Films déjà présent. */
object SearchRepository {
    data class SearchOutcome(
        val items: List<Video>,
        val remoteFailed: Boolean,
    )

    suspend fun search(
        context: Context,
        query: String,
        config: FilmEngineConfig,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext SearchOutcome(emptyList(), remoteFailed = false)
        val local = CatalogStore.snapshot()
            .flatMap { row -> row.videos.map { video -> row.title to video } }
            .filter { (category, video) ->
                video.title.contains(clean, true) ||
                    video.description.contains(clean, true) ||
                    video.displayKindLabel.contains(clean, true) ||
                    category.contains(clean, true)
            }
            .map { it.second }
        val remote = if (NetworkStatus.isOnline(context)) {
            try {
                withTimeoutOrNull(10_000L) { FilmSourceRegistry.search(clean, config) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
        SearchOutcome(
            items = (local + remote.orEmpty()).distinctBy { it.id },
            remoteFailed = remote == null,
        )
    }
}
