package com.missa.tv360.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Grille TV d'une catégorie, sans carte récursive ni faux contenu. */
class CategoryGridFragment : VerticalGridSupportFragment() {
    private val cards = ArrayObjectAdapter(MissaCardPresenter(useGridDimensions = true))
    private var selectedPosition = 0
    private var selectedVideoId: String? = null
    private val resolvingIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gridPresenter = VerticalGridPresenter().apply { shadowEnabled = false }
        setAdapter(cards)
        selectedPosition = savedInstanceState?.getInt(STATE_POSITION, 0) ?: 0
        setOnItemViewClickedListener { _, item, _, _ ->
            (item as? Video)?.let(::openVideo)
        }
        bind(CatalogStore.row(arguments?.getString(ARG_ROW_ID), arguments?.getString(ARG_TITLE)))
        if (selectedPosition > 0) setSelectedPosition(selectedPosition)
    }

    fun bind(row: CatalogStore.DisplayRow?) {
        val shape = row?.videos?.firstOrNull()?.let { CardShape.forVideo(it) } ?: CardShape.POSTER
        (gridPresenter as? VerticalGridPresenter)?.numberOfColumns = shape.gridColumns
        val selectedId = selectedVideoId
        cards.clear()
        row?.videos?.forEach(cards::add)
        val restored = row?.videos?.indexOfFirst { it.id == selectedId } ?: -1
        if (restored >= 0) setSelectedPosition(restored)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setOnItemViewSelectedListener { _, item, _, _ ->
            val videos = (0 until cards.size()).mapNotNull { cards.get(it) as? Video }
            val position = videos.indexOfFirst { it === item || it.id == (item as? Video)?.id }
            if (position >= 0) selectedPosition = position
            val video = item as? Video ?: return@setOnItemViewSelectedListener
            selectedVideoId = video.id
            BackgroundManager.getInstance(requireActivity()).drawable =
                ContextCompat.getDrawable(requireContext(), fondParDefaut(video.kind.name))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_POSITION, selectedPosition)
        super.onSaveInstanceState(outState)
    }

    private fun openVideo(video: Video) {
        if (video.isGamesLauncher) {
            startActivity(android.content.Intent(requireContext(), com.missa.tv360.games.GamesActivity::class.java))
            return
        }
        if (video.sourceId == null || video.streamUrl.isNotBlank()) {
            openDetails(video)
            return
        }
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
            if (resolved == null) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            } else openDetails(resolved)
        }
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("details")
            .commit()
    }

    companion object {
        private const val ARG_ROW_ID = "arg_row_id"
        private const val ARG_TITLE = "arg_title"
        private const val STATE_POSITION = "state_position"
        fun newInstance(rowId: String?, title: String) = CategoryGridFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROW_ID, rowId)
                putString(ARG_TITLE, title)
            }
        }
    }
}
