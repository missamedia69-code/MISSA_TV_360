package com.missa.tv360.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore

/**
 * Présentateur des cartes de contenu régularisé par type de contenu.
 *
 * Un seul libellé (sous l'image) : le titre n'est plus répété sur le visuel.
 * Les marges latérales viennent uniquement de `card_missa.xml` (card_gap) ;
 * l'ancien code les rajoutait en Kotlin, et son cast vers
 * `LinearLayout.LayoutParams` échouait dans un RecyclerView, ce qui
 * interrompait tout le bind (carte sans image ni titre).
 *
 * @param fixedWidthRes      largeur imposée (0 = taille dynamique selon le type)
 * @param fixedHeightRes     hauteur imposée (0 = taille dynamique selon le type)
 * @param useGridDimensions  true pour appliquer les tailles régularisées de grille matrice
 */
class MissaCardPresenter(
    private val fixedWidthRes: Int = 0,
    private val fixedHeightRes: Int = 0,
    private val useGridDimensions: Boolean = false,
) : Presenter() {

    private companion object {
        const val FOCUS_SCALE = 1.08f
        const val FOCUS_ANIM_MS = 160L
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        // Une seule fois par rangée : désactivation de l'animator par défaut de RecyclerView
        (parent as? RecyclerView)?.itemAnimator = null
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_missa, parent, false)
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        v.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) FOCUS_SCALE else 1f)
                .scaleY(if (focused) FOCUS_SCALE else 1f)
                .setDuration(FOCUS_ANIM_MS)
                .start()
            // Le focus passe devant les voisines : le zoom ne passe pas sous la carte suivante.
            view.translationZ = if (focused) 8f else 0f
            view.findViewById<TextView>(R.id.card_title).apply {
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (focused) R.color.missa_yellow_focus else R.color.missa_text_secondary,
                    ),
                )
                setTypeface(null, if (focused) Typeface.BOLD else Typeface.NORMAL)
            }
            val ring = view.findViewById<View>(R.id.card_focus_ring)
            if (focused) {
                ring.setBackgroundResource(R.drawable.bg_card_focus_ring)
            } else {
                ring.background = null
            }
        }
        return ViewHolder(v)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val video = item as? Video ?: return
        val v = vh.view
        val res = v.resources

        val shape = CardShape.forVideo(video)
        val image = v.findViewById<ImageView>(R.id.card_image)

        val defaultWRes = if (useGridDimensions) shape.gridWidthRes else shape.widthRes
        val defaultHRes = if (useGridDimensions) shape.gridHeightRes else shape.heightRes

        val w = res.getDimensionPixelSize(fixedWidthRes.takeIf { it != 0 } ?: defaultWRes)
        val h = res.getDimensionPixelSize(fixedHeightRes.takeIf { it != 0 } ?: defaultHRes)

        image.layoutParams = image.layoutParams.apply { width = w; height = h }

        val ring = v.findViewById<View>(R.id.card_focus_ring)
        ring.layoutParams = ring.layoutParams.apply { width = w; height = h }
        // Le bind peut survenir pendant que la carte garde le focus (mise à jour en place).
        if (v.isFocused) ring.setBackgroundResource(R.drawable.bg_card_focus_ring) else ring.background = null

        val title = v.findViewById<TextView>(R.id.card_title)
        title.apply {
            text = video.title
            isVisible = video.title.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
            if (!v.isFocused) {
                setTextColor(ContextCompat.getColor(context, R.color.missa_text_secondary))
                setTypeface(null, Typeface.NORMAL)
            }
        }
        // Description courte sous le titre (saison/épisode, direct, audio).
        v.findViewById<TextView>(R.id.card_subtitle).apply {
            val subtitle = video.subtitle()
            text = subtitle
            isVisible = video.moreTargetRowId == null && subtitle.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
        }
        v.findViewById<TextView>(R.id.badge_live).isVisible = video.kind.isLive

        val progress = v.findViewById<ProgressBar>(R.id.card_progress)
        val frac = if (video.moreTargetRowId == null) WatchProgressStore.fraction(v.context, video.id) else null
        progress.isVisible = frac != null
        progress.progress = ((frac ?: 0f) * 100).toInt()

        v.tag = video.id

        if (video.moreTargetRowId != null) {
            image.setImageResource(R.drawable.card_more)
            val description = v.context.getString(R.string.row_more_description, video.description)
            image.contentDescription = description
            v.contentDescription = description
            return
        }
        v.contentDescription = video.title

        // Affichage immédiat de l'illustration locale ou du fond par défaut
        if (video.cardRes != 0) {
            image.setImageResource(video.cardRes)
        } else {
            image.setImageResource(afficheParDefaut(video.kind))
        }

        // Chargement asynchrone de la vignette distante (si présente)
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadArtwork(video.cardUrl) { bmp ->
                if (v.tag == video.id) image.setImageBitmap(bmp)
            }
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        vh.view.tag = null
        vh.view.findViewById<ImageView>(R.id.card_image).setImageDrawable(null)
    }
}
