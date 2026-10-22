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
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.CatalogViewModel
import kotlinx.coroutines.launch

/** En-tête et état vide autour de la grille Leanback. */
class CategoryFragment : Fragment() {
    private val rowId: String? get() = arguments?.getString(ARG_ROW_ID)
    private val categoryTitle: String get() = arguments?.getString(ARG_TITLE).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.category_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.category_grid_container, CategoryGridFragment.newInstance(rowId, categoryTitle))
                .commit()
        }
        render()
        val model = ViewModelProvider(requireActivity())[CatalogViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch { model.state.collect { render() } }
    }

    override fun onResume() {
        super.onResume()
        // Les stores utilisateur peuvent avoir changé dans la fiche sans
        // émettre un nouveau catalogue distant : réafficher les données locales.
        CatalogStore.refreshUserRows(requireContext())
        render()
    }

    private fun render() {
        val currentView = view ?: return
        val row = CatalogStore.row(rowId, categoryTitle)
        val count = row?.videos?.size ?: 0
        currentView.findViewById<TextView>(R.id.category_title).text =
            categoryTitle.ifBlank { row?.title.orEmpty() }
        currentView.findViewById<TextView>(R.id.category_count).text =
            resources.getQuantityString(R.plurals.category_count, count, count)
        currentView.findViewById<View>(R.id.category_empty).isVisible = count == 0
        currentView.findViewById<View>(R.id.category_grid_container).isVisible = count > 0
        (childFragmentManager.findFragmentById(R.id.category_grid_container) as? CategoryGridFragment)
            ?.bind(row)
    }

    companion object {
        private const val ARG_ROW_ID = "arg_row_id"
        private const val ARG_TITLE = "arg_title"
        fun newInstance(rowId: String?, title: String) = CategoryFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROW_ID, rowId)
                putString(ARG_TITLE, title)
            }
        }
    }
}
