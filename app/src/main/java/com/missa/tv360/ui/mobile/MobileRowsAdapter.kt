package com.missa.tv360.ui.mobile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.CatalogStore
import com.missa.tv360.data.Video

class MobileRowsAdapter(
    private val onVideoClick: (Video) -> Unit,
    private val onMoreClick: (CatalogStore.DisplayRow) -> Unit,
) : ListAdapter<CatalogStore.DisplayRow, MobileRowsAdapter.Holder>(Diff) {
    class Holder(view: View, onVideoClick: (Video) -> Unit) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.mobile_row_title)
        val more: Button = view.findViewById(R.id.mobile_row_more)
        val videos: RecyclerView = view.findViewById(R.id.mobile_row_videos)
        val empty: TextView = view.findViewById(R.id.mobile_row_empty)
        val adapter = MobileVideoAdapter(onClick = onVideoClick)
        var row: CatalogStore.DisplayRow? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.mobile_catalog_row, parent, false)
        return Holder(view, onVideoClick).also { holder ->
            holder.videos.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
            holder.videos.adapter = holder.adapter
            holder.more.setOnClickListener { holder.row?.let(onMoreClick) }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.row = row
        holder.title.text = row.title
        holder.more.isVisible = row.videos.isNotEmpty() &&
            row.videos.none { it.isGamesLauncher }
        holder.more.contentDescription = holder.more.context.getString(R.string.row_more_description, row.title)
        holder.videos.isVisible = row.videos.isNotEmpty()
        holder.empty.isVisible = row.videos.isEmpty()
        holder.empty.text = holder.empty.context.getString(
            if (row.rowId == CatalogStore.MY_LIST_ROW_ID) R.string.empty_my_list else R.string.empty_category,
        )
        holder.adapter.submitList(row.videos)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.row = null
        holder.adapter.submitList(emptyList())
        super.onViewRecycled(holder)
    }

    private object Diff : DiffUtil.ItemCallback<CatalogStore.DisplayRow>() {
        override fun areItemsTheSame(oldItem: CatalogStore.DisplayRow, newItem: CatalogStore.DisplayRow): Boolean =
            oldItem.rowId == newItem.rowId && oldItem.title == newItem.title
        override fun areContentsTheSame(oldItem: CatalogStore.DisplayRow, newItem: CatalogStore.DisplayRow): Boolean =
            oldItem == newItem
    }
}
