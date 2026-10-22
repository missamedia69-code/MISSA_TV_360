package com.missa.tv360.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
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
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore
import com.missa.tv360.data.VideoRow
import com.missa.tv360.data.source.FilmEngineConfig
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.data.source.bundledFilmEngineConfig
import com.missa.tv360.extensions.ExtensionCatalog
import com.missa.tv360.extensions.ExtensionManager
import com.missa.tv360.extensions.ExtensionsActivity
import com.missa.tv360.games.GamesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Écran principal : lignes de contenus (Browse Leanback).
 * Charge le catalogue puis permet une recherche à la demande auprès des sources Films.
 */
class BrowseFragment : BrowseSupportFragment() {

    private val rowPresenter = ListRowPresenter().apply {
        selectEffectEnabled = false
        // Pas d'ombre Leanback autour des cartes focalisées : elle dessine un
        // cadre noir flou (ShadowOverlayContainer / Z-shadow) sur le fond marine.
        shadowEnabled = false
    }
    private val rowsAdapter = ArrayObjectAdapter(rowPresenter)
    private var filmListAdapter: ArrayObjectAdapter? = null
    private var engineConfig: FilmEngineConfig? = null

    private companion object {
        const val EXTENSIONS_CARD_ID = "open-extensions"
        const val EXTENSIONS_HEADER_ID = 2000L
        const val EXTENSIONS_TIMEOUT_MS = 15_000L
    }
    private var headerIconObserver: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onInflateTitleView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.lb_browse_title, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        title = getString(R.string.app_name)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_app_logo)
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
                    item.id == EXTENSIONS_CARD_ID -> openExtensions()
                    item.moreTargetRowId != null -> openCategoryGrid(item.moreTargetRowId, item.description)
                    item.sourceId != null -> resolveAndOpen(item)
                    item.isGamesLauncher -> openGames()
                    else -> openDetails(item)
                }
            }
        }

        // Clic sur le TITRE d'une rangée : ouvre la même grille complète.
        setOnItemViewSelectedListener(null)

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
            bindCatalog(CatalogRepository.localCatalog(), first = true)
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
                    // Mise à jour EN PLACE : remplacer l'adaptateur des rangées
                    // faisait revenir le défilement au début si l'utilisateur
                    // naviguait pendant le chargement du catalogue distant.
                    bindCatalog(remoteCatalog, first = false)
                }
                val remoteConfig = engineTask.await()
                if (remoteConfig != null) {
                    engineConfig = remoteConfig
                    val trending = try { FilmSourceRegistry.trending(remoteConfig) } catch (_: Exception) { emptyList() }
                    if (trending.isNotEmpty()) updateFilmRow(trending)
                }
            }

            // Extensions : au premier démarrage, bootstrap du dépôt intégré
            // (téléchargement + installation + chargement), puis publication
            // des rangées fournies par les extensions actives.
            loadExtensions()

            return@launch
        }
    }

    // ------------------------------------------------------------------
    // Extensions (plugins téléchargés depuis un dépôt)
    // ------------------------------------------------------------------

    /**
     * Charge les extensions puis ajoute leurs rangées à l'accueil.
     *
     * Le bootstrap (premier démarrage) est exécuté sur le dispatcher IO : il
     * télécharge `repo.json`, la liste des extensions et les artefacts, ce qui
     * ne doit jamais toucher le thread principal.
     */
    private suspend fun loadExtensions() {
        try {
            ExtensionManager.init(requireContext())
            withContext(Dispatchers.IO) {
                try {
                    ExtensionManager.bootstrapIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    android.util.Log.w("Browse", "Bootstrap des extensions impossible", t)
                }
            }
            val rows = try {
                withTimeout(EXTENSIONS_TIMEOUT_MS) { ExtensionCatalog.rows() }
            } catch (_: Exception) {
                emptyList()
            }
            if (!isAdded) return
            appendRows(rows)
            appendExtensionsCard()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w("Browse", "Extensions indisponibles", t)
        }
    }

    /** Ajoute des rangées sans toucher à celles déjà affichées. */
    private fun appendRows(rows: List<VideoRow>) {
        if (rows.isEmpty()) return
        val cardPresenter = MissaCardPresenter()
        val displayRows = mutableListOf<CatalogStore.DisplayRow>()

        rows.forEach { row ->
            val videos = row.videos.filter { !it.title.contains("Emplacement", ignoreCase = true) }
            if (videos.isEmpty()) return@forEach
            val header = row.headerTitle ?: if (row.headerRes != 0) getString(row.headerRes) else ""
            if (header.isBlank()) return@forEach
            // Rangée déjà présente (rafraîchissement) : on remplace son contenu.
            val existing = existingRow(header)
            val adapter = ArrayObjectAdapter(cardPresenter).apply { videos.forEach { add(it) } }
            if (existing == null) {
                val position = rowsAdapter.size().toLong()
                rowsAdapter.add(ListRow(HeaderItem(position, header), adapter))
            } else {
                @Suppress("UNCHECKED_CAST")
                val current = existing.adapter as ArrayObjectAdapter
                current.clear()
                videos.forEach { current.add(it) }
            }
            displayRows += CatalogStore.DisplayRow(row.rowId ?: header, header, videos)
        }
        CatalogStore.append(displayRows)
    }

    /**
     * Carte d'accès permanent à l'écran Extensions : sans elle, un utilisateur
     * de TV n'aurait aucun moyen d'ajouter un dépôt.
     */
    private fun appendExtensionsCard() {
        val header = getString(R.string.extensions_title)
        val existing = existingRow(header)
        val card = Video(
            id = EXTENSIONS_CARD_ID,
            title = getString(R.string.extensions_card_title),
            description = getString(R.string.extensions_card_description),
            cardRes = R.drawable.card_more,
            streamUrl = "",
        )
        if (existing == null) {
            val adapter = ArrayObjectAdapter(MissaCardPresenter()).apply { add(card) }
            rowsAdapter.add(ListRow(HeaderItem(EXTENSIONS_HEADER_ID, header), adapter))
        } else {
            @Suppress("UNCHECKED_CAST")
            val current = existing.adapter as ArrayObjectAdapter
            if (current.size() == 0) current.add(card)
        }
    }

    private fun openExtensions() {
        startActivity(Intent(requireContext(), ExtensionsActivity::class.java))
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

    private fun addResumeRow(catalog: List<VideoRow>, first: Boolean) {
        val recentIds = WatchProgressStore.recent(requireContext())
        if (recentIds.isEmpty()) return
        val videosById = catalog.flatMap { it.videos }.associateBy { it.id }
        val resumeVideos = recentIds.mapNotNull { videosById[it] }
        // Évite les doublons : retire une éventuelle ligne « Reprendre » existante.
        existingRow(getString(R.string.row_resume))?.let { rowsAdapter.remove(it) }
        if (resumeVideos.isEmpty()) return
        val adapter = ArrayObjectAdapter(MissaCardPresenter()).apply { resumeVideos.forEach { add(it) } }
        val row = ListRow(HeaderItem(999L, getString(R.string.row_resume)), adapter)
        // Enrichissement : la ligne revient à sa place (tête de liste) sans
        // toucher aux autres rangées — l'utilisateur peut avoir navigué.
        if (first) rowsAdapter.add(0, row) else rowsAdapter.add(0, row)
    }

    /** Rangée affichée portant ce libellé d'en-tête (null si absente). */
    private fun existingRow(header: String): ListRow? =
        (0 until rowsAdapter.size()).mapNotNull { rowsAdapter.get(it) as? ListRow }
            .firstOrNull { it.headerItem?.name == header }

    /**
     * Affiche le catalogue.
     *
     * @param first premier affichage (catalogue embarqué) : les rangées sont
     *              créées, la transition d'en-têtes est lancée et la ligne
     *              « Reprendre » est posée. Sur un affichage ultérieur
     *              (catalogue distant), les contenus sont remplacés EN PLACE :
     *              reconstruire l'adaptateur remettait le défilement au début.
     */
    private fun bindCatalog(catalog: List<VideoRow>, first: Boolean) {
        val cardPresenter = MissaCardPresenter()
        val displayRows = mutableListOf<CatalogStore.DisplayRow>()

        catalog.forEachIndexed { index, row ->
            val videos = row.videos.filter { !it.title.contains("Emplacement", ignoreCase = true) }
            // Pas de zone vide : une rangée dont tous les contenus sont des
            // placeholders (« Emplacement ») n'est pas ajoutée.
            if (videos.isEmpty()) return@forEachIndexed

            val header = row.headerTitle
                ?: if (row.headerRes != 0) getString(row.headerRes) else ""
            // Identifiant stable : celui du dépôt, sinon l'index de la rangée.
            val rowKey = row.rowId ?: "row-$index"
            // La rangée « Jeux vidéo » n'a qu'une carte lanceur : pas de grille.
            val isGames = row.rowId == "jeux" || row.rowId == "games" ||
                row.headerRes == R.string.row_games ||
                header.equals(getString(R.string.row_games), ignoreCase = true)
            val key = if (row.rowId != null) row.rowId else rowKey
            displayRows += CatalogStore.DisplayRow(key, header, videos)

            val existing = existingRow(header)
            if (existing == null) {
                val listAdapter = ArrayObjectAdapter(cardPresenter)
                videos.forEach { listAdapter.add(it) }
                if (!isGames) listAdapter.add(moreCard(key, header))
                rowsAdapter.add(ListRow(HeaderItem(index.toLong(), header), listAdapter))
            } else {
                // Même rangée : on ne remplace que les contenus.
                @Suppress("UNCHECKED_CAST")
                val listAdapter = existing.adapter as ArrayObjectAdapter
                listAdapter.clear()
                videos.forEach { listAdapter.add(it) }
                if (!isGames) listAdapter.add(moreCard(key, header))
            }
        }

        CatalogStore.set(displayRows)

        val heroVideos = catalog.flatMap { it.videos }
            .filter { !it.title.contains("Emplacement", ignoreCase = true) }
            .take(6)
        if (heroVideos.isNotEmpty() && existingRow(getString(R.string.row_hero)) == null) {
            val heroAdapter = ArrayObjectAdapter(MissaCardPresenter()).apply { heroVideos.forEach { add(it) } }
            rowsAdapter.add(0, ListRow(HeaderItem(1000L, getString(R.string.row_hero)), heroAdapter))
        }

        if (first) addResumeRow(catalog, first = true)
        if (first) startHeadersTransition(true)
    }

    /** Carte de fin de rangée ouvrant la grille complète de la catégorie. */
    private fun moreCard(rowId: String, header: String): Video = Video(
        id = "more-$rowId",
        title = getString(R.string.row_more),
        description = header,
        cardRes = R.drawable.card_more,
        streamUrl = "",
        kind = com.missa.tv360.data.ContentKind.MOVIE,
        moreTargetRowId = rowId,
    )

    /** Ouvre la grille « matrice » de la rangée (tuiles réduites, tous contenus). */
    private fun openCategoryGrid(rowId: String?, header: String) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, CategoryGridFragment.newInstance(rowId, header))
            .addToBackStack("category-grid")
            .commit()
    }

    private fun updateFilmRow(videos: List<Video>) {
        val header = getString(R.string.row_films)
        @Suppress("UNCHECKED_CAST")
        val existingAdapter = existingRow(header)?.adapter as? ArrayObjectAdapter
        if (existingAdapter == null) {
            val newAdapter = ArrayObjectAdapter(MissaCardPresenter()).apply {
                videos.forEach { add(it) }
                add(moreCard("films", header))
            }
            rowsAdapter.add(0, ListRow(HeaderItem(rowsAdapter.size().toLong(), header), newAdapter))
            filmListAdapter = newAdapter
            return
        }
        filmListAdapter = existingAdapter
        existingAdapter.clear()
        videos.forEach { existingAdapter.add(it) }
        existingAdapter.add(moreCard("films", header))
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
