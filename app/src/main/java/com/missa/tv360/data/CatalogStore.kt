package com.missa.tv360.data

import android.content.Context
import com.missa.tv360.R

/** Dernier catalogue présenté, partagé par accueil, grilles, recherche et formats mobiles. */
object CatalogStore {
    const val RESUME_ROW_ID = "special:resume"
    const val MY_LIST_ROW_ID = "special:my-list"

    @Volatile private var rows: List<DisplayRow> = emptyList()

    data class DisplayRow(
        val rowId: String?,
        val title: String,
        val videos: List<Video>,
    )

    /** Remplace le catalogue principal sans effacer les rangées d'extensions déjà chargées. */
    @Synchronized
    fun replaceCatalog(context: Context, catalog: List<VideoRow>) {
        val appContext = context.applicationContext
        val core = catalog.mapIndexedNotNull { index, row ->
            val videos = row.videos.filterNot { it.title.contains("Emplacement", true) }
            if (videos.isEmpty()) return@mapIndexedNotNull null
            val title = row.headerTitle
                ?: if (row.headerRes != 0) appContext.getString(row.headerRes) else ""
            if (title.isBlank()) return@mapIndexedNotNull null
            DisplayRow(row.rowId ?: "row-$index", title, videos)
        }
        val extensions = rows.filter { it.rowId?.startsWith("ext:") == true }
        rows = withUserRows(appContext, core + extensions)
    }

    /** Ajoute/remplace les rangées d'extensions, puis recalcule les données utilisateur. */
    @Synchronized
    fun append(context: Context, displayRows: List<DisplayRow>) {
        if (displayRows.isEmpty()) return
        val appContext = context.applicationContext
        val incoming = displayRows.associateBy { it.rowId ?: it.title }
        val base = rows.filterNot { incoming.containsKey(it.rowId ?: it.title) } + displayRows
        rows = withUserRows(appContext, base)
    }

    @Synchronized
    fun removeExtensions(context: Context) {
        rows = withUserRows(
            context.applicationContext,
            rows.filterNot { it.rowId?.startsWith("ext:") == true },
        )
    }

    /** Recalcule Reprendre/Ma liste après une action utilisateur ou un retour d'écran. */
    @Synchronized
    fun refreshUserRows(context: Context) {
        val base = rows.filterNot { it.rowId == RESUME_ROW_ID || it.rowId == MY_LIST_ROW_ID }
        rows = withUserRows(context.applicationContext, base)
    }

    fun snapshot(): List<DisplayRow> = rows

    fun row(rowId: String?, title: String?): DisplayRow? {
        val current = rows
        if (rowId != null) {
            current.firstOrNull { it.rowId?.equals(rowId, true) == true }?.let { return it }
        }
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return null
        return current.firstOrNull { it.title.equals(cleanTitle, true) }
    }

    private fun withUserRows(context: Context, source: List<DisplayRow>): List<DisplayRow> {
        val base = source.filterNot { it.rowId == RESUME_ROW_ID || it.rowId == MY_LIST_ROW_ID }
        val videos = base.flatMap { it.videos }.distinctBy { it.id }.associateBy { it.id }

        val resume = WatchProgressStore.recent(context)
            .mapNotNull { videos[it] }
            .filter { !it.isLive && WatchProgressStore.fraction(context, it.id) != null }
        val myList = MyListStore.idsSnapshot(context).mapNotNull { videos[it] }

        return buildList {
            if (resume.isNotEmpty()) {
                add(DisplayRow(RESUME_ROW_ID, context.getString(R.string.row_resume), resume))
            }
            if (myList.isNotEmpty()) {
                add(DisplayRow(MY_LIST_ROW_ID, context.getString(R.string.row_my_list), myList))
            }
            addAll(base)
        }
    }
}
