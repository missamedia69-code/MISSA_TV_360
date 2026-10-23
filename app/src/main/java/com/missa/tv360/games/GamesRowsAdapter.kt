package com.missa.tv360.games

import android.view.ViewGroup
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import com.missa.tv360.data.GamesRow

/**
 * Adapteur de la grille de jeux : transforme la liste de lignes [GamesRow]
 * (groupes publies par l'engine « jeux » du depot) en lignes Leanback,
 * chaque carte etant rendue par [GameCardPresenter].
 */
class GamesRowsAdapter : ArrayObjectAdapter(
    ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply { shadowEnabled = false }
) {

    fun bind(rows: List<GamesRow>) {
        clear()
        val cardPresenter = GameCardPresenter()
        rows.forEachIndexed { index, row ->
            val cards = ArrayObjectAdapter(cardPresenter)
            row.games.forEach { cards.add(it) }
            add(ListRow(HeaderItem(index.toLong(), row.title), cards))
        }
    }
}
