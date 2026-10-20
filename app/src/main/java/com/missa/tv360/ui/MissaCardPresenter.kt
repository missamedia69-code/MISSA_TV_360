package com.missa.tv360.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore

class MissaCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_missa, parent, false)
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        v.setOnFocusChangeListener { view, focused ->
            // Zoom « actif » (cahier des charges §5) : 1,05 sur 160 ms, sans
            // élévation (une vue élevée sans fond a un outline plein rectangle
            // qui projetait une ombre noire derrière le titre jaune).
            view.animate().cancel()
            view.animate()
                .scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f)
                .setDuration(160L)
                .start()
            view.findViewById<TextView>(R.id.card_title)
                .setTextColor(ContextCompat.getColor(view.context,
                    if (focused) R.color.missa_yellow_focus else R.color.missa_text_secondary))
            // Anneau de focus : fond posé/retiré explicitement (aucun selector
            // d'état : leanback propage activated/selecté à toute la rangée).
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
        val shape = CardShape.forKind(video.kind)
        val image = v.findViewById<ImageView>(R.id.card_image)
        val w = v.resources.getDimensionPixelSize(shape.widthRes)
        val h = v.resources.getDimensionPixelSize(shape.heightRes)
        image.layoutParams = image.layoutParams.apply { width = w; height = h }
        // L'anneau prend les mêmes dimensions que le visuel : la chaîne de mesure
        // des rangées résout mal match_parent (0x0 observé), comme pour les autres
        // enfants de la carte dont la taille est fixée en code.
        val ring = v.findViewById<View>(R.id.card_focus_ring)
        ring.layoutParams = ring.layoutParams.apply { width = w; height = h }
        ring.background = null // carte recyclée : pas d'anneau tant que non focalisée
        v.findViewById<TextView>(R.id.card_title).apply {
            text = video.title
            // Carte recyclée : le titre repasse en couleur de repos, même si elle
            // a été défocus-débindée dans un état « focalisé » (§5 recyclage).
            setTextColor(ContextCompat.getColor(context, R.color.missa_text_secondary))
            layoutParams = layoutParams.apply { width = w }
        }
        v.findViewById<TextView>(R.id.badge_live).isVisible = video.kind.isLive
        val progress = v.findViewById<ProgressBar>(R.id.card_progress)
        val frac = WatchProgressStore.fraction(v.context, video.id)
        progress.isVisible = frac != null
        progress.progress = ((frac ?: 0f) * 100).toInt()
        progress.layoutParams = progress.layoutParams.apply { width = w }
        // Vignette : cardUrl via le chargeur existant, repli sur fond par défaut.
        if (!video.cardUrl.isNullOrBlank()) {
            ThumbnailLoader.loadArtwork(video.cardUrl) { bmp -> image.setImageBitmap(bmp) }
        } else {
            image.setImageDrawable(ContextCompat.getDrawable(v.context, R.drawable.fallback_card))
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        vh.view.findViewById<ImageView>(R.id.card_image).setImageDrawable(null)
    }
}