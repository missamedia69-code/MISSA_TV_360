package com.missa.tv360.ui

import android.app.AlertDialog
import android.content.Intent
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.MyListStore
import com.missa.tv360.data.ReportStore
import com.missa.tv360.data.Video
import com.missa.tv360.extensions.StreamResolver
import com.missa.tv360.playback.PlayerActivity
import com.missa.tv360.ui.mobile.RatioImageView
import kotlinx.coroutines.launch

/** Fiche partagée : hiérarchie tactile compacte ou composition large TV/tablette. */
class DetailsFragment : Fragment() {
    private lateinit var video: Video
    private lateinit var primaryButton: Button
    private lateinit var myListButton: Button
    private var resolvedHeaders: Map<String, String> = emptyMap()
    private var isResolvingSources = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        video = requireArguments().getSerializable(ARG_VIDEO) as Video
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindBreadcrumb(view)
        bindBadges(view)
        bindTexts(view)
        applyBackdrop(view)
        view.findViewById<View>(R.id.details_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        buildActions(view)
        bindSimilar(view)
        setupDescriptionToggle(view)
        resolveSourcesIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        CatalogStore.refreshUserRows(requireContext())
        if (::primaryButton.isInitialized) updatePrimaryButton()
        if (::myListButton.isInitialized) updateMyListButton()
    }

    private fun resolveSourcesIfNeeded() {
        if (video.streamUrl.isNotBlank() || video.pageUrl.isNullOrBlank() || video.providerId.isNullOrBlank() || isResolvingSources) {
            return
        }
        isResolvingSources = true
        if (::primaryButton.isInitialized) {
            primaryButton.text = getString(R.string.details_searching_sources)
            primaryButton.isEnabled = false
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val res = StreamResolver.resolve(video)
            if (!isAdded) return@launch
            isResolvingSources = false
            if (res.streams.isNotEmpty()) {
                val best = res.streams[0]
                resolvedHeaders = best.headers
                video = video.copy(streamUrl = best.url, format = best.format)
                updatePrimaryButton()
            } else {
                if (::primaryButton.isInitialized) {
                    primaryButton.text = getString(R.string.details_retry_sources)
                    primaryButton.contentDescription = getString(R.string.details_retry_sources)
                    primaryButton.isEnabled = !video.pageUrl.isNullOrBlank() && !video.providerId.isNullOrBlank()
                }
                if (!res.error.isNullOrBlank()) {
                    Toast.makeText(requireContext(), res.error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindBreadcrumb(view: View) {
        val back = view.findViewById<Button>(R.id.details_back)
        back.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_back, 0, 0, 0)
        view.findViewById<TextView>(R.id.details_crumb_title).text =
            getString(R.string.details_crumb, video.displayKindLabel, video.title)
    }

    private fun applyBackdrop(view: View) {
        val bg = view.findViewById<ImageView>(R.id.details_bg)
        val art = view.findViewById<ImageView>(R.id.details_art)
        val shape = CardShape.forVideo(video)
        (art as? RatioImageView)?.aspectRatio = shape.ratio
        val artContainer = view.findViewById<View>(R.id.details_art_container)
        val compact = resources.getBoolean(R.bool.is_compact_layout)
        val twoColumn = resources.getBoolean(R.bool.details_two_column)
        when {
            twoColumn -> {
                var (width, height) = when (shape) {
                    CardShape.POSTER -> if (compact) 160.dp to 240.dp else 180.dp to 270.dp
                    CardShape.SQUARE -> 180.dp to 180.dp
                    CardShape.LANDSCAPE -> if (compact) 240.dp to 135.dp else 264.dp to 148.dp
                }
                val reserved = artContainer.layoutParams.width
                if (reserved > 0 && width > reserved) {
                    height = (height * reserved / width.toFloat()).toInt()
                    width = reserved
                }
                artContainer.layoutParams = artContainer.layoutParams.apply {
                    this.width = width
                    this.height = height
                }
            }
            compact -> {
                val width = when (shape) {
                    CardShape.POSTER -> 180.dp
                    CardShape.SQUARE -> 200.dp
                    CardShape.LANDSCAPE -> ViewGroup.LayoutParams.MATCH_PARENT
                }
                artContainer.layoutParams = artContainer.layoutParams.apply { this.width = width }
            }
            shape == CardShape.POSTER || shape == CardShape.SQUARE -> {
                artContainer.layoutParams = artContainer.layoutParams.apply {
                    width = 180.dp
                    height = if (shape == CardShape.POSTER) 270.dp else 180.dp
                }
            }
        }
        val fallback = fondParDefaut(video.kind.name)
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadDrawable(
                requireContext(),
                video.cardUrl,
                fallback,
                targetWidth = if (compact) 640 else 1280,
            ) { drawable ->
                if (!isAdded) return@loadDrawable
                bg.setImageDrawable(drawable)
                art.setImageDrawable(drawable)
            }
        }
    }

    private fun bindBadges(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.details_badges)
        container.removeAllViews()
        if (video.isLive) addChip(container, getString(R.string.badge_live))
        addChip(container, video.displayKindLabel)
        video.durationSec?.takeIf { it > 0 }?.let { seconds ->
            addChip(container, getString(R.string.details_duration, (seconds + 59) / 60))
        }
        video.license?.takeIf { it.isNotBlank() }?.let { addChip(container, it) }
    }

    private fun addChip(container: LinearLayout, text: String) {
        val chip = TextView(requireContext()).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.missa_text_secondary))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        container.addView(
            chip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = 8.dp },
        )
    }

    private fun bindTexts(view: View) {
        view.findViewById<ImageView>(R.id.details_category_icon).setImageResource(categoryIcon(video.kind))
        view.findViewById<TextView>(R.id.details_title).text =
            video.seriesDisplayTitle() ?: video.title
        view.findViewById<TextView>(R.id.details_description).text =
            video.description.ifBlank { getString(R.string.details_description) }
        val metadata = listOfNotNull(
            video.creator?.trim()?.takeIf { it.isNotEmpty() }?.let {
                getString(R.string.details_creator, it.take(160))
            },
            video.releaseYear?.trim()?.takeIf { it.isNotEmpty() }?.let {
                getString(R.string.details_release_year, it.take(20))
            },
        )
        view.findViewById<TextView>(R.id.details_metadata).apply {
            text = metadata.joinToString("\n")
            isVisible = metadata.isNotEmpty()
        }
        val sourceName = video.source
        val sourceParts = listOfNotNull(
            sourceName?.takeIf { it.isNotBlank() }?.let { getString(R.string.details_source, it) },
            video.format?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.details_source_format, it.uppercase())
            },
        )
        view.findViewById<TextView>(R.id.details_source).apply {
            text = sourceParts.joinToString(" · ")
            isVisible = sourceParts.isNotEmpty()
        }
        view.findViewById<View>(R.id.details_art).isFocusable = false
    }

    private fun buildActions(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.details_actions)
        val compact = resources.getBoolean(R.bool.is_compact_layout)
        container.orientation = LinearLayout.HORIZONTAL
        val actionHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        val actions = listOf(
            MissaAction(1, primaryLabel(video, requireContext()), playIcon(video), true),
            MissaAction(2, myListLabel(), if (MyListStore.contains(requireContext(), video.id)) R.drawable.ic_remove else R.drawable.ic_add, false),
            MissaAction(3, getString(R.string.action_report), R.drawable.ic_report, false),
        )
        actions.forEachIndexed { index, action ->
            val button = LayoutInflater.from(requireContext())
                .inflate(R.layout.action_button, container, false) as Button
            button.text = action.label
            button.contentDescription = action.label
            button.textSize = if (compact) 12f else 16f
            button.maxLines = 2
            button.gravity = Gravity.CENTER
            val horizontalPadding = if (compact) 6.dp else 18.dp
            button.setPadding(horizontalPadding, 4.dp, horizontalPadding, 4.dp)
            button.setCompoundDrawablesWithIntrinsicBounds(action.iconRes, 0, 0, 0)
            if (!action.primary) {
                button.setBackgroundResource(R.drawable.bg_btn_secondary)
                button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.button_secondary_text))
            } else {
                button.setBackgroundResource(R.drawable.bg_btn_primary)
                button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.button_primary_text))
                primaryButton = button
                button.isEnabled = video.streamUrl.isNotBlank() || (!video.pageUrl.isNullOrBlank() && !video.providerId.isNullOrBlank())
            }
            button.setOnFocusChangeListener { v, focused -> FocusMotion.apply(v, focused) }
            when (action.id) {
                1L -> button.setOnClickListener {
                    if (video.streamUrl.isNotBlank()) openPlayer() else resolveSourcesIfNeeded()
                }
                2L -> {
                    myListButton = button
                    button.setOnClickListener { toggleMyList() }
                }
                else -> button.setOnClickListener { showReportDialog() }
            }
            val params = LinearLayout.LayoutParams(0, actionHeight, 1f).apply {
                marginEnd = if (index < actions.lastIndex) 8.dp else 0
            }
            container.addView(button, params)
        }
    }

    private fun updatePrimaryButton() {
        val canResolve = !video.pageUrl.isNullOrBlank() && !video.providerId.isNullOrBlank()
        val label = if (video.streamUrl.isBlank() && !isResolvingSources && canResolve) {
            getString(R.string.details_retry_sources)
        } else primaryLabel(video, requireContext())
        primaryButton.text = label
        primaryButton.contentDescription = label
        primaryButton.isEnabled = video.streamUrl.isNotBlank() || (!isResolvingSources && canResolve)
    }

    private fun myListLabel(): String = getString(
        if (MyListStore.contains(requireContext(), video.id)) R.string.action_my_list_remove else R.string.action_my_list_add,
    )

    private fun updateMyListButton() {
        val inList = MyListStore.contains(requireContext(), video.id)
        val label = myListLabel()
        myListButton.text = label
        myListButton.contentDescription = if (inList) {
            getString(R.string.details_remove_list_description, video.title)
        } else getString(R.string.details_add_list_description, video.title)
        myListButton.setCompoundDrawablesWithIntrinsicBounds(
            if (inList) R.drawable.ic_remove else R.drawable.ic_add, 0, 0, 0,
        )
    }

    private fun toggleMyList() {
        MyListStore.toggle(requireContext(), video.id)
        CatalogStore.refreshUserRows(requireContext())
        updateMyListButton()
    }

    private fun setupDescriptionToggle(view: View) {
        val description = view.findViewById<TextView>(R.id.details_description)
        val toggle = view.findViewById<Button>(R.id.details_description_toggle)
        description.post {
            val layout = description.layout ?: return@post
            val truncated = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
            toggle.isVisible = truncated
        }
        toggle.setOnClickListener {
            description.maxLines = Int.MAX_VALUE
            description.ellipsize = null
            toggle.isVisible = false
        }
    }

    private fun bindSimilar(view: View) {
        val section = view.findViewById<View>(R.id.details_similar_section)
        view.findViewById<HorizontalGridView>(R.id.details_similar_grid)
        val similar = CatalogStore.snapshot()
            .flatMap { it.videos }
            .filter {
                it.id != video.id && it.streamUrl.isNotBlank() &&
                    it.displayKindLabel == video.displayKindLabel
            }
            .distinctBy { it.id }
            .take(6)
        if (similar.size >= 3) showSimilar(view, similar)
        else section.isVisible = false
    }

    private fun showSimilar(view: View, related: List<Video>) {
        if (related.size < 3) {
            view.findViewById<View>(R.id.details_similar_section).isVisible = false
            return
        }
        view.findViewById<View>(R.id.details_similar_section).isVisible = true
        view.findViewById<TextView>(R.id.details_similar_title).setText(
            if (video.isLive || video.isAudioOnly) R.string.details_similar_channels else R.string.details_similar,
        )
        val cards = ArrayObjectAdapter(MissaCardPresenter()).apply {
            related.forEach { add(it) }
        }
        val bridge = ItemBridgeAdapter(cards)
        bridge.setAdapterListener(object : ItemBridgeAdapter.AdapterListener() {
            override fun onCreate(holder: ItemBridgeAdapter.ViewHolder) {
                holder.itemView.setOnClickListener {
                    val position = holder.bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                    val selected = cards[position] as? Video ?: return@setOnClickListener
                    openRelatedDetails(selected)
                }
            }
        })
        view.findViewById<HorizontalGridView>(R.id.details_similar_grid).adapter = bridge
    }

    private fun openRelatedDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, newInstance(video))
            .addToBackStack("related-details")
            .commit()
    }

    private fun openPlayer() {
        if (video.streamUrl.isBlank()) {
            Toast.makeText(requireContext(), R.string.search_unplayable, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_URL, video.streamUrl)
                putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
                putExtra(PlayerActivity.EXTRA_TITLE, video.seriesDisplayTitle() ?: video.title)
                putExtra(PlayerActivity.EXTRA_LIVE, video.isLive)
                putExtra(PlayerActivity.EXTRA_AUDIO_ONLY, video.isAudioOnly)
                putExtra(
                    PlayerActivity.EXTRA_COVER_RES,
                    if (video.cardRes != 0) video.cardRes else fondParDefaut(video.kind.name),
                )
                putExtra(PlayerActivity.EXTRA_COVER_URL, video.cardUrl)
                putExtra(PlayerActivity.EXTRA_FORMAT, video.format)
                if (resolvedHeaders.isNotEmpty()) {
                    putExtra(PlayerActivity.EXTRA_HEADERS, HashMap(resolvedHeaders))
                }
            },
        )
    }

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

    private fun playIcon(video: Video): Int = if (video.streamUrl.isBlank()) 0 else when {
        video.isLive -> R.drawable.ic_tv_direct
        video.isAudioOnly -> R.drawable.ic_radio
        else -> R.drawable.ic_play
    }

    private fun categoryIcon(kind: ContentKind): Int {
        if (video.originalKind.equals("sport", true)) return R.drawable.ic_tv_direct
        return when {
            kind.isLive -> R.drawable.ic_tv_direct
            kind == ContentKind.SERIES -> R.drawable.ic_series
            kind == ContentKind.SHORT -> R.drawable.ic_films_courts
            kind.isAudioOnly -> R.drawable.ic_radio
            kind == ContentKind.KIDS -> R.drawable.ic_jeunesse
            kind == ContentKind.GAME -> R.drawable.ic_jeux_video
            else -> R.drawable.ic_films
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val ARG_VIDEO = "arg_video"
        fun newInstance(video: Video) = DetailsFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_VIDEO, video) }
        }
    }
}
