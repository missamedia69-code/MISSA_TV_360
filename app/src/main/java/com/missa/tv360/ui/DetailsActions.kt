package com.missa.tv360.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind

/** Une action de la fiche détail : libellé, icône et rôle (principale ou secondaire). */
data class MissaAction(
    val id: Long,
    val label: String,
    @DrawableRes val iconRes: Int,
    val primary: Boolean,
)

/** Libellé du bouton principal selon le type de contenu (sans majuscules forcées). */
@StringRes
fun primaryLabelRes(kind: ContentKind): Int = when {
    kind.isLive -> R.string.action_watch_live
    kind.isAudioOnly -> R.string.action_listen
    else -> R.string.action_play
}

/** Variante prête à afficher, qui délègue à [primaryLabelRes]. */
fun primaryLabel(kind: ContentKind, ctx: Context): String =
    ctx.getString(primaryLabelRes(kind))

/** Présente les boutons d'action de la fiche : zoom 1,05 au focus, 120 ms. */
class MissaActionPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val b = LayoutInflater.from(parent.context).inflate(R.layout.action_button, parent, false)
        b.setOnFocusChangeListener { v, focused ->
            v.animate().scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f).setDuration(120).start()
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val a = item as? MissaAction ?: return
        (vh.view as Button).apply {
            text = a.label
            contentDescription = a.label
            if (a.iconRes != 0) {
                setCompoundDrawablesWithIntrinsicBounds(a.iconRes, 0, 0, 0)
            } else {
                setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
            setBackgroundResource(if (a.primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (a.primary) R.color.missa_on_yellow else R.color.missa_text,
                ),
            )
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        (vh.view as Button).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }
}
