package com.missa.tv360.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind

data class MissaAction(val id: Long, val label: String, val iconRes: Int, val primary: Boolean)

fun primaryLabel(kind: ContentKind, ctx: android.content.Context): String = when {
    kind.isLive -> ctx.getString(R.string.action_watch_live)
    kind.isAudioOnly -> ctx.getString(R.string.action_listen)
    else -> ctx.getString(R.string.action_play)
}

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
            setCompoundDrawablesWithIntrinsicBounds(a.iconRes, 0, 0, 0)
            setBackgroundResource(if (a.primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
            setTextColor(ContextCompat.getColor(context,
                if (a.primary) R.color.missa_on_yellow else R.color.missa_text))
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {}
}