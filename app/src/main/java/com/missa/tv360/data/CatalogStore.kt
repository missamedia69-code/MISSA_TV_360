package com.missa.tv360.data

/**
 * Dernier catalogue affiché sur l'écran d'accueil.
 *
 * La grille « matrice » (CategoryGridFragment) est ouverte depuis une rangée :
 * elle a besoin des contenus de cette rangée sans recharger le catalogue. Le
 * Browse dépose donc ici les rangées réellement affichées (placeholders
 * retirés), et la grille les relit par [rowId] ou par titre d'en-tête.
 */
object CatalogStore {

    @Volatile
    private var rows: List<DisplayRow> = emptyList()

    data class DisplayRow(
        val rowId: String?,
        val title: String,
        val videos: List<Video>,
    )

    fun set(displayRows: List<DisplayRow>) {
        rows = displayRows
    }

    /**
     * Ajoute des rangées à la suite de celles déjà déposées (extensions).
     * Les rangées de même identifiant sont remplacées : un rafraîchissement
     * d'extension met à jour la grille sans dupliquer la rangée.
     */
    fun append(displayRows: List<DisplayRow>) {
        if (displayRows.isEmpty()) return
        val incoming = displayRows.associateBy { it.rowId ?: it.title }
        val merged = rows.filterNot { incoming.containsKey(it.rowId ?: it.title) } + displayRows
        rows = merged
    }

    /** Rangées actuellement déposées (lecture défensive). */
    fun snapshot(): List<DisplayRow> = rows

    /** Rangée correspondant à un identifiant ou, à défaut, à un titre. */
    fun row(rowId: String?, title: String?): DisplayRow? {
        val current = rows
        if (rowId != null) {
            current.firstOrNull { it.rowId != null && it.rowId.equals(rowId, ignoreCase = true) }
                ?.let { return it }
        }
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return null
        return current.firstOrNull { it.title.equals(cleanTitle, ignoreCase = true) }
    }
}
