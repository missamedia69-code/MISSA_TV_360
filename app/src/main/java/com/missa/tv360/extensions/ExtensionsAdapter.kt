package com.missa.tv360.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R

/** Liste tactile/D-pad des dépôts et extensions, avec actions explicites. */
internal class ExtensionsAdapter(
    private val onPluginClick: (RemotePlugin) -> Unit,
    private val onPluginLongClick: (InstalledExtension) -> Unit,
    private val onRepoLongClick: (RepositoryState) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private sealed class Row {
        data class Header(val titleRes: Int) : Row()
        data class Empty(val textRes: Int) : Row()
        data class Repo(val state: RepositoryState) : Row()
        data class Plugin(val remote: RemotePlugin, val installed: InstalledExtension?) : Row()
        data class Orphan(val installed: InstalledExtension) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(state: ExtensionsState) {
        val installedByName = state.installed.associateBy { it.internalName }
        val remoteNames = state.remote.map { it.entry.normalizedInternalName }.toSet()
        val built = mutableListOf<Row>()
        built += Row.Header(R.string.extensions_repositories)
        if (state.repositories.isEmpty()) built += Row.Empty(R.string.extensions_empty_repositories)
        state.repositories.forEach { built += Row.Repo(it) }
        built += Row.Header(R.string.extensions_plugins)
        state.remote.sortedBy { it.entry.normalizedName.lowercase() }
            .forEach { built += Row.Plugin(it, installedByName[it.entry.normalizedInternalName]) }
        state.installed.filter { it.internalName !in remoteNames }.forEach { built += Row.Orphan(it) }
        if (state.remote.isEmpty() && state.installed.isEmpty()) {
            built += Row.Empty(R.string.extensions_empty_plugins)
        }
        rows = built
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Empty -> TYPE_EMPTY
        is Row.Repo -> TYPE_REPO
        is Row.Plugin, is Row.Orphan -> TYPE_PLUGIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_extensions_header, parent, false))
            TYPE_EMPTY -> EmptyHolder(inflater.inflate(R.layout.item_extensions_empty, parent, false))
            else -> ItemHolder(inflater.inflate(R.layout.item_extension, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.titleRes)
            is Row.Empty -> (holder as EmptyHolder).bind(row.textRes)
            is Row.Repo -> (holder as ItemHolder).bindRepo(row.state)
            is Row.Plugin -> (holder as ItemHolder).bindPlugin(row.remote.entry, row.installed)
            is Row.Orphan -> (holder as ItemHolder).bindOrphan(row.installed)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.ext_header_title)
        fun bind(textRes: Int) { title.setText(textRes) }
    }

    private class EmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view as TextView
        fun bind(textRes: Int) { text.setText(textRes) }
    }

    private inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.ext_title)
        private val subtitle: TextView = view.findViewById(R.id.ext_subtitle)
        private val action: TextView = view.findViewById(R.id.ext_action)
        private val remove: ImageButton = view.findViewById(R.id.ext_remove)

        fun bindRepo(state: RepositoryState) {
            title.text = state.manifest?.name ?: state.entry.name
            subtitle.text = state.error ?: state.entry.url
            action.text = itemView.context.getString(
                if (state.error == null) R.string.extensions_status_ok else R.string.extensions_status_error,
            )
            itemView.isFocusable = true
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener {
                onRepoLongClick(state)
                true
            }
            remove.isVisible = true
            remove.contentDescription = itemView.context.getString(R.string.extensions_remove)
            remove.setOnClickListener { onRepoLongClick(state) }
            updateDescription()
        }

        fun bindPlugin(entry: PluginEntry, installed: InstalledExtension?) {
            title.text = entry.normalizedName
            subtitle.text = buildString {
                append("v").append(entry.versionInt)
                if (installed != null) {
                    append(" · installée v").append(installed.version)
                    if (!installed.enabled) append(" (désactivée)")
                }
                entry.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            action.text = itemView.context.getString(
                when {
                    entry.isDisabledByAuthor -> R.string.extensions_unavailable
                    !entry.isCompatible -> R.string.extensions_incompatible
                    installed == null -> R.string.extensions_install
                    entry.versionInt > installed.version -> R.string.extensions_update
                    else -> R.string.extensions_up_to_date
                },
            )
            itemView.isFocusable = true
            itemView.setOnClickListener {
                if (!entry.isDisabledByAuthor && entry.isCompatible) {
                    onPluginClick(RemotePlugin(entry, repositoryOf(entry)))
                }
            }
            itemView.setOnLongClickListener {
                installed?.let { onPluginLongClick(it) }
                installed != null
            }
            remove.isVisible = installed != null
            remove.setOnClickListener { installed?.let(onPluginLongClick) }
            updateDescription()
        }

        fun bindOrphan(installed: InstalledExtension) {
            title.text = installed.name
            subtitle.text = "Installée localement · v${installed.version} · ${installed.filePath}"
            action.text = itemView.context.getString(
                if (installed.enabled) R.string.extensions_local else R.string.extensions_disabled,
            )
            itemView.isFocusable = true
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener {
                onPluginLongClick(installed)
                true
            }
            remove.isVisible = true
            remove.setOnClickListener { onPluginLongClick(installed) }
            updateDescription()
        }

        private fun updateDescription() {
            itemView.contentDescription = "${title.text}. ${subtitle.text}. ${action.text}"
        }

        private fun repositoryOf(entry: PluginEntry): String =
            rows.filterIsInstance<Row.Plugin>()
                .firstOrNull { it.remote.entry.normalizedInternalName == entry.normalizedInternalName }
                ?.remote?.repositoryUrl.orEmpty()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_REPO = 1
        const val TYPE_PLUGIN = 2
        const val TYPE_EMPTY = 3
    }
}
