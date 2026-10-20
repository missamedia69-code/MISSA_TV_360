package com.missa.tv360.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogRepository
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.MyListStore
import com.missa.tv360.data.ReportStore
import com.missa.tv360.data.Video
import com.missa.tv360.data.source.FilmEngineRepository
import com.missa.tv360.data.source.FilmSourceRegistry
import com.missa.tv360.playback.PlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Fiche détail (maquette « Fiche détail ») : fond plein écran avec dégradés,
 * illustration + badge direct, titre, description, source/licence, actions
 * (Lire / Ma liste / Signaler) et ligne de contenus similaires.
 *
 * Fragments « plain » (pas Leanback Details) : la grille d'accueil reste intacte
 * dans la pile précédente, le retour télécommande ne la recharge pas.
 */
class DetailsFragment : Fragment() {

    companion object {
        private const val ARG_VIDEO = "arg_video"
        private const val ACTION_PLAY = 1L
        private const val ACTION_MY_LIST = 2L
        private const val ACTION_REPORT = 3L

        fun newInstance(video: Video): DetailsFragment = DetailsFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_VIDEO, video) }
        }
    }

    private lateinit var video: Video
    private var primaryButton: Button? = null
    private var myListButton: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        video = requireArguments().getSerializable(ARG_VIDEO) as Video

        bindBreadcrumb(view)
        applyBackdrop(view)
        bindBadges(view)
        bindTexts(view)
        buildActions(view)
        bindSimilar(view)

        // Focus initial sur le bouton principal (spec 5.1).
        view.post { primaryButton?.requestFocus() }
    }

    // ---------------------------------------------------------------- écran

    private fun bindBreadcrumb(view: View) {
        view.findViewById<TextView>(R.id.details_category).text = video.kind.label
        // Icône du pack d'icônes posée devant la catégorie (cachée si inconnue).
        view.findViewById<ImageView>(R.id.details_category_icon).apply {
            val icon = categoryIcon(video.kind)
            isVisible = icon != 0
            if (icon != 0) setImageResource(icon)
        }
        // Retour explicite (libellé visible, spec 7 : bouton facile à repérer).
        view.findViewById<TextView>(R.id.details_back).text =
            "‹ ${getString(R.string.details_back)}"
        view.findViewById<TextView>(R.id.details_crumb_title).text = buildString {
            append(" › ")
            append(
                if (video.isSeries) {
                    "${video.seriesTitle} — S${video.season ?: 1} E${video.episode ?: 1}"
                } else {
                    video.title
                },
            )
        }
        view.findViewById<View>(R.id.details_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    /** Icône du pack correspondant à la catégorie (le plus proche sinon 0). */
    private fun categoryIcon(kind: ContentKind): Int = when (kind) {
        ContentKind.LIVE_TV, ContentKind.LIVE_SPORT -> R.drawable.ic_tv_direct
        ContentKind.MOVIE -> R.drawable.ic_films
        ContentKind.SERIES -> R.drawable.ic_series
        ContentKind.SHORT -> R.drawable.ic_films_courts
        ContentKind.RADIO, ContentKind.PODCAST, ContentKind.MUSIC -> R.drawable.ic_radio
        ContentKind.KIDS -> R.drawable.ic_jeunesse
        ContentKind.GAME -> R.drawable.ic_jeux_video
    }

    /** Fond plein écran (visuel du contenu) + dégradés marine, jamais de zone vide. */
    private fun applyBackdrop(view: View) {
        val bg = view.findViewById<ImageView>(R.id.details_bg)
        val art = view.findViewById<ImageView>(R.id.details_art)
        val ctx = requireContext()

        fun layered(d: Drawable): Drawable = LayerDrawable(
            arrayOf(
                d.constantState?.newDrawable()?.mutate() ?: d,
                ContextCompat.getDrawable(ctx, R.drawable.scrim_horizontal)!!,
                ContextCompat.getDrawable(ctx, R.drawable.scrim_vertical)!!,
            ),
        )

        // Repli immédiat (aucun crash si cardUrl absent ou réseau coupé),
        // puis remplacement par la vignette distante si elle arrive.
        ThumbnailLoader.loadDrawable(ctx, video.cardUrl, fondParDefaut(video.kind.name)) { d ->
            if (!isAdded || this.view !== view) return@loadDrawable
            art.setImageDrawable(d)
            bg.setImageDrawable(layered(d))
        }
    }

    private fun bindBadges(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.details_badges)
        container.removeAllViews()

        if (video.isLive) addChip(container, getString(R.string.badge_live), live = true)
        addChip(container, video.kind.label)
        val hdCapable = !video.isAudioOnly && (
            video.format.equals("mp4", ignoreCase = true) ||
                video.format.equals("hls", ignoreCase = true)
            )
        if (hdCapable) addChip(container, getString(R.string.badge_hd))
        // Licence absente (film sans renseignement) : pas de badge, jamais de « Domaine public » inventé.
        video.license?.trim()?.takeIf { it.isNotEmpty() }?.let { addChip(container, it) }
    }

    private fun addChip(container: LinearLayout, text: String, live: Boolean = false) {
        val density = resources.displayMetrics.density
        val padH = (8 * density).toInt()
        val padV = (3 * density).toInt()
        val marginEnd = (8 * density).toInt()
        TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (live) R.color.missa_white else R.color.missa_text,
                ),
            )
            setBackgroundResource(if (live) R.drawable.bg_badge_live else R.drawable.bg_chip)
            setPadding(padH, padV, padH, padV)
            contentDescription = text
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = marginEnd }
            container.addView(this)
        }
    }

    private fun bindTexts(view: View) {
        val title = if (video.isSeries) {
            "${video.seriesTitle} — S${video.season ?: 1} E${video.episode ?: 1}"
        } else {
            video.title
        }
        view.findViewById<TextView>(R.id.details_title).text = title

        view.findViewById<TextView>(R.id.details_desc).text =
            video.description.ifBlank { getString(R.string.details_description) }

        view.findViewById<TextView>(R.id.details_source).text =
            getString(R.string.details_source, sourceLabel())

        view.findViewById<View>(R.id.details_play).setOnClickListener { openPlayer() }
        view.findViewById<View>(R.id.details_art).setOnClickListener { openPlayer() }
    }

    /** « Source : Internet Archive · Flux MP4 » — l'origine reste affichée même sans nom. */
    private fun sourceLabel(): String {
        val name = video.source ?: when {
            video.sourceId?.contains("archive", ignoreCase = true) == true -> "Internet Archive"
            else -> null
        }
        val flux = "Flux ${video.format.uppercase(Locale.ROOT)}"
        return listOfNotNull(name, flux).joinToString(" · ")
    }

    // -------------------------------------------------------------- actions

    private fun buildActions(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.details_actions)
        container.removeAllViews()
        val presenter = MissaActionPresenter()

        val actions = listOf(
            MissaAction(
                ACTION_PLAY,
                primaryLabel(video.kind, requireContext()),
                R.drawable.ic_play,
                primary = true,
            ),
            MissaAction(
                ACTION_MY_LIST,
                getString(myListLabel()),
                R.drawable.ic_add,
                primary = false,
            ),
            MissaAction(
                ACTION_REPORT,
                getString(R.string.action_report),
                R.drawable.ic_report,
                primary = false,
            ),
        )

        val density = resources.displayMetrics.density
        val gap = (12 * density).toInt()
        actions.forEach { action ->
            val holder = presenter.onCreateViewHolder(container)
            presenter.onBindViewHolder(holder, action)
            holder.view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.marginEnd = gap }
            holder.view.setOnClickListener { performAction(action.id) }
            when (action.id) {
                ACTION_PLAY -> primaryButton = holder.view as Button
                ACTION_MY_LIST -> myListButton = holder.view as Button
            }
            container.addView(holder.view)
        }
    }

    private fun myListLabel(): Int =
        if (MyListStore.contains(requireContext(), video.id)) {
            R.string.action_my_list_remove
        } else {
            R.string.action_my_list_add
        }

    private fun performAction(id: Long) {
        when (id) {
            ACTION_PLAY -> openPlayer()
            ACTION_MY_LIST -> {
                MyListStore.toggle(requireContext(), video.id)
                myListButton?.text = getString(myListLabel())
            }
            ACTION_REPORT -> showReportDialog()
        }
    }

    /** « Signaler un problème » : liste de motifs, enregistre videoId + motif en local. */
    private fun showReportDialog() {
        val reasons = resources.getStringArray(R.array.report_reasons)
        var selected = 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_report)
            .setSingleChoiceItems(reasons, selected) { _, which -> selected = which }
            .setPositiveButton(R.string.report_send) { _, _ ->
                ReportStore.save(requireContext(), video.id, reasons[selected])
                Toast.makeText(requireContext(), R.string.report_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // -------------------------------------------------------------- lecteur

    private fun openPlayer() {
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, video.streamUrl)
                putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                putExtra(PlayerActivity.EXTRA_TITLE, video.title)
                putExtra(PlayerActivity.EXTRA_LIVE, video.isLive)
                putExtra(PlayerActivity.EXTRA_AUDIO_ONLY, video.isAudioOnly)
                putExtra(
                    PlayerActivity.EXTRA_COVER_RES,
                    if (video.cardRes != 0) video.cardRes else fondParDefaut(video.kind.name),
                )
                putExtra(PlayerActivity.EXTRA_COVER_URL, video.cardUrl)
            },
        )
    }

    // ------------------------------------------------------------- similaires

    private fun bindSimilar(view: View) {
        if (video.kind == ContentKind.MOVIE) loadRelatedFilms(view) else loadSameCategory(view)
    }

    /** Chaînes/émissions de même catégorie piochées dans le catalogue embarqué. */
    private fun loadSameCategory(view: View) {
        val items = CatalogRepository.localCatalog()
            .flatMap { it.videos }
            .filter {
                it.kind == video.kind &&
                    it.id != video.id &&
                    !it.title.contains("Emplacement", ignoreCase = true)
            }
            .distinctBy(Video::id)
            .take(8)
        showSimilar(view, items, R.string.details_similar)
    }

    private fun loadRelatedFilms(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val suggestions = try {
                val config = FilmEngineRepository.currentOrLoad(requireContext())
                FilmSourceRegistry.related(video, config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            showSimilar(view, suggestions, R.string.related_films_header)
        }
    }

    /** Affiche la ligne uniquement s'il y a au moins 3 contenus (spec 5.6). */
    private fun showSimilar(view: View, items: List<Video>, @StringRes titleRes: Int) {
        if (!isAdded || this.view !== view) return
        if (items.size < 3) return

        view.findViewById<TextView>(R.id.details_similar_title).text = getString(titleRes)
        view.findViewById<View>(R.id.details_similar_section).isVisible = true

        val grid = view.findViewById<HorizontalGridView>(R.id.details_similar_grid)
        val cards = ArrayObjectAdapter(MissaCardPresenter()).apply { items.forEach { add(it) } }
        val bridge = ItemBridgeAdapter(cards)
        bridge.setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
            override fun onCreate(holder: ItemBridgeAdapter.ViewHolder) {
                holder.itemView.setOnClickListener {
                    val position = holder.bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                    val item = cards[position] as? Video ?: return@setOnClickListener
                    openRelatedVideo(item)
                }
            }
        })
        grid.adapter = bridge
    }

    private fun openRelatedVideo(searchResult: Video) {
        // Déjà jouable (catalogue embarqué/distant) : ouverture directe, sans rechargement.
        if (searchResult.streamUrl.isNotBlank()) {
            openDetails(searchResult)
            return
        }
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
                openDetails(resolved)
            }
        }
    }

    private fun openDetails(next: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, newInstance(next))
            .addToBackStack("related-details")
            .commit()
    }
}
