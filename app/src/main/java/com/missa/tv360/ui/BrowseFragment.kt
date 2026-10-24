package com.missa.tv360.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.CatalogViewModel
import com.missa.tv360.data.Video
import com.missa.tv360.extensions.ExtensionCatalogLoader
import com.missa.tv360.extensions.ExtensionManager
import com.missa.tv360.extensions.ExtensionsActivity
import com.missa.tv360.games.GamesActivity
import com.missa.tv360.ui.mobile.MobileRowsAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Accueil TV avec des vues explicites et navigables au D-pad.
 *
 * Les détails et la recherche conservent leurs composants Leanback. L'accueil
 * utilise des RecyclerView classiques afin que marges, textes et focus ne
 * dépendent plus des transitions de BrowseSupportFragment.
 */
class BrowseFragment : Fragment() {
    private lateinit var rowsAdapter: MobileRowsAdapter
    private var extensionJob: Job? = null
    private var refreshExtensionsOnResume = false
    private var extensionsStarted = false
    private var extensionsLoading = false
    private var extensionsFailed = false
    private var providerCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_tv_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rowsAdapter = MobileRowsAdapter(
            onVideoClick = ::openVideo,
            onMoreClick = { row -> openCategory(row.rowId, row.title) },
            rowLayoutRes = R.layout.tv_catalog_row,
            cardLayoutRes = R.layout.tv_video_card,
        )
        view.findViewById<RecyclerView>(R.id.tv_home_rows).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rowsAdapter
            itemAnimator = null
        }
        view.findViewById<Button>(R.id.tv_search).setOnClickListener { openSearch() }
        val openExtensions = View.OnClickListener { openExtensions() }
        view.findViewById<Button>(R.id.tv_extensions).setOnClickListener(openExtensions)
        view.findViewById<Button>(R.id.tv_empty_extensions).setOnClickListener(openExtensions)

        render()
        val model = ViewModelProvider(requireActivity())[CatalogViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch { model.state.collect { render() } }
        loadExtensions(bootstrap = true)

        if (savedInstanceState == null) {
            view.findViewById<Button>(R.id.tv_extensions).post {
                if (isAdded) view.findViewById<Button>(R.id.tv_extensions).requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CatalogStore.refreshUserRows(requireContext())
        render()
        if (refreshExtensionsOnResume) {
            refreshExtensionsOnResume = false
            loadExtensions(bootstrap = false)
        }
    }

    private fun render() {
        val currentView = view ?: return
        val rows = CatalogStore.snapshot()
        rowsAdapter.submitList(rows)
        currentView.findViewById<RecyclerView>(R.id.tv_home_rows).isVisible = rows.isNotEmpty()
        currentView.findViewById<View>(R.id.tv_home_empty).isVisible = rows.isEmpty()
        currentView.findViewById<View>(R.id.tv_extension_loading).isVisible = extensionsLoading

        val status = currentView.findViewById<TextView>(R.id.tv_extension_status)
        status.isVisible = rows.isNotEmpty() && (extensionsLoading || extensionsFailed)
        status.text = when {
            extensionsLoading -> getString(R.string.extensions_home_loading)
            extensionsFailed -> getString(R.string.extensions_home_failed)
            else -> ""
        }

        val emptyTitle: String
        val emptyBody: String
        when {
            extensionsLoading -> {
                emptyTitle = getString(R.string.tv_home_loading_title)
                emptyBody = getString(R.string.extensions_home_loading)
            }
            extensionsFailed -> {
                emptyTitle = getString(R.string.tv_home_failed_title)
                emptyBody = getString(R.string.extensions_home_failed)
            }
            providerCount > 0 -> {
                emptyTitle = getString(R.string.tv_home_no_rows_title)
                emptyBody = getString(R.string.extensions_home_no_rows, providerCount)
            }
            else -> {
                emptyTitle = getString(R.string.empty_category)
                emptyBody = getString(R.string.extensions_card_description)
            }
        }
        currentView.findViewById<TextView>(R.id.tv_empty_title).text = emptyTitle
        currentView.findViewById<TextView>(R.id.tv_empty_body).text = emptyBody
    }

    private fun loadExtensions(bootstrap: Boolean) {
        if (extensionsStarted && bootstrap) return
        extensionsStarted = true
        extensionJob?.cancel()
        extensionsLoading = true
        extensionsFailed = false
        render()
        extensionJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = ExtensionCatalogLoader.load(requireContext(), bootstrap)
                if (!isAdded) return@launch
                CatalogStore.removeExtensions(requireContext())
                CatalogStore.append(requireContext(), rows)
                providerCount = ExtensionManager.providers().size
                extensionsLoading = false
                extensionsFailed = false
                render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                android.util.Log.w("Browse", "Extensions indisponibles", error)
                if (isAdded) {
                    extensionsLoading = false
                    extensionsFailed = true
                    render()
                }
            }
        }
    }

    private fun openVideo(video: Video) {
        if (video.isGamesLauncher) openGames() else openDetails(video)
    }

    private fun openExtensions() {
        refreshExtensionsOnResume = true
        startActivity(android.content.Intent(requireContext(), ExtensionsActivity::class.java))
    }

    private fun openCategory(rowId: String?, title: String) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, CategoryFragment.newInstance(rowId, title))
            .addToBackStack("category-grid")
            .commit()
    }

    private fun openSearch() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, FilmSearchFragment())
            .addToBackStack("film-search")
            .commit()
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("details")
            .commit()
    }

    private fun openGames() {
        startActivity(android.content.Intent(requireContext(), GamesActivity::class.java))
    }

    override fun onDestroyView() {
        extensionJob = null
        extensionsLoading = false
        super.onDestroyView()
    }
}
