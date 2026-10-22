package com.missa.tv360.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.missa.tv360.R

data class SearchStatus(val text: String, val retry: Boolean = false)

class SearchStatusPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.search_status, parent, false))

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val status = item as? SearchStatus ?: return
        (vh.view as TextView).apply {
            text = status.text
            isFocusable = status.retry
            isFocusableInTouchMode = status.retry
            contentDescription = if (status.retry) "$text. Activer pour réessayer." else text
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) = Unit
}
