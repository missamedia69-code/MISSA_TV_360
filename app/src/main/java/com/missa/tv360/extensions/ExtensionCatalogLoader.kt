package com.missa.tv360.extensions

import android.content.Context
import com.missa.tv360.data.CatalogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Chargement d'extensions partagé par les accueils TV et tactile. */
object ExtensionCatalogLoader {
    suspend fun load(context: Context, bootstrap: Boolean): List<CatalogStore.DisplayRow> {
        ExtensionManager.init(context)
        if (bootstrap) {
            withContext(Dispatchers.IO) {
                try { ExtensionManager.bootstrapIfNeeded() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (t: Throwable) { android.util.Log.w("Extensions", "Bootstrap indisponible", t) }
            }
        }
        return withTimeout(15_000L) { ExtensionCatalog.rows() }.mapNotNull { row ->
            val title = row.headerTitle
                ?: if (row.headerRes != 0) context.getString(row.headerRes) else ""
            if (row.videos.isEmpty() || title.isBlank()) null
            else CatalogStore.DisplayRow(row.rowId ?: title, title, row.videos)
        }
    }
}
