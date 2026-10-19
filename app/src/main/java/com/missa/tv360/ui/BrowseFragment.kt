package com.missa.tv360.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.missa.tv360.data.VideoRow
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.games.GamesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Écran principal : lignes de contenus (Browse Leanback).
 * Charge le catalogue puis permet une recherche à la demande auprès des sources Films.
 */
class BrowseFragment : BrowseSupportFragment() {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var searchResultsRow: ListRow? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.missa_blue)
        adapter = rowsAdapter
        onSearchClickedListener = View.OnClickListener { showFilmSearchDialog() }

        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
                                                                _: RowPresenter.ViewHolder, _: Row ->
            if (item is Video) {
                when {
                    item.sourceId != null -> resolveAndOpen(item)
                    item.isGamesLauncher -> openGames()
                    else -> openDetails(item)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val catalog = CatalogRepository.loadCatalog()
            bindCatalog(catalog)
            requireActivity().findViewById<View>(R.id.catalog_loading)?.visibility = View.GONE
        }
    }

    private fun bindCatalog(catalog: List<VideoRow>) {
        rowsAdapter.clear()
        searchResultsRow = null
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

    /** La loupe Leanback ouvre la recherche de films sans ajouter de menu principal. */
    private fun showFilmSearchDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.search_film_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.search_submit) { _, _ ->
                searchFilms(input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun searchFilms(query: String) {
        val cleanedQuery = query.trim()
        if (cleanedQuery.isBlank()) return

        searchResultsRow?.let { rowsAdapter.remove(it) }
        searchResultsRow = null
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), R.string.search_loading, Toast.LENGTH_SHORT).show()
            val results = try {
                FilmSourceRegistry.search(cleanedQuery)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.search_error, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            if (!isAdded) return@launch
            if (results.isEmpty()) {
                Toast.makeText(requireContext(), R.string.search_no_results, Toast.LENGTH_LONG).show()
                return@launch
            }

            searchResultsRow?.let { rowsAdapter.remove(it) }
            val resultsAdapter = ArrayObjectAdapter(CardPresenter()).apply {
                results.forEach { add(it) }
            }
            val row = ListRow(
                HeaderItem(
                    rowsAdapter.size().toLong(),
                    getString(R.string.search_results_header, cleanedQuery, results.size),
                ),
                resultsAdapter,
            )
            rowsAdapter.add(0, row)
            searchResultsRow = row
            setSelectedPosition(0)
        }
    }

    private fun resolveAndOpen(searchResult: Video) {
        Toast.makeText(requireContext(), R.string.search_resolving, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = try {
                FilmSourceRegistry.resolve(searchResult)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isAdded) return@launch
            if (resolved == null) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            } else {
                openDetails(resolved)
            }
        }
    }

    /**
     * Ouvre un écran plein dans le MEME conteneur que celui qui héberge le
     * Browse (R.id.main_browse_container). On évite android.R.id.content :
     * comme le Browse n'a pas été ajouté à ce conteneur-là, un « replace »
     * ne le retirerait pas et les deux vues resteraient superposées.
     */
    private fun openDetails(video: Video) {
        val fragment: Fragment = DetailsFragment.newInstance(video)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, fragment)
            .addToBackStack("details")
            .commit()
    }

    /**
     * Ouvre l'écran « Jeux vidéo » dans son Activity dédiée.
     *
     * On ne remplace pas le Browse par un fragment : un BrowseSupportFragment
     * placé sur une back stack n'ajoute pas son fragment enfant de lignes
     * (défaut Leanback connu) et la grille resterait vide. L'Activity dédiée
     * (comme PlayerActivity) offre en plus un retour « back » naturel.
     */
    private fun openGames() {
        startActivity(android.content.Intent(requireContext(), GamesActivity::class.java))
    }
}
