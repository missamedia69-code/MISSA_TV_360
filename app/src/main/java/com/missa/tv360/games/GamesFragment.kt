package com.missa.tv360.games

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.data.Game
import com.missa.tv360.data.GamesRepository
import kotlinx.coroutines.launch

/**
 * Ecran « Jeux video » : affiche les jeux telechargeables publies par
 * l'engine « jeux » du depot GitHub. Clique sur une carte telecharge le
 * fichier, verifie son checksum puis lance l'installation systeme.
 *
 * Widget : [RowsSupportFragment] (et non BrowseSupportFragment). C'est la
 * grille de lignes de Leanback sans volet d'en-tetes — exactement ce qu'il
 * faut ici, et surtout elle ne delegue pas l'affichage a un fragment enfant
 * (ce qui casse le rendu d'un BrowseSupportFragment mis sur une back stack).
 * Le titre « Jeux video » est porte par l'en-tete de ligne (HeaderItem).
 */
class GamesFragment : RowsSupportFragment() {

    private val rowsAdapter = GamesRowsAdapter()
    private val downloader by lazy { GameDownloader(requireContext().applicationContext) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
            _: RowPresenter.ViewHolder, _: Row ->
            if (item is Game) startDownload(item)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            rowsAdapter.bind(GamesRepository.loadGames())
        }
    }

    private fun startDownload(game: Game) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Telechargement de ${game.title}…",
                Toast.LENGTH_SHORT).show()
            val error = downloader.downloadAndInstall(game)
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun newInstance(): GamesFragment = GamesFragment()
    }
}
