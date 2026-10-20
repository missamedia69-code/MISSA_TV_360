package com.missa.tv360.ui

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.app.SearchSupportFragment.SearchResultProvider
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recherche Films Leanback : clavier TV, saisie vocale et résultats navigables. */
class FilmSearchFragment : SearchSupportFragment(), SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var engineConfig: FilmEngineConfig? = null
    private var searchJob: Job? = null
    private var requestVersion = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(getString(R.string.search_title))
        setSearchResultProvider(this)
        setOnItemViewClickedListener(OnItemViewClickedListener {
                _: Presenter.ViewHolder, item: Any, _: RowPresenter.ViewHolder, _: Row ->
            if (item is Video) openSearchResult(item)
        })
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        scheduleSearch(newQuery, submitted = false)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        scheduleSearch(query, submitted = true)
        return true
    }

    private fun scheduleSearch(rawQuery: String, submitted: Boolean) {
        val query = rawQuery.trim()
        val version = ++requestVersion
        searchJob?.cancel()
        rowsAdapter.clear()
        if (query.isBlank()) return

        searchJob = lifecycleScope.launch {
            if (!submitted) delay(300)
            val appContext = context?.applicationContext ?: return@launch
            try {
                val config = engineConfig ?: FilmEngineRepository.currentOrLoad(appContext)
                    .also { engineConfig = it }
                val results = FilmSourceRegistry.search(query, config)
                if (!isAdded || version != requestVersion) return@launch
                if (results.isEmpty()) {
                    if (submitted) showMessage(R.string.search_no_results)
                    return@launch
                }

                // Même famille visuelle que l'accueil (§8) : cartes MISSA.
                val cards = ArrayObjectAdapter(MissaCardPresenter()).apply {
                    results.forEach { add(it) }
                }
                rowsAdapter.add(
                    ListRow(
                        HeaderItem(0L, getString(R.string.search_results_header, query, results.size)),
                        cards,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (submitted && isAdded && version == requestVersion) {
                    showMessage(R.string.search_error)
                }
            }
        }
    }

    private fun openSearchResult(result: Video) {
        val appContext = context?.applicationContext ?: return
        showMessage(R.string.search_resolving)
        lifecycleScope.launch {
            val resolved = try {
                val config = engineConfig ?: FilmEngineRepository.currentOrLoad(appContext)
                    .also { engineConfig = it }
                FilmSourceRegistry.resolve(result, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isAdded) return@launch
            if (resolved == null) {
                showMessage(R.string.search_unplayable)
            } else {
                val details = DetailsFragment.newInstance(resolved)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.main_browse_container, details)
                    .addToBackStack("search-details")
                    .commit()
            }
        }
    }

    private fun showMessage(messageRes: Int) {
        context?.let { Toast.makeText(it, messageRes, Toast.LENGTH_LONG).show() }
    }
}
