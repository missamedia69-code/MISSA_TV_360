package com.missa.tv360.games

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.Game
import com.missa.tv360.data.GamesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Parcours jeux D-pad, avec le même téléchargeur vérifié que sur mobile. */
class GamesFragment : RowsSupportFragment() {
    private val rowsAdapter = GamesRowsAdapter()
    private val downloader by lazy { GameDownloader(requireContext().applicationContext) }
    private var games: List<Game> = emptyList()
    private var activeGameId: String? = null
    private var downloadJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter
        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
            _: RowPresenter.ViewHolder, _: Row ->
            if (item is Game) onGameClick(item)
        }
        activity?.findViewById<Button>(R.id.games_cancel)?.setOnClickListener {
            activeGameId?.let(downloader::cancel)
        }
        activity?.findViewById<Button>(R.id.games_retry)?.setOnClickListener {
            val game = games.firstOrNull { it.id == activeGameId }
            if (game != null) confirmDownload(game) else loadGames()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            downloader.state.collect { renderActiveState(it) }
        }
        loadGames()
    }

    private fun loadGames() {
        showStatus(getString(R.string.games_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { GamesRepository.loadGames() }
                games = rows.flatMap { it.games }.distinctBy { it.id }
                rowsAdapter.bind(rows)
                if (games.isEmpty()) showStatus(getString(R.string.games_empty)) else hideStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showStatus(getString(R.string.games_load_error), retry = true)
            }
        }
    }

    private fun onGameClick(game: Game) {
        val state = downloader.state.value[game.id]
        if (state?.running == true) {
            downloader.cancel(game.id)
            return
        }
        if (state?.done == true && downloader.openVerified(game)) return
        confirmDownload(game)
    }

    private fun confirmDownload(game: Game) {
        val size = game.humanSize().ifBlank { getString(R.string.games_size_unknown) }
        AlertDialog.Builder(requireContext())
            .setTitle(game.title)
            .setMessage(getString(R.string.games_confirm_download, game.title, size))
            .setPositiveButton(R.string.games_download) { _, _ ->
                if (hasInstallPermission(game)) startDownload(game)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun hasInstallPermission(game: Game): Boolean {
        if (!game.packageType.equals("apk", true) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            requireContext().packageManager.canRequestPackageInstalls()) return true
        AlertDialog.Builder(requireContext())
            .setTitle(game.title)
            .setMessage(R.string.games_unknown_sources)
            .setPositiveButton(R.string.games_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${requireContext().packageName}")))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return false
    }

    private fun startDownload(game: Game) {
        if (activeGameId?.let { downloader.state.value[it]?.running } == true) {
            showStatus(getString(R.string.games_already_downloading), running = true)
            return
        }
        activeGameId = game.id
        showStatus(getString(R.string.games_download_started, game.title), running = true)
        downloadJob?.cancel()
        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            val error = downloader.downloadAndInstall(game)
            if (!isAdded) return@launch
            if (error != null) showStatus(error, retry = true)
        }
    }

    private fun renderActiveState(states: Map<String, GameDownloader.DownloadState>) {
        val id = activeGameId ?: return
        val state = states[id] ?: return
        when {
            state.running -> showStatus(getString(R.string.games_downloading, state.progress), running = true, progress = state.progress)
            !state.failed.isNullOrBlank() -> showStatus(state.failed!!, retry = true)
            state.done -> showStatus(getString(R.string.games_verified))
        }
    }

    private fun showStatus(text: String, retry: Boolean = false, running: Boolean = false, progress: Int = 0) {
        val activity = activity ?: return
        activity.findViewById<View>(R.id.games_status_panel).isVisible = true
        activity.findViewById<TextView>(R.id.games_status).text = text
        activity.findViewById<ProgressBar>(R.id.games_progress).apply {
            isVisible = running
            this.progress = progress
        }
        activity.findViewById<Button>(R.id.games_cancel).isVisible = running
        activity.findViewById<Button>(R.id.games_retry).isVisible = retry
    }

    private fun hideStatus() {
        if (activeGameId == null) activity?.findViewById<View>(R.id.games_status_panel)?.isVisible = false
    }

    companion object {
        fun newInstance(): GamesFragment = GamesFragment()
    }
}
