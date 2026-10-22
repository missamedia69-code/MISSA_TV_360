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
import com.missa.tv360.data.Video
import com.missa.tv360.data.WatchProgressStore

data class MissaAction(val id: Long, val label: String, @DrawableRes val iconRes: Int, val primary: Boolean)

@StringRes
fun primaryLabelRes(kind: ContentKind): Int = when {
    kind.isLive -> R.string.action_watch_live
    kind.isAudioOnly -> R.string.action_listen
    else -> R.string.action_play
}

fun primaryLabel(kind: ContentKind, ctx: Context): String = ctx.getString(primaryLabelRes(kind))

/** Libellé calculé sur l'état réel du contenu, pas seulement sur sa catégorie. */
fun primaryLabel(video: Video, ctx: Context): String = when {
    video.streamUrl.isBlank() -> ctx.getString(R.string.action_unavailable)
    !video.isLive && WatchProgressStore.position(ctx, video.id) != null -> ctx.getString(R.string.action_resume)
    video.isLive -> ctx.getString(R.string.action_watch_live)
    video.isAudioOnly -> ctx.getString(R.string.action_listen)
    else -> ctx.getString(R.string.action_play)
}

class MissaActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val button = LayoutInflater.from(parent.context).inflate(R.layout.action_button, parent, false)
        button.setOnFocusChangeListener { view, focused -> FocusMotion.apply(view, focused) }
        return ViewHolder(button)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val action = item as? MissaAction ?: return
        (vh.view as Button).apply {
            text = action.label
            contentDescription = action.label
            setCompoundDrawablesWithIntrinsicBounds(action.iconRes, 0, 0, 0)
            setBackgroundResource(if (action.primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
            setTextColor(
                ContextCompat.getColorStateList(
                    context,
                    if (action.primary) R.color.button_primary_text else R.color.button_secondary_text,
                ),
            )
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        (vh.view as Button).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }
}
