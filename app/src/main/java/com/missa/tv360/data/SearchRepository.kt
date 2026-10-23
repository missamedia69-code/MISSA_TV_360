package com.missa.tv360.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recherche dans le catalogue JSON agrégé, sans adaptateur de fournisseur externe. */
object SearchRepository {
    data class SearchOutcome(val items: List<Video>)

    suspend fun search(query: String): SearchOutcome = withContext(Dispatchers.Default) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext SearchOutcome(emptyList())
        val items = CatalogStore.snapshot()
            .flatMap { row -> row.videos.map { video -> row.title to video } }
            .filter { (category, video) ->
                video.title.contains(clean, true) ||
                    video.description.contains(clean, true) ||
                    video.displayKindLabel.contains(clean, true) ||
                    category.contains(clean, true)
            }
            .map { it.second }
            .distinctBy { it.id }
        SearchOutcome(items)
    }
}
