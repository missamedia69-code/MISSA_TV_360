package com.missa.tv360.extensions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R

/** Liste tactile/D-pad : état, version et action sont trois informations distinctes. */
internal class ExtensionsAdapter(
    private val onPluginAction: (RemotePlugin) -> Unit,
    private val onPluginUninstall: (InstalledExtension) -> Unit,
    private val onRepoRemove: (RepositoryState) -> Unit,
    private val onFailureDetails: (ExtensionFailure) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private sealed class Row {
        data class Header(val titleRes: Int) : Row()
        data class Empty(val textRes: Int) : Row()
        data class Repo(val state: RepositoryState) : Row()
        data class Plugin(
            val remote: RemotePlugin,
            val installed: InstalledExtension?,
            val failure: ExtensionFailure?,
        ) : Row()
        data class Orphan(val installed: InstalledExtension) : Row()
        data class Failure(val failure: ExtensionFailure) : Row()
    }

    private data class StatusVisual(
        val text: String,
        val iconRes: Int,
        val colorRes: Int,
    )

    private var rows: List<Row> = emptyList()

    fun submit(state: ExtensionsState) {
        val installedByName = state.installed.associateBy { it.internalName }
        val failuresByName = state.failures.associateBy { it.internalName }
        val remoteNames = state.remote.map { it.entry.normalizedInternalName }.toSet()
        val built = mutableListOf<Row>()
        built += Row.Header(R.string.extensions_repositories)
        if (state.repositories.isEmpty()) built += Row.Empty(R.string.extensions_empty_repositories)
        state.repositories.forEach { built += Row.Repo(it) }
        built += Row.Header(R.string.extensions_plugins)
        state.remote.sortedBy { it.entry.normalizedName.lowercase() }.forEach { remote ->
            val name = remote.entry.normalizedInternalName
            built += Row.Plugin(remote, installedByName[name], failuresByName[name])
        }
        state.installed.filter { it.internalName !in remoteNames }.forEach {
            built += Row.Orphan(it)
        }
        state.failures.filter { failure ->
            failure.internalName !in remoteNames && failure.internalName !in installedByName
        }.forEach { built += Row.Failure(it) }
        if (state.remote.isEmpty() && state.installed.isEmpty() && state.failures.isEmpty()) {
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
        is Row.Plugin, is Row.Orphan, is Row.Failure -> TYPE_PLUGIN
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
            is Row.Plugin -> (holder as ItemHolder).bindPlugin(row.remote, row.installed, row.failure)
            is Row.Orphan -> (holder as ItemHolder).bindOrphan(row.installed)
            is Row.Failure -> (holder as ItemHolder).bindFailure(row.failure)
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
        private val status: TextView = view.findViewById(R.id.ext_status)
        private val statusIcon: ImageView = view.findViewById(R.id.ext_status_icon)
        private val actions: View = view.findViewById(R.id.ext_actions)
        private val primary: Button = view.findViewById(R.id.ext_primary_action)
        private val remove: Button = view.findViewById(R.id.ext_remove)

        private fun resetActions() {
            actions.isVisible = false
            primary.isVisible = false
            primary.setOnClickListener(null)
            remove.isVisible = false
            remove.setOnClickListener(null)
        }

        private fun setStatus(visual: StatusVisual) {
            status.text = visual.text
            status.setTextColor(ContextCompat.getColor(itemView.context, visual.colorRes))
            statusIcon.setImageResource(visual.iconRes)
            statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, visual.colorRes))
        }

        private fun showPrimary(textRes: Int, action: () -> Unit) {
            actions.isVisible = true
            primary.isVisible = true
            primary.setText(textRes)
            primary.contentDescription = "${title.text} : ${primary.text}"
            primary.setOnClickListener { action() }
        }

        private fun showRemove(textRes: Int, action: () -> Unit) {
            actions.isVisible = true
            remove.isVisible = true
            remove.setText(textRes)
            remove.contentDescription = "${title.text} : ${remove.text}"
            remove.setOnClickListener { action() }
        }

        fun bindRepo(state: RepositoryState) {
            resetActions()
            title.text = state.manifest?.name ?: state.entry.name
            subtitle.text = state.error ?: state.entry.url
            setStatus(
                if (state.error == null) {
                    StatusVisual(
                        itemView.context.getString(R.string.extensions_status_ok),
                        R.drawable.ic_ext_active,
                        R.color.missa_green,
                    )
                } else {
                    StatusVisual(
                        itemView.context.getString(R.string.extensions_status_error),
                        R.drawable.ic_ext_error,
                        R.color.missa_error,
                    )
                },
            )
            showRemove(R.string.extensions_remove) { onRepoRemove(state) }
        }

        fun bindPlugin(
            remote: RemotePlugin,
            installed: InstalledExtension?,
            failure: ExtensionFailure?,
        ) {
            resetActions()
            val entry = remote.entry
            val currentFailure = failure?.takeIf {
                installed == null && it.version == entry.versionInt
            }
            title.text = entry.normalizedName
            subtitle.text = buildString {
                append("v").append(entry.versionInt)
                if (installed != null) append(" · installée v").append(installed.version)
                if (currentFailure != null) {
                    append(" · ").append(currentFailure.message)
                } else {
                    entry.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
            }

            when {
                entry.isDisabledByAuthor -> setStatus(statusDisabled(author = true))
                !entry.isCompatible -> setStatus(statusIncompatible())
                installed != null && !installed.enabled -> setStatus(
                    statusDisabled(author = installed.status == PluginEntry.STATUS_DOWN),
                )
                installed != null && entry.versionInt > installed.version -> setStatus(statusUpdate())
                installed != null && ExtensionLoader.isLoaded(installed.internalName) -> {
                    setStatus(statusActive())
                }
                installed != null -> setStatus(statusNotLoaded())
                currentFailure != null -> setStatus(statusFailure(currentFailure))
                else -> setStatus(statusAvailable())
            }

            when {
                entry.isDisabledByAuthor || !entry.isCompatible -> Unit
                currentFailure != null && currentFailure.errorType == ExtensionErrorType.INCOMPATIBLE -> {
                    showPrimary(R.string.extensions_details) { onFailureDetails(currentFailure) }
                }
                currentFailure != null -> {
                    showPrimary(R.string.extensions_retry) { onPluginAction(remote) }
                }
                installed == null -> showPrimary(R.string.extensions_install) { onPluginAction(remote) }
                installed != null && entry.versionInt > installed.version -> {
                    showPrimary(R.string.extensions_update) { onPluginAction(remote) }
                }
                installed != null && !installed.enabled -> Unit
                installed != null && !ExtensionLoader.isLoaded(installed.internalName) -> {
                    showPrimary(R.string.extensions_retry) { onPluginAction(remote) }
                }
            }
            if (installed != null) {
                showRemove(R.string.extensions_uninstall) { onPluginUninstall(installed) }
            }
        }

        fun bindOrphan(installed: InstalledExtension) {
            resetActions()
            title.text = installed.name
            subtitle.text = "Installée localement · v${installed.version} · ${installed.filePath}"
            setStatus(
                if (installed.enabled && ExtensionLoader.isLoaded(installed.internalName)) {
                    statusActive()
                } else if (!installed.enabled) {
                    statusDisabled(author = installed.status == PluginEntry.STATUS_DOWN)
                } else {
                    statusNotLoaded()
                },
            )
            showRemove(R.string.extensions_uninstall) { onPluginUninstall(installed) }
        }

        fun bindFailure(failure: ExtensionFailure) {
            resetActions()
            title.text = failure.name
            subtitle.text = "Échec v${failure.version} · ${failure.message}"
            setStatus(statusFailure(failure))
            showPrimary(R.string.extensions_details) { onFailureDetails(failure) }
        }

        private fun statusActive() = StatusVisual(
            itemView.context.getString(R.string.extensions_active),
            R.drawable.ic_ext_active,
            R.color.missa_green,
        )

        private fun statusAvailable() = StatusVisual(
            itemView.context.getString(R.string.extensions_status_ok),
            R.drawable.ic_ext_update,
            R.color.missa_text_secondary,
        )

        private fun statusUpdate() = StatusVisual(
            itemView.context.getString(R.string.extensions_update_available),
            R.drawable.ic_ext_update,
            R.color.missa_yellow,
        )

        private fun statusNotLoaded() = StatusVisual(
            itemView.context.getString(R.string.extensions_not_loaded),
            R.drawable.ic_ext_error,
            R.color.missa_yellow,
        )

        private fun statusDisabled(author: Boolean) = StatusVisual(
            itemView.context.getString(
                if (author) R.string.extensions_disabled_author else R.string.extensions_disabled,
            ),
            R.drawable.ic_ext_disabled,
            R.color.missa_text_secondary,
        )

        private fun statusIncompatible() = StatusVisual(
            itemView.context.getString(R.string.extensions_incompatible),
            R.drawable.ic_ext_error,
            R.color.missa_error,
        )

        private fun statusFailure(failure: ExtensionFailure): StatusVisual {
            val textRes = when (failure.errorType) {
                ExtensionErrorType.INCOMPATIBLE -> R.string.extensions_incompatible
                ExtensionErrorType.MISSING_DEPENDENCY -> R.string.extensions_error_dependency
                ExtensionErrorType.BAD_MANIFEST -> R.string.extensions_error_manifest
                ExtensionErrorType.LOAD_FAILED -> R.string.extensions_status_error
            }
            return StatusVisual(
                itemView.context.getString(textRes),
                R.drawable.ic_ext_error,
                R.color.missa_error,
            )
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_REPO = 1
        const val TYPE_PLUGIN = 2
        const val TYPE_EMPTY = 3
    }
}
