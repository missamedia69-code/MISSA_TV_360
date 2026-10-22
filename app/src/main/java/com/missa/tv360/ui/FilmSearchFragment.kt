package com.missa.tv360.ui

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.SearchRepository
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recherche TV locale et distante avec états explicites et clavier/voix Leanback. */
class FilmSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply { shadowEnabled = false })
    private var engineConfig: FilmEngineConfig? = null
    private var searchJob: Job? = null
    private var requestVersion = 0L
    private var lastQuery = ""
    private val resolvingIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(getString(R.string.search_title))
        setSearchResultProvider(this)
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Video -> openSearchResult(item)
                is SearchStatus -> if (item.retry) scheduleSearch(lastQuery, submitted = true)
            }
        }
        showStatus(SearchStatus(getString(R.string.search_initial)))
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
        lastQuery = query
        val version = ++requestVersion
        searchJob?.cancel()
        if (query.isBlank()) {
            showStatus(SearchStatus(getString(R.string.search_initial)))
            return
        }
        showStatus(SearchStatus(getString(R.string.search_loading)))
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!submitted) delay(300)
            try {
                val appContext = requireContext().applicationContext
                val config = engineConfig ?: FilmEngineRepository.currentOrLoad(appContext)
                    .also { engineConfig = it }
                val outcome = SearchRepository.search(appContext, query, config)
                if (!isAdded || version != requestVersion) return@launch
                renderOutcome(query, outcome)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isAdded && version == requestVersion) {
                    showStatus(SearchStatus(getString(R.string.search_error), retry = true))
                }
            }
        }
    }

    private fun renderOutcome(query: String, outcome: SearchRepository.SearchOutcome) {
        rowsAdapter.clear()
        if (outcome.remoteFailed) {
            addStatusRow(SearchStatus(getString(R.string.search_error), retry = true))
        }
        if (outcome.items.isEmpty()) {
            if (!outcome.remoteFailed) addStatusRow(SearchStatus(getString(R.string.search_no_results)))
            return
        }
        val cards = ArrayObjectAdapter(MissaCardPresenter()).apply {
            outcome.items.forEach(::add)
        }
        rowsAdapter.add(
            ListRow(
                HeaderItem(1L, getString(R.string.search_results_header, query, outcome.items.size)),
                cards,
            ),
        )
    }

    private fun showStatus(status: SearchStatus) {
        rowsAdapter.clear()
        addStatusRow(status)
    }

    private fun addStatusRow(status: SearchStatus) {
        val adapter = ArrayObjectAdapter(SearchStatusPresenter()).apply { add(status) }
        rowsAdapter.add(ListRow(HeaderItem(0L, ""), adapter))
    }

    private fun openSearchResult(result: Video) {
        if (result.isGamesLauncher) {
            startActivity(android.content.Intent(requireContext(), com.missa.tv360.games.GamesActivity::class.java))
            return
        }
        if (result.streamUrl.isNotBlank() || result.sourceId == null) {
            openDetails(result)
            return
        }
        if (!resolvingIds.add(result.id)) return
        Toast.makeText(requireContext(), R.string.search_resolving, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = try {
                val config = engineConfig ?: FilmEngineRepository.currentOrLoad(requireContext())
                    .also { engineConfig = it }
                FilmSourceRegistry.resolve(result, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            resolvingIds.remove(result.id)
            if (!isAdded) return@launch
            if (resolved == null) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            } else openDetails(resolved)
        }
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("search-details")
            .commit()
    }
}
