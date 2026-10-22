package com.missa.tv360.ui.mobile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.extensions.ExtensionCatalogLoader
import com.missa.tv360.extensions.ExtensionsActivity
import com.missa.tv360.games.GamesActivity
import com.missa.tv360.ui.DetailsFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MobileHomeFragment : Fragment() {
    private lateinit var rowsAdapter: MobileRowsAdapter
    private var extensionsStarted = false
    private var refreshExtensionsOnResume = false
    private val resolvingIds = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_mobile_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rowsAdapter = MobileRowsAdapter(
            onVideoClick = ::openVideo,
            onMoreClick = { row -> openCategory(row.rowId, row.title) },
        )
        view.findViewById<RecyclerView>(R.id.mobile_home_rows).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rowsAdapter
        }
        val openSearch = View.OnClickListener { openSearch() }
        view.findViewById<Button>(R.id.mobile_top_search).setOnClickListener(openSearch)
        view.findViewById<Button>(R.id.mobile_nav_search).setOnClickListener(openSearch)
        view.findViewById<Button>(R.id.mobile_nav_home).setOnClickListener {
            view.findViewById<RecyclerView>(R.id.mobile_home_rows).scrollToPosition(0)
        }
        view.findViewById<Button>(R.id.mobile_nav_extensions).setOnClickListener {
            refreshExtensionsOnResume = true
            startActivity(Intent(requireContext(), ExtensionsActivity::class.java))
        }
        render()
        val model = ViewModelProvider(requireActivity())[CatalogViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch { model.state.collect { render() } }
        loadExtensions(bootstrap = true)
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
        val view = view ?: return
        val current = CatalogStore.snapshot()
        val rows = if (current.none { it.rowId == CatalogStore.MY_LIST_ROW_ID }) {
            listOf(CatalogStore.DisplayRow(CatalogStore.MY_LIST_ROW_ID, getString(R.string.row_my_list), emptyList())) + current
        } else current
        rowsAdapter.submitList(rows)
        view.findViewById<TextView>(R.id.mobile_home_empty).isVisible = current.isEmpty()
    }

    private fun loadExtensions(bootstrap: Boolean) {
        if (extensionsStarted && bootstrap) return
        extensionsStarted = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = ExtensionCatalogLoader.load(requireContext(), bootstrap)
                if (!isAdded) return@launch
                CatalogStore.removeExtensions(requireContext())
                CatalogStore.append(requireContext(), rows)
                render()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Le catalogue principal reste utilisable si un dépôt est hors ligne.
            }
        }
    }

    private fun openVideo(video: Video) {
        when {
            video.isGamesLauncher -> startActivity(Intent(requireContext(), GamesActivity::class.java))
            video.sourceId != null && video.streamUrl.isBlank() -> resolveAndOpen(video)
            else -> openDetails(video)
        }
    }

    private fun resolveAndOpen(video: Video) {
        if (!resolvingIds.add(video.id)) return
        Toast.makeText(requireContext(), R.string.search_resolving, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = try {
                val config = FilmEngineRepository.currentOrLoad(requireContext())
                FilmSourceRegistry.resolve(video, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            resolvingIds.remove(video.id)
            if (!isAdded) return@launch
            if (resolved == null) Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            else openDetails(resolved)
        }
    }

    private fun openCategory(rowId: String?, title: String) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, MobileCategoryFragment.newInstance(rowId, title))
            .addToBackStack("mobile-category")
            .commit()
    }

    private fun openSearch() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, MobileSearchFragment())
            .addToBackStack("mobile-search")
            .commit()
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("mobile-details")
            .commit()
    }
}
