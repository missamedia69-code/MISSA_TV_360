package com.missa.tv360.ui

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.CatalogRepository
import com.missa.tv360.data.Video
import com.missa.tv360.games.GamesFragment
import kotlinx.coroutines.launch

/**
 * Écran principal : lignes de contenus (Browse Leanback).
 * Charge le catalogue (BFF distant avec repli local) puis affiche les lignes.
 */
class BrowseFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.missa_blue)
        adapter = rowsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
                                                                _: RowPresenter.ViewHolder, _: Row ->
            if (item is Video) {
                if (item.isGamesLauncher) openGames() else openDetails(item)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val catalog = CatalogRepository.loadCatalog()
            bindCatalog(catalog)
        }
    }

    private fun bindCatalog(catalog: List<com.missa.tv360.data.VideoRow>) {
        rowsAdapter.clear()
        val cardPresenter = CardPresenter()
        catalog.forEachIndexed { index, row ->
            val listAdapter = ArrayObjectAdapter(cardPresenter)
            row.videos.forEach { listAdapter.add(it) }
            val header = row.headerTitle
                ?: if (row.headerRes != 0) getString(row.headerRes) else ""
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), header), listAdapter))
        }
        startHeadersTransition(true)
    }

    private fun openDetails(video: Video) {
        val fragment: Fragment = DetailsFragment.newInstance(video)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("details")
            .commit()
    }

    private fun openGames() {
        val fragment: Fragment = GamesFragment.newInstance()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("games")
            .commit()
    }
}
