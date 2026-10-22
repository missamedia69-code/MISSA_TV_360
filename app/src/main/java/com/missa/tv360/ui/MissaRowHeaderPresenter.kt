package com.missa.tv360.ui

import android.view.View
import android.widget.ImageView
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import com.missa.tv360.R

/** Lie l'icône de catégorie au bind Leanback, sans parcours global de vues. */
class MissaRowHeaderPresenter : RowHeaderPresenter() {
    override fun onBindViewHolder(vh: Presenter.ViewHolder, item: Any?) {
        super.onBindViewHolder(vh, item)
        val header = (item as? Row)?.headerItem
        val icon = vh.view.findViewById<ImageView>(R.id.row_header_icon) ?: return
        val res = iconRes(vh.view, header)
        if (res != 0) icon.setImageResource(res)
        icon.visibility = if (res != 0) View.VISIBLE else View.GONE
    }

    private fun iconRes(view: View, header: HeaderItem?): Int {
        val label = header?.name?.trim().orEmpty()
        val context = view.context
        return listOf(
            context.getString(R.string.row_resume) to R.drawable.ic_reprendre,
            context.getString(R.string.row_my_list) to R.drawable.ic_add,
            context.getString(R.string.row_live_tv) to R.drawable.ic_tv_direct,
            context.getString(R.string.row_sport) to R.drawable.ic_tv_direct,
            "Sport" to R.drawable.ic_tv_direct,
            context.getString(R.string.row_films) to R.drawable.ic_films,
            context.getString(R.string.row_series) to R.drawable.ic_series,
            context.getString(R.string.row_shorts) to R.drawable.ic_films_courts,
            context.getString(R.string.row_radio) to R.drawable.ic_radio,
            context.getString(R.string.row_podcast) to R.drawable.ic_radio,
            context.getString(R.string.row_music) to R.drawable.ic_radio,
            context.getString(R.string.row_kids) to R.drawable.ic_jeunesse,
            context.getString(R.string.row_games) to R.drawable.ic_jeux_video,
        ).firstOrNull { it.first.equals(label, true) }?.second ?: 0
    }
}
