package com.missa.tv360.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.CatalogViewModel
import com.missa.tv360.data.NetworkStatus
import com.missa.tv360.data.Video
import com.missa.tv360.data.VideoRow
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.data.source.bundledFilmEngineConfig
import com.missa.tv360.extensions.ExtensionsActivity
import com.missa.tv360.games.GamesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Accueil Leanback synchronisé avec le catalogue partagé TV/mobile. */
class BrowseFragment : BrowseSupportFragment() {
    private val rowPresenter = ListRowPresenter().apply {
        setHeaderPresenter(MissaRowHeaderPresenter())
        selectEffectEnabled = false
        shadowEnabled = false
    }
    private val rowsAdapter = ArrayObjectAdapter(rowPresenter)
    private val cardPresenter = MissaCardPresenter()
    private val rowsById = mutableMapOf<String, ListRow>()
    private val boundVideos = mutableMapOf<String, List<Video>>()
    private var extensionsRow: ListRow? = null
    private var extensionJob: Job? = null
    private var refreshExtensionsOnResume = false
    private var extensionsStarted = false
    private var lastCatalog: List<VideoRow>? = null
    private var hasPresentedRows = false
    private var engineConfig: FilmEngineConfig? = null
    private var selectedBackgroundId: String? = null
    private val resolvingIds = mutableSetOf<String>()

    override fun onInflateTitleView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.lb_browse_title, parent, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = getString(R.string.app_name)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_app_logo)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.missa_bg_top)
        adapter = rowsAdapter
        setOnSearchClickedListener { openFilmSearch() }
        // Le BackgroundManager est un singleton par activite : rattacher une seconde fois
        // (retour depuis un detail) leve "Already attached to DecorView".
        BackgroundManager.getInstance(requireActivity()).let { background ->
            if (!background.isAttached) background.attach(requireActivity().window)
        }

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val video = item as? Video ?: return@OnItemViewClickedListener
            when {
                video.id == EXTENSIONS_CARD_ID -> openExtensions()
                video.moreTargetRowId != null -> openCategoryGrid(video.moreTargetRowId, video.description)
                video.isGamesLauncher -> openGames()
                video.sourceId != null && video.streamUrl.isBlank() -> resolveAndOpen(video)
                else -> openDetails(video)
            }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            val video = item as? Video ?: return@OnItemViewSelectedListener
            updateBackground(video)
        }

        val model = ViewModelProvider(requireActivity())[CatalogViewModel::class.java]
        viewLifecycleOwner.lifecycleScope.launch {
            model.state.collect { state ->
                if (state.rows != lastCatalog) {
                    lastCatalog = state.rows
                    syncRows()
                }
            }
        }
        engineConfig = bundledFilmEngineConfig()
        refreshFilmEngine()
        loadExtensions(bootstrap = true)
    }

    override fun onResume() {
        super.onResume()
        CatalogStore.refreshUserRows(requireContext())
        syncRows()
        if (refreshExtensionsOnResume) {
            refreshExtensionsOnResume = false
            loadExtensions(bootstrap = false)
        }
    }

    /** Synchronise uniquement les rangées réellement modifiées pour préserver le focus. */
    private fun syncRows() {
        if (!isAdded) return
        val desired = CatalogStore.snapshot()
        val desiredIds = desired.mapIndexed { index, row -> row.rowId ?: "title-$index-${row.title}" }
        val removed = rowsById.keys - desiredIds.toSet()
        removed.forEach { id ->
            rowsById.remove(id)?.let(rowsAdapter::remove)
            boundVideos.remove(id)
        }

        desired.forEachIndexed { index, displayRow ->
            val id = desiredIds[index]
            val isGames = id == "jeux" || id == "games" || displayRow.videos.firstOrNull()?.isGamesLauncher == true
            val items = displayRow.videos + if (isGames) emptyList() else {
                listOf(moreCard(id, displayRow.title, displayRow.videos.firstOrNull()))
            }
            val existing = rowsById[id]
            if (existing == null) {
                val listAdapter = ArrayObjectAdapter(cardPresenter)
                items.forEach(listAdapter::add)
                val row = ListRow(HeaderItem(id.hashCode().toLong(), displayRow.title), listAdapter)
                rowsById[id] = row
                boundVideos[id] = items
                rowsAdapter.add(index.coerceAtMost(rowsAdapter.size()), row)
            } else if (boundVideos[id] != items) {
                @Suppress("UNCHECKED_CAST")
                val listAdapter = existing.adapter as ArrayObjectAdapter
                listAdapter.clear()
                items.forEach(listAdapter::add)
                boundVideos[id] = items
            }
        }
        appendExtensionsCard()
        if (!hasPresentedRows && rowsAdapter.size() > 0) {
            hasPresentedRows = true
            startHeadersTransition(true)
        }
    }

    private fun moreCard(
        rowId: String,
        header: String,
        sample: Video?,
    ) = Video(
        id = "more-$rowId",
        title = getString(R.string.row_more),
        description = header,
        cardRes = R.drawable.card_more,
        streamUrl = "",
        kind = sample?.kind ?: com.missa.tv360.data.ContentKind.MOVIE,
        shapeHint = sample?.shapeHint,
        moreTargetRowId = rowId,
    )

    private fun appendExtensionsCard() {
        if (extensionsRow != null) return
        val card = Video(
            id = EXTENSIONS_CARD_ID,
            title = getString(R.string.extensions_card_title),
            description = getString(R.string.extensions_card_description),
            cardRes = R.drawable.card_more,
            streamUrl = "",
        )
        val adapter = ArrayObjectAdapter(cardPresenter).apply { add(card) }
        extensionsRow = ListRow(
            HeaderItem(EXTENSIONS_HEADER_ID, getString(R.string.extensions_title)),
            adapter,
        ).also { rowsAdapter.add(it) }
    }

    private fun updateBackground(video: Video) {
        selectedBackgroundId = video.id
        val context = requireContext()
        val fallback = fondParDefaut(video.kind.name)
        BackgroundManager.getInstance(requireActivity()).drawable =
            ContextCompat.getDrawable(context, fallback)
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadDrawable(context, video.cardUrl, fallback) { drawable ->
                if (selectedBackgroundId == video.id && isAdded) {
                    BackgroundManager.getInstance(requireActivity()).drawable = drawable
                }
            }
        }
    }

    private fun refreshFilmEngine() {
        if (!NetworkStatus.isOnline(requireContext())) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                withTimeout(10_000L) {
                    val config = FilmEngineRepository.refresh(requireContext().applicationContext)
                    if (config != null) {
                        engineConfig = config
                        FilmSourceRegistry.trending(config)
                    } else emptyList()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (result.isNotEmpty() && isAdded) {
                CatalogStore.append(
                    requireContext(),
                    listOf(
                        CatalogStore.DisplayRow(
                            "films",
                            getString(R.string.row_films),
                            result,
                        ),
                    ),
                )
                syncRows()
            }
        }
    }

    private fun loadExtensions(bootstrap: Boolean) {
        if (extensionsStarted && bootstrap) return
        extensionsStarted = true
        extensionJob?.cancel()
        extensionJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rows = com.missa.tv360.extensions.ExtensionCatalogLoader.load(
                    requireContext().applicationContext,
                    bootstrap,
                )
                if (!isAdded) return@launch
                CatalogStore.removeExtensions(requireContext())
                CatalogStore.append(requireContext(), rows)
                syncRows()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.w("Browse", "Extensions indisponibles", t)
            } finally {
                if (isAdded) appendExtensionsCard()
            }
        }
    }

    private fun openExtensions() {
        refreshExtensionsOnResume = true
        startActivity(android.content.Intent(requireContext(), ExtensionsActivity::class.java))
    }

    private fun openCategoryGrid(rowId: String?, header: String) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, CategoryFragment.newInstance(rowId, header))
            .addToBackStack("category-grid")
            .commit()
    }

    private fun resolveAndOpen(searchResult: Video) {
        if (!resolvingIds.add(searchResult.id)) return
        Toast.makeText(requireContext(), R.string.search_resolving, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = try {
                val config = engineConfig ?: FilmEngineRepository.currentOrLoad(requireContext())
                FilmSourceRegistry.resolve(searchResult, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            resolvingIds.remove(searchResult.id)
            if (!isAdded) return@launch
            if (resolved == null) Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            else openDetails(resolved)
        }
    }

    private fun openFilmSearch() {
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
        super.onDestroyView()
    }

    private companion object {
        const val EXTENSIONS_CARD_ID = "open-extensions"
        const val EXTENSIONS_HEADER_ID = 2000L
    }
}
