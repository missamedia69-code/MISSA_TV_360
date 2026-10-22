package com.missa.tv360.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R

/**
 * Liste de l'écran Extensions : dépôts puis extensions.
 *
 * Interaction pensée pour une télécommande :
 *  - clic sur une extension → installer / mettre à jour ;
 *  - appui long sur une extension installée → proposer la suppression ;
 *  - appui long sur un dépôt → proposer la suppression du dépôt.
 */
internal class ExtensionsAdapter(
    private val onPluginClick: (RemotePlugin) -> Unit,
    private val onPluginLongClick: (InstalledExtension) -> Unit,
    private val onRepoLongClick: (RepositoryState) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Repo(val state: RepositoryState) : Row()
        data class Plugin(val remote: RemotePlugin, val installed: InstalledExtension?) : Row()
        data class Orphan(val installed: InstalledExtension) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(state: ExtensionsState) {
        val installedByName = state.installed.associateBy { it.internalName }
        val remoteNames = state.remote.map { it.entry.normalizedInternalName }.toSet()

        val built = mutableListOf<Row>()
        built += Row.Header("Dépôts")
        state.repositories.forEach { built += Row.Repo(it) }
        built += Row.Header("Extensions")
        state.remote
            .sortedBy { it.entry.normalizedName.lowercase() }
            .forEach { built += Row.Plugin(it, installedByName[it.entry.normalizedInternalName]) }
        // Extensions présentes sur l'appareil mais absentes des dépôts :
        // on les montre quand même, sinon elles seraient invisibles.
        state.installed
            .filter { it.internalName !in remoteNames }
            .forEach { built += Row.Orphan(it) }

        rows = built
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Repo -> TYPE_REPO
        is Row.Plugin -> TYPE_PLUGIN
        is Row.Orphan -> TYPE_PLUGIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_extensions_header, parent, false))
            else -> ItemHolder(inflater.inflate(R.layout.item_extension, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row.title)
            is Row.Repo -> (holder as ItemHolder).bindRepo(row.state)
            is Row.Plugin -> (holder as ItemHolder).bindPlugin(row.remote.entry, row.installed)
            is Row.Orphan -> (holder as ItemHolder).bindOrphan(row.installed)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.ext_header_title)
        fun bind(text: String) {
            title.text = text
        }
    }

    private inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.ext_title)
        private val subtitle: TextView = view.findViewById(R.id.ext_subtitle)
        private val action: TextView = view.findViewById(R.id.ext_action)

        fun bindRepo(state: RepositoryState) {
            title.text = state.manifest?.name ?: state.entry.name
            subtitle.text = state.error ?: state.entry.url
            action.text = if (state.error == null) "OK" else "ERREUR"
            itemView.isFocusable = true
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener {
                onRepoLongClick(state)
                true
            }
        }

        fun bindPlugin(entry: PluginEntry, installed: InstalledExtension?) {
            title.text = entry.normalizedName
            val details = buildString {
                append("v").append(entry.versionInt)
                if (installed != null) {
                    append(" · installée v").append(installed.version)
                    if (!installed.enabled) append(" (désactivée)")
                }
                entry.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            subtitle.text = details
            action.text = when {
                entry.isDisabledByAuthor -> "Indisponible"
                !entry.isCompatible -> "API incompatible"
                installed == null -> "Installer"
                entry.versionInt > installed.version -> "Mettre à jour"
                else -> "À jour"
            }
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
        }

        fun bindOrphan(installed: InstalledExtension) {
            title.text = installed.name
            subtitle.text = "Installée localement · v${installed.version} · ${installed.filePath}"
            action.text = if (installed.enabled) "Locale" else "Désactivée"
            itemView.isFocusable = true
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener {
                onPluginLongClick(installed)
                true
            }
        }

        /** Le dépôt d'origine est résolu par l'adaptateur au moment de l'installation. */
        private fun repositoryOf(entry: PluginEntry): String =
            remoteRepositories[entry.normalizedInternalName].orEmpty()

        private val remoteRepositories: Map<String, String>
            get() = rows.filterIsInstance<Row.Plugin>()
                .associate { it.remote.entry.normalizedInternalName to it.remote.repositoryUrl }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_REPO = 1
        const val TYPE_PLUGIN = 2
    }
}
