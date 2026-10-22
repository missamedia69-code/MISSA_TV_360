package com.missa.tv360.ui

import android.content.res.Resources
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
 * @param fixedWidthRes      largeur imposée (0 = taille dynamique selon le type)
 * @param fixedHeightRes     hauteur imposée (0 = taille dynamique selon le type)
 * @param useGridDimensions  true pour appliquer les tailles régularisées de grille matrice
 */
class MissaCardPresenter(
    private val fixedWidthRes: Int = 0,
    private val fixedHeightRes: Int = 0,
    private val useGridDimensions: Boolean = false,
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        // Une seule fois par rangée : désactivation de l'animator par défaut de RecyclerView
        (parent as? RecyclerView)?.itemAnimator = null
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_missa, parent, false)
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        v.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f)
                .setDuration(160L)
                .start()
            view.findViewById<TextView>(R.id.card_title)
                .setTextColor(ContextCompat.getColor(view.context,
                    if (focused) R.color.missa_yellow_focus else R.color.missa_text_secondary))
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
        // Les marges se posent sur la classe de base commune à tous les
        // ViewGroup : la carte est inflatée dans le RecyclerView de Leanback,
        // dont les layoutParams ne sont PAS des LinearLayout.LayoutParams
        // (un cast vers cette classe faisait échouer tout le binding : ni
        // image, ni titre, ni description sur les tuiles).
        val params = v.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null) {
            val gap = dpToPx(16)
            params.setMargins(gap, 0, gap, 0)
            v.layoutParams = params
        }

        val shape = CardShape.forVideo(video)
        val image = v.findViewById<ImageView>(R.id.card_image)

        val defaultWRes = if (useGridDimensions) shape.gridWidthRes else shape.widthRes
        val defaultHRes = if (useGridDimensions) shape.gridHeightRes else shape.heightRes

        val w = v.resources.getDimensionPixelSize(
            fixedWidthRes.takeIf { it != 0 } ?: defaultWRes)
        val h = v.resources.getDimensionPixelSize(
            fixedHeightRes.takeIf { it != 0 } ?: defaultHRes)

        image.layoutParams = image.layoutParams.apply { width = w; height = h }

        val ring = v.findViewById<View>(R.id.card_focus_ring)
        ring.layoutParams = ring.layoutParams.apply { width = w; height = h }
        ring.background = null

        v.findViewById<TextView>(R.id.card_title).apply {
            text = video.title
            setTextColor(ContextCompat.getColor(context, R.color.missa_text_secondary))
            isVisible = video.moreTargetRowId == null || video.title.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
        }
        // Description courte sous le titre : l'utilisateur voit l'affiche ET
        // le resume du contenu sans avoir a ouvrir la fiche. [Video.subtitle]
        // ajoute les badges utiles (saison/episode, DIRECT, audio).
        v.findViewById<TextView>(R.id.card_subtitle).apply {
            val subtitle = video.subtitle()
            text = subtitle
            isVisible = video.moreTargetRowId == null && subtitle.isNotBlank()
            layoutParams = layoutParams.apply { width = w }
        }
        v.findViewById<TextView>(R.id.badge_live).isVisible = video.kind.isLive
        val progress = v.findViewById<ProgressBar>(R.id.card_progress)
        val frac = WatchProgressStore.fraction(v.context, video.id)
        progress.isVisible = frac != null
        progress.progress = ((frac ?: 0f) * 100).toInt()
        progress.layoutParams = progress.layoutParams.apply { width = w }

        v.tag = video.id
        val overlayTitle = v.findViewById<TextView>(R.id.card_overlay_title)
        val scrim = v.findViewById<View>(R.id.card_scrim)

        if (video.moreTargetRowId != null) {
            image.setImageResource(R.drawable.card_more)
            image.contentDescription =
                v.context.getString(R.string.row_more_description, video.title)
            v.contentDescription = v.context.getString(R.string.row_more_description, video.title)
            overlayTitle?.isVisible = false
            scrim?.isVisible = false
            v.findViewById<TextView>(R.id.card_subtitle).isVisible = false
            return
        }
        v.contentDescription = video.title
        overlayTitle?.text = video.title
        overlayTitle?.isVisible = video.title.isNotBlank()
        scrim?.isVisible = video.title.isNotBlank()

        // Affichage immédiat de l'illustration locale, sinon de l'affiche de
        // repli du type de contenu (320×180, format des tuiles).
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

    private fun dpToPx(dp: Int): Int =
        (dp * Resources.getSystem().displayMetrics.density).toInt()
}
