package com.missa.tv360.extensions

import com.google.gson.annotations.SerializedName

/**
 * Modèles du dépôt d'extensions.
 */
data class RepositoryManifest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("manifestVersion") val manifestVersion: Int? = null,
    @SerializedName("pluginLists") val pluginLists: List<String?>? = null,
) {
    val lists: List<String>
        get() = pluginLists.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
}

data class PluginEntry(
    @SerializedName("url") val url: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("internalName") val internalName: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("apiVersion") val apiVersion: Int? = null,
    @SerializedName("authors") val authors: List<String?>? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("tvTypes") val tvTypes: List<String?>? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("fileHash") val fileHash: String? = null,
) {
    val normalizedInternalName: String get() = internalName.orEmpty().trim()

    val normalizedName: String
        get() = name?.takeIf { it.isNotBlank() } ?: normalizedInternalName

    val versionInt: Int get() = version ?: 0

    val statusInt: Int get() = status ?: STATUS_OK

    val apiVersionInt: Int get() = apiVersion ?: 0

    val authorsList: List<String> get() = authors.orEmpty().filterNotNull()

    val tvTypesList: List<String> get() = tvTypes.orEmpty().filterNotNull()

    val urlOrNull: String? get() = url?.trim()?.takeIf { it.isNotEmpty() }

    val hashOrNull: String? get() = fileHash?.trim()?.takeIf { it.isNotEmpty() }

    val isDisabledByAuthor: Boolean get() = statusInt == STATUS_DOWN

    // Tolère apiVersion == 0 ou non spécifié pour charger les extensions existantes
    val isCompatible: Boolean get() = apiVersionInt == com.missa.tv360.api.PLUGIN_API_VERSION || apiVersionInt == 0

    companion object {
        const val STATUS_DOWN = 0
        const val STATUS_OK = 1
        const val STATUS_SLOW = 2
        const val STATUS_BETA_ONLY = 3
    }
}

data class InstalledExtension(
    val internalName: String,
    val repositoryUrl: String,
    val name: String,
    val version: Int,
    val filePath: String,
    val apiVersion: Int,
    val status: Int = PluginEntry.STATUS_OK,
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
)

data class RepositoryEntry(
    val url: String,
    val name: String,
    val builtIn: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

data class RemotePlugin(
    val entry: PluginEntry,
    val repositoryUrl: String,
)

data class RepositoryState(
    val entry: RepositoryEntry,
    val manifest: RepositoryManifest? = null,
    val error: String? = null,
)

data class ExtensionsState(
    val repositories: List<RepositoryState> = emptyList(),
    val remote: List<RemotePlugin> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val lastRefreshAt: Long = 0L,
)
