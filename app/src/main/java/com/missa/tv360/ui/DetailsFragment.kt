package com.missa.tv360.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.playback.PlayerActivity

/**
 * Écran de détails d'un contenu : image, titre, description, bouton "Lire".
 */
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
            val meta = buildString {
                append(v.subtitle())
                v.durationSec?.let { append(" • ").append(it / 60).append(" min") }
                if (v.isAudioOnly) append(" • Audio")
            }
            val seriesLine = if (v.isSeries) "${v.seriesTitle}\n\n" else ""
            viewHolder.title.text = if (v.isSeries) "${v.seriesTitle} — E${v.episode ?: 1}" else v.title
            viewHolder.body.text = "$meta\n\n$seriesLine${v.description}"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        video = requireArguments().getSerializable(ARG_VIDEO) as Video

        val presenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter()).apply {
            onActionClickedListener = androidx.leanback.widget.OnActionClickedListener { action ->
                if (action.id == ACTION_PLAY) openPlayer()
            }
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.missa_navy)
        }

        val overviewRow = DetailsOverviewRow(video).apply {
            ContextCompat.getDrawable(requireContext(), video.cardRes)?.let { imageDrawable = it }
            actionsAdapter = ArrayObjectAdapter().apply {
                add(Action(ACTION_PLAY, getString(R.string.action_play)))
            }
        }

        adapter = ArrayObjectAdapter(presenter).apply { add(overviewRow) }
    }

    private fun openPlayer() {
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, video.streamUrl)
                putExtra(PlayerActivity.EXTRA_TITLE, video.title)
                putExtra(PlayerActivity.EXTRA_LIVE, video.isLive)
                putExtra(PlayerActivity.EXTRA_AUDIO_ONLY, video.isAudioOnly)
                putExtra(PlayerActivity.EXTRA_COVER_RES, video.cardRes)
            }
        )
    }
}
