package com.missa.tv360.data

import android.content.Context
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmSourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            runCatching { FilmSourceRegistry.search(clean, config) }.getOrNull()
        } else null
        SearchOutcome(
            items = (local + remote.orEmpty()).distinctBy { it.id },
            remoteFailed = remote == null,
        )
    }
}
