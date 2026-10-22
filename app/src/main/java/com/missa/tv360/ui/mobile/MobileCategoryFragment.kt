package com.missa.tv360.ui.mobile

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.CatalogViewModel
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.ui.DetailsFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MobileCategoryFragment : Fragment() {
    private lateinit var adapter: MobileVideoAdapter
    private val resolvingIds = mutableSetOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_mobile_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MobileVideoAdapter(fillWidth = true, onClick = ::openVideo)
        view.findViewById<RecyclerView>(R.id.mobile_category_grid).apply {
            layoutManager = GridLayoutManager(requireContext(), spanCount())
            adapter = this@MobileCategoryFragment.adapter
            addItemDecoration(SpacingDecoration(8))
        }
        view.findViewById<Button>(R.id.mobile_category_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        render()
        val model = ViewModelProvider(requireActivity())[CatalogViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch { model.state.collect { render() } }
    }

    override fun onResume() {
        super.onResume()
        CatalogStore.refreshUserRows(requireContext())
        render()
    }

    private fun render() {
        val view = view ?: return
        if (!::adapter.isInitialized) return
        val row = CatalogStore.row(arguments?.getString(ARG_ROW_ID), arguments?.getString(ARG_TITLE))
        val videos = row?.videos.orEmpty()
        val titleArg = arguments?.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.mobile_category_title).text =
            titleArg.ifBlank { row?.title.orEmpty() }
        view.findViewById<TextView>(R.id.mobile_category_count).text =
            resources.getQuantityString(R.plurals.category_count, videos.size, videos.size)
        view.findViewById<TextView>(R.id.mobile_category_empty).apply {
            isVisible = videos.isEmpty()
            text = getString(
                if (row?.rowId == CatalogStore.MY_LIST_ROW_ID) R.string.empty_my_list else R.string.empty_category,
            )
        }
        adapter.submitList(videos)
    }

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 144f).toInt().coerceIn(2, 6)
    }

    private fun openVideo(video: Video) {
        if (video.streamUrl.isNotBlank() || video.sourceId == null) {
            if (video.streamUrl.isBlank()) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_SHORT).show()
            } else openDetails(video)
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
            if (resolved == null) Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            else openDetails(resolved)
        }
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("mobile-details")
            .commit()
    }

    private class SpacingDecoration(private val spacingDp: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val spacing = (spacingDp * view.resources.displayMetrics.density).toInt() / 2
            outRect.set(spacing, spacing, spacing, spacing)
        }
    }

    companion object {
        private const val ARG_ROW_ID = "arg_row_id"
        private const val ARG_TITLE = "arg_title"
        fun newInstance(rowId: String?, title: String) = MobileCategoryFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROW_ID, rowId)
                putString(ARG_TITLE, title)
            }
        }
    }
}
