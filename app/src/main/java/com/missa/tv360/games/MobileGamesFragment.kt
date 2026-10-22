package com.missa.tv360.games

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.Game
import com.missa.tv360.data.GamesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileGamesFragment : Fragment() {
    private val downloader by lazy { GameDownloader(requireContext().applicationContext) }
    private lateinit var adapter: MobileGamesAdapter
    private var games: List<Game> = emptyList()
    private var activeGameId: String? = null
    private var downloadJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_mobile_games, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MobileGamesAdapter(::onGameAction)
        view.findViewById<RecyclerView>(R.id.mobile_games_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MobileGamesFragment.adapter
        }
        activity?.findViewById<Button>(R.id.games_cancel)?.setOnClickListener {
            activeGameId?.let(downloader::cancel)
        }
        activity?.findViewById<Button>(R.id.games_retry)?.setOnClickListener {
            val game = games.firstOrNull { it.id == activeGameId }
            if (game != null) confirmDownload(game) else loadGames()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            downloader.state.collect { states ->
                adapter.updateStates(states)
                renderActiveState(states)
            }
        }
        loadGames()
    }

    private fun loadGames() {
        showStatus(getString(R.string.games_loading))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) { GamesRepository.loadGames() }
                games = rows.flatMap { it.games }.distinctBy { it.id }
                adapter.submitList(games)
                if (games.isEmpty()) showStatus(getString(R.string.games_empty))
                else hideStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showStatus(getString(R.string.games_load_error), retry = true)
            }
        }
    }

    private fun onGameAction(game: Game, state: GameDownloader.DownloadState?) {
        when {
            state?.running == true -> downloader.cancel(game.id)
            state?.done == true -> if (!downloader.openVerified(game)) confirmDownload(game)
            else -> confirmDownload(game)
        }
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
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${requireContext().packageName}")),
                )
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
}
