package com.missa.tv360.ui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.playback.PlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Fiche de contenu avec métadonnées complètes et suggestions calculées par le moteur. */
class DetailsFragment : DetailsSupportFragment() {

    companion object {
        private const val ARG_VIDEO = "arg_video"
        private const val ACTION_PLAY = 1L

        fun newInstance(video: Video): DetailsFragment = DetailsFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_VIDEO, video) }
        }
    }

    private lateinit var video: Video

    private class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
            val v = item as Video
            val metadata = buildList {
                add(v.kind.label)
                v.releaseYear?.takeIf { it.isNotBlank() }?.let { add(it) }
                v.creator?.takeIf { it.isNotBlank() }?.let { add("Par $it") }
                if (v.isSeries) add("S${v.season ?: 1} · E${v.episode ?: 1}")
                else if (v.isLive) add("EN DIRECT")
                v.durationSec?.let { seconds ->
                    add(if (seconds >= 3600) "${seconds / 3600} h ${(seconds % 3600) / 60} min" else "${seconds / 60} min")
                }
                if (v.isAudioOnly) add("Audio")
                v.tags.take(4).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
            }.joinToString("  •  ")
            viewHolder.title.text = if (v.isSeries) {
                "${v.seriesTitle} — S${v.season ?: 1} E${v.episode ?: 1}"
            } else v.title
            viewHolder.body.text = if (v.description.isBlank()) metadata else "$metadata\n\n${v.description}"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        video = requireArguments().getSerializable(ARG_VIDEO) as Video

        val overviewPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()).apply {
            onActionClickedListener = androidx.leanback.widget.OnActionClickedListener { action ->
                if (action.id == ACTION_PLAY) openPlayer()
            }
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.missa_navy)
        }
        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(DetailsOverviewRow::class.java, overviewPresenter)
            addClassPresenter(ListRow::class.java, ListRowPresenter())
        }

        val overviewRow = DetailsOverviewRow(video).apply {
            val artworkRes = if (video.cardRes != 0) video.cardRes else fondParDefaut(video.kind.name)
            ContextCompat.getDrawable(requireContext(), artworkRes)?.let { imageDrawable = it }
            actionsAdapter = ArrayObjectAdapter().apply {
                val actionLabel = when {
                    video.isAudioOnly -> getString(R.string.action_listen)
                    video.isLive -> getString(R.string.action_watch_live)
                    else -> getString(R.string.action_watch)
                }
                add(Action(ACTION_PLAY, actionLabel))
            }
        }

        val detailsAdapter = ArrayObjectAdapter(presenterSelector).apply { add(overviewRow) }
        adapter = detailsAdapter
        onItemViewClickedListener = OnItemViewClickedListener {
                _: Presenter.ViewHolder, item: Any, _: RowPresenter.ViewHolder, _: Row ->
            if (item is Video) openRelatedVideo(item)
        }

        video.cardUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ThumbnailLoader.loadArtwork(url) { bitmap ->
                // Ignore a late response after the details view has been recreated.
                if (!isAdded || this.view !== view) return@loadArtwork
                overviewRow.imageDrawable = BitmapDrawable(resources, bitmap)
                detailsAdapter.notifyArrayItemRangeChanged(0, 1)
            }
        }

        if (video.kind == ContentKind.MOVIE) loadRelatedFilms(view, detailsAdapter)
    }

    private fun loadRelatedFilms(view: View, detailsAdapter: ArrayObjectAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            val suggestions = try {
                val config = FilmEngineRepository.currentOrLoad(requireContext())
                FilmSourceRegistry.related(video, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (!isAdded || this@DetailsFragment.view !== view || suggestions.isEmpty()) return@launch

            val cards = ArrayObjectAdapter(CardPresenter()).apply {
                suggestions.forEach { add(it) }
            }
            detailsAdapter.add(
                ListRow(
                    HeaderItem(1L, getString(R.string.related_films_header)),
                    cards,
                ),
            )
        }
    }

    private fun openRelatedVideo(searchResult: Video) {
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = try {
                val config = FilmEngineRepository.currentOrLoad(requireContext())
                FilmSourceRegistry.resolve(searchResult, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isAdded) return@launch
            if (resolved == null) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            } else {
                val next: Fragment = newInstance(resolved)
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.main_browse_container, next)
                    .addToBackStack("related-details")
                    .commit()
            }
        }
    }

    private fun openPlayer() {
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, video.streamUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, video.title)
                putExtra(PlayerActivity.EXTRA_LIVE, video.isLive)
                putExtra(PlayerActivity.EXTRA_AUDIO_ONLY, video.isAudioOnly)
                putExtra(
                    PlayerActivity.EXTRA_COVER_RES,
                    if (video.cardRes != 0) video.cardRes else fondParDefaut(video.kind.name),
                )
                putExtra(PlayerActivity.EXTRA_COVER_URL, video.cardUrl)
            }
        )
    }
}
