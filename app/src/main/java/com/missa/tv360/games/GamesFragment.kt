package com.missa.tv360.games

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.Game
import com.missa.tv360.data.GamesRepository
import kotlinx.coroutines.launch

/**
 * Ecran « Jeux video » : affiche les jeux telechargeables publies par
 * l'engine « jeux » du depot GitHub. Clique sur une carte telecharge le
 * fichier, verifie son checksum puis lance l'installation systeme.
 */
class GamesFragment : BrowseSupportFragment() {

    private val rowsAdapter = GamesRowsAdapter()
    private val downloader by lazy { GameDownloader(requireContext().applicationContext) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.row_games)
        headersState = HEADERS_DISABLED
        brandColor = ContextCompat.getColor(requireContext(), R.color.missa_blue)
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
