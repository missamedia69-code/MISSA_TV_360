package com.missa.tv360.ui

import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video

/**
 * Grille « matrice » d'une catégorie : tous les contenus d'une rangée en tuiles
 * régularisées selon leur type de contenu (Affiches 2:3, Carrés 1:1 ou Paysages 16:9).
 */
class CategoryGridFragment : VerticalGridSupportFragment() {

    companion object {
        private const val ARG_ROW_ID = "arg_row_id"
        private const val ARG_TITLE = "arg_title"
        private const val STATE_POSITION = "state_position"

        fun newInstance(rowId: String?, title: String): CategoryGridFragment =
            CategoryGridFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROW_ID, rowId)
                    putString(ARG_TITLE, title)
                }
            }
    }

    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rowId = arguments?.getString(ARG_ROW_ID)
        val titleArg = arguments?.getString(ARG_TITLE).orEmpty()
        val row = CatalogStore.row(rowId, titleArg)

        val title = titleArg.ifBlank { row?.title.orEmpty() }

        // Forme de tuile et colonnes régularisées en fonction du type de contenu
        val sampleKind = row?.videos?.firstOrNull()?.kind ?: ContentKind.MOVIE
        val shape = CardShape.forKind(sampleKind)

        gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false).apply {
            numberOfColumns = shape.gridColumns
            shadowEnabled = false
        }

        // Adaptateur avec tuiles matrice adaptées au type (16:9, 2:3 ou 1:1)
        val adapter = ArrayObjectAdapter(
            MissaCardPresenter(useGridDimensions = true),
        )
        row?.videos?.forEach { adapter.add(it) }

        // Pas de carte « Tout voir » ici : on est déjà dans la grille complète
        // (et un clic dessus ouvrait une fiche vide).

        // Le titre de la catégorie remplace « MISSA TV 360 » dans l'en-tête.
        if (title.isNotBlank()) setTitle(title)

        if (savedInstanceState != null) {
            selectedPosition = savedInstanceState.getInt(STATE_POSITION, 0)
            setSelectedPosition(selectedPosition)
        }

        setAdapter(adapter)

        if (row != null && row.videos.isNotEmpty()) {
            setOnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
                                            _: RowPresenter.ViewHolder, _: Row ->
                (item as? Video)?.let { openDetails(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_POSITION, selectedPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backgrounds = BackgroundManager.getInstance(requireActivity()).also {
            if (!it.isAttached) it.attach(requireActivity().window)
        }
        var currentRes = 0
        setOnItemViewSelectedListener(
            OnItemViewSelectedListener { _: Presenter.ViewHolder?, item: Any?,
                                         _: RowPresenter.ViewHolder?, _: Row? ->
                if (item != null) {
                    val pos = (adapter as? ArrayObjectAdapter)?.indexOf(item) ?: -1
                    if (pos >= 0) selectedPosition = pos
                }
                val video = item as? Video ?: return@OnItemViewSelectedListener
                val res = fondParDefaut(video.kind.name)
                if (res == currentRes) return@OnItemViewSelectedListener
                currentRes = res
                backgrounds.drawable = ContextCompat.getDrawable(requireContext(), res)
            },
        )
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("details")
            .commit()
    }
}
