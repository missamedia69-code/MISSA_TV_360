package com.missa.tv360.data

import android.content.Context
import com.missa.tv360.data.json.JsonSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Origine affichée du catalogue agrégé, sans catalogue de démonstration. */
enum class CatalogSource { REMOTE, CACHE, EMPTY }

data class CatalogLoadResult(
    val rows: List<VideoRow>,
    val source: CatalogSource,
    val remoteFailed: Boolean = false,
    val networkFailed: Boolean = false,
)

/** Façade historique des écrans ; toute la lecture est déléguée aux JSON du dépôt. */
object CatalogRepository {
    suspend fun initialCatalog(context: Context): CatalogLoadResult = withContext(Dispatchers.IO) {
        val cached = JsonSourceRepository.cachedCatalog(context)
        if (cached != null) CatalogLoadResult(cached.rows, CatalogSource.CACHE)
        else CatalogLoadResult(emptyList(), CatalogSource.EMPTY)
    }

    suspend fun loadCatalog(context: Context): List<VideoRow> = loadCatalogWithStatus(context).rows

    suspend fun loadCatalogWithStatus(context: Context): CatalogLoadResult = withContext(Dispatchers.IO) {
        val result = JsonSourceRepository.load(context)
        CatalogLoadResult(
            rows = result.catalog.rows,
            source = if (result.usedCache) CatalogSource.CACHE else CatalogSource.REMOTE,
            remoteFailed = result.remoteFailed,
            networkFailed = result.networkFailed,
        )
    }
}
