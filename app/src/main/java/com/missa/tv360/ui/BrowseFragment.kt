package com.missa.tv360.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
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
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.missa.tv360.R
import com.missa.tv360.data.CatalogRepository
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore
import com.missa.tv360.data.VideoRow
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.data.source.bundledFilmEngineConfig
import com.missa.tv360.games.GamesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Écran principal : lignes de contenus (Browse Leanback).
 * Charge le catalogue puis permet une recherche à la demande auprès des sources Films.
 */
class BrowseFragment : BrowseSupportFragment() {

    private val rowPresenter = ListRowPresenter().apply { selectEffectEnabled = false }
    private val rowsAdapter = ArrayObjectAdapter(rowPresenter)
    private var filmListAdapter: ArrayObjectAdapter? = null
    private var engineConfig: FilmEngineConfig? = null
    private var headerIconObserver: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.missa_bg_top)
        adapter = rowsAdapter
        setOnSearchClickedListener { openFilmSearch() }
        installHeaderIcons(view)

        onItemViewClickedListener = OnItemViewClickedListener { _: Presenter.ViewHolder, item: Any,
                                                                _: RowPresenter.ViewHolder, _: Row ->
            if (item is Video) {
                when {
                    item.sourceId != null -> resolveAndOpen(item)
                    item.isGamesLauncher -> openGames()
                    else -> openDetails(item)
                }
            }
        }

        onItemViewSelectedListener = object : androidx.leanback.widget.OnItemViewSelectedListener {
            override fun onItemSelected(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder?,
                row: Row?,
            ) {
                val video = item as? Video ?: return
                val res = fondParDefaut(video.kind.name)
                BackgroundManager.getInstance(requireActivity())?.apply {
                    drawable = ContextCompat.getDrawable(requireContext(), res)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            // Affichage immédiat du catalogue embarqué pour ne jamais bloquer l'UI.
            bindCatalog(CatalogRepository.localCatalog())
            addResumeRow(CatalogRepository.localCatalog())
            requireActivity().findViewById<View>(R.id.catalog_loading)?.visibility = View.GONE
            engineConfig = bundledFilmEngineConfig()

            // Tentative d'enrichissement en arrière-plan (timeout court).
            if (isOnline()) {
                val catalogTask = async {
                    try { withTimeout(4000) { CatalogRepository.loadCatalog() } }
                    catch (_: Exception) { null }
                }
                val engineTask = async {
                    try { withTimeout(4000) { FilmEngineRepository.refresh(appContext) } }
                    catch (_: Exception) { null }
                }
                val remoteCatalog = catalogTask.await()
                if (!remoteCatalog.isNullOrEmpty()) {
                    bindCatalog(remoteCatalog)
                    addResumeRow(remoteCatalog)
                }
                val remoteConfig = engineTask.await()
                if (remoteConfig != null) {
                    engineConfig = remoteConfig
                    val trending = try { FilmSourceRegistry.trending(remoteConfig) } catch (_: Exception) { emptyList() }
                    if (trending.isNotEmpty()) updateFilmRow(trending)
                }
            }
            return@launch
        }
    }

    private fun isOnline(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ------------------------------------------------------------------
    // Icônes de catégorie du pack d'icônes (panneau latéral + en-têtes)
    // ------------------------------------------------------------------

    /**
     * Branche les icônes du pack sur les en-têtes de rangée. RowHeaderPresenter
     * ne relie que le texte : on observe donc l'arbre de vues (le panneau est un
     * fragment enfant, créé après le nôtre) et on couple chaque [TextView] portant
     * l'id `row_header` à l'ImageView frère `row_header_icon` de nos surcharges
     * lb_header / lb_row_header.
     */
    private fun installHeaderIcons(root: View) {
        val observer = ViewTreeObserver.OnGlobalLayoutListener { hookHeaderIcons(root) }
        root.viewTreeObserver.addOnGlobalLayoutListener(observer)
        headerIconObserver = observer
        hookHeaderIcons(root)
    }

    override fun onDestroyView() {
        headerIconObserver?.let { observer ->
            view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(observer)
        }
        headerIconObserver = null
        super.onDestroyView()
    }

    private fun hookHeaderIcons(view: View) {
        if (view is TextView && view.id == R.id.row_header) hookHeaderIcon(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) hookHeaderIcons(view.getChildAt(i))
        }
    }

    /** Un seul branchement par icône ; le TextWatcher couvre les rebindings. */
    private fun hookHeaderIcon(title: TextView) {
        val parent = title.parent as? ViewGroup ?: return
        val icon = parent.findViewById<ImageView>(R.id.row_header_icon) ?: return
        if (icon.tag != null) return
        icon.tag = true
        applyHeaderIcon(icon, title.text)
        title.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyHeaderIcon(icon, title.text)
        })
    }

    private fun applyHeaderIcon(icon: ImageView, label: CharSequence?) {
        val res = headerIconRes(label)
        icon.setImageResource(res)
        icon.visibility = if (res != 0) View.VISIBLE else View.GONE
    }

    /**
     * Icône du pack correspondant au libellé de la rangée. Le pack ne fournit
     * pas d'icône dédiée pour Sport Direct / Podcasts / Musique : ils réutilisent
     * l'icône la plus proche ; tout libellé inconnu n'affiche simplement rien.
     */
    private fun headerIconRes(label: CharSequence?): Int {
        val text = label?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return 0
        return listOf(
            getString(R.string.row_resume) to R.drawable.ic_reprendre,
            getString(R.string.row_hero) to R.drawable.ic_a_la_une,
            getString(R.string.row_live_tv) to R.drawable.ic_tv_direct,
            getString(R.string.row_sport) to R.drawable.ic_tv_direct,
            getString(R.string.row_films) to R.drawable.ic_films,
            getString(R.string.row_series) to R.drawable.ic_series,
            getString(R.string.row_shorts) to R.drawable.ic_films_courts,
            getString(R.string.row_radio) to R.drawable.ic_radio,
            getString(R.string.row_podcast) to R.drawable.ic_radio,
            getString(R.string.row_music) to R.drawable.ic_radio,
            getString(R.string.row_kids) to R.drawable.ic_jeunesse,
            getString(R.string.row_games) to R.drawable.ic_jeux_video,
        ).firstOrNull { it.first.equals(text, ignoreCase = true) }?.second ?: 0
    }

    private fun addResumeRow(catalog: List<VideoRow>) {
        val recentIds = WatchProgressStore.recent(requireContext())
        if (recentIds.isEmpty()) return
        val videosById = catalog.flatMap { it.videos }.associateBy { it.id }
        val resumeVideos = recentIds.mapNotNull { videosById[it] }
        if (resumeVideos.isEmpty()) return
        // Évite les doublons : retire une éventuelle ligne « Reprendre » existante.
        val existing = (0 until rowsAdapter.size()).mapNotNull { rowsAdapter.get(it) as? ListRow }
            .firstOrNull { it.headerItem?.name == getString(R.string.row_resume) }
        existing?.let { rowsAdapter.remove(it) }
        val adapter = ArrayObjectAdapter(MissaCardPresenter()).apply { resumeVideos.forEach { add(it) } }
        rowsAdapter.add(0, ListRow(HeaderItem(999L, getString(R.string.row_resume)), adapter))
    }

    private fun bindCatalog(catalog: List<VideoRow>) {
        rowsAdapter.clear()
        filmListAdapter = null
        val cardPresenter = MissaCardPresenter()
        catalog.forEachIndexed { index, row ->
            val listAdapter = ArrayObjectAdapter(cardPresenter)
            row.videos.filter { !it.title.contains("Emplacement", ignoreCase = true) }
                .forEach { listAdapter.add(it) }
            // Pas de zone vide : une rangée dont tous les contenus sont des
            // placeholders (« Emplacement ») n'est pas ajoutée.
            if (listAdapter.size() == 0) return@forEachIndexed
            val isFilms = row.rowId == "films" || row.headerRes == R.string.row_films ||
                row.headerTitle.equals("Films", ignoreCase = true)
            val header = row.headerTitle
                ?: if (row.headerRes != 0) getString(row.headerRes) else ""
            if (isFilms) filmListAdapter = listAdapter
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), header), listAdapter))
        }
        val heroVideos = catalog.flatMap { it.videos }
            .filter { !it.title.contains("Emplacement", ignoreCase = true) }
            .take(6)
        if (heroVideos.isNotEmpty()) {
            val heroAdapter = ArrayObjectAdapter(MissaCardPresenter()).apply { heroVideos.forEach { add(it) } }
            rowsAdapter.add(0, ListRow(HeaderItem(1000L, getString(R.string.row_hero)), heroAdapter))
        }
        startHeadersTransition(true)
    }

    private fun updateFilmRow(videos: List<Video>) {
        val existingAdapter = filmListAdapter
        if (existingAdapter == null) {
            val newAdapter = ArrayObjectAdapter(MissaCardPresenter()).apply {
                videos.forEach { add(it) }
            }
            rowsAdapter.add(
                0,
                ListRow(
                    HeaderItem(rowsAdapter.size().toLong(), getString(R.string.row_films)),
                    newAdapter,
                ),
            )
            filmListAdapter = newAdapter
            return
        }
        existingAdapter.clear()
        videos.forEach { existingAdapter.add(it) }
    }

    /** Le composant Leanback natif fournit le clavier TV et l'entrée vocale. */
    private fun openFilmSearch() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, FilmSearchFragment())
            .addToBackStack("film-search")
            .commit()
    }

    private fun resolveAndOpen(searchResult: Video) {
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
            if (!isAdded) return@launch
            if (resolved == null) {
                Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_LONG).show()
            } else {
                openDetails(resolved)
            }
        }
    }

    /**
     * Ouvre un écran plein dans le MEME conteneur que celui qui héberge le
     * Browse (R.id.main_browse_container). On évite android.R.id.content :
     * comme le Browse n'a pas été ajouté à ce conteneur-là, un « replace »
     * ne le retirerait pas et les deux vues resteraient superposées.
     */
    private fun openDetails(video: Video) {
        val fragment: Fragment = DetailsFragment.newInstance(video)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, fragment)
            .addToBackStack("details")
            .commit()
    }

    /**
     * Ouvre l'écran « Jeux vidéo » dans son Activity dédiée.
     *
     * On ne remplace pas le Browse par un fragment : un BrowseSupportFragment
     * placé sur une back stack n'ajoute pas son fragment enfant de lignes
     * (défaut Leanback connu) et la grille resterait vide. L'Activity dédiée
     * (comme PlayerActivity) offre en plus un retour « back » naturel.
     */
    private fun openGames() {
        startActivity(android.content.Intent(requireContext(), GamesActivity::class.java))
    }
}
