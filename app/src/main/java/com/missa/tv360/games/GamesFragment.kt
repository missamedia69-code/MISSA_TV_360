package com.missa.tv360.games

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
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
        if (game.packageType.equals("apk", ignoreCase = true) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !requireContext().packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                requireContext(),
                R.string.games_allow_unknown_sources,
                Toast.LENGTH_LONG,
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${requireContext().packageName}"),
                ),
            )
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(
                requireContext(),
                getString(R.string.games_downloading, game.title),
                Toast.LENGTH_SHORT,
            ).show()
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
