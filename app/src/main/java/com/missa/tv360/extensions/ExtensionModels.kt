package com.missa.tv360.extensions

import com.google.gson.annotations.SerializedName

/**
 * Modèles du dépôt d'extensions.
 *
 * Trois niveaux, comme dans le modèle « repo.json → plugins.json → artefact » :
 *  1. [RepositoryManifest] : le fichier `repo.json` (pointeur) ;
 *  2. [PluginEntry] : une ligne de `plugins.json` (une extension publiée) ;
 *  3. [InstalledExtension] : ce que l'application a réellement installé.
 *
 * IMPORTANT — tous les champs lus depuis le réseau sont DÉCLARÉS NULLABLES :
 * Gson instancie les classes sans appeler leur constructeur, donc les valeurs
 * par défaut Kotlin ne s'appliquent pas à un champ absent du JSON. Les
 * accesseurs de confort (`normalizedName`, `versionInt`, …) centralisent les
 * replis et évitent tout `NullPointerException` sur une réponse incomplète.
 */

/** `repo.json` — petit fichier stable que l'utilisateur colle dans l'application. */
data class RepositoryManifest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("manifestVersion") val manifestVersion: Int? = null,
    @SerializedName("pluginLists") val pluginLists: List<String?>? = null,
) {
    /** Listes d'extensions, nettoyées des entrées vides ou nulles. */
    val lists: List<String>
        get() = pluginLists.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
}

/** Une entrée de `plugins.json`. */
data class PluginEntry(
    @SerializedName("url") val url: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("internalName") val internalName: String? = null,
    @SerializedName("version") val version: Int? = null,
    /** 0 = désactivée par l'auteur, 1 = ok, 2 = lente, 3 = bêta. */
    @SerializedName("status") val status: Int? = null,
    @SerializedName("apiVersion") val apiVersion: Int? = null,
    @SerializedName("authors") val authors: List<String?>? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("tvTypes") val tvTypes: List<String?>? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    /** `sha256-<hex>` ou `sha256:<hex>` ; facultatif comme chez Cloudstream, vérifié si présent. */
    @SerializedName("fileHash") val fileHash: String? = null,
) {
    val normalizedInternalName: String get() = internalName.orEmpty().trim()

    val normalizedName: String
        get() = name?.takeIf { it.isNotBlank() } ?: normalizedInternalName

    val versionInt: Int get() = version ?: 0

    val statusInt: Int get() = status ?: STATUS_OK

    /** Les manifestes Cloudstream historiques ne déclarent pas apiVersion : valeur 0. */
    val apiVersionInt: Int get() = apiVersion ?: 0

    val authorsList: List<String> get() = authors.orEmpty().filterNotNull()

    val tvTypesList: List<String> get() = tvTypes.orEmpty().filterNotNull()

    val urlOrNull: String? get() = url?.trim()?.takeIf { it.isNotEmpty() }

    val hashOrNull: String? get() = fileHash?.trim()?.takeIf { it.isNotEmpty() }

    val isDisabledByAuthor: Boolean get() = statusInt == STATUS_DOWN

    /** 1 = contrat MISSA/Cloudstream déclaré, 0 = manifeste Cloudstream sans version. */
    val isCompatible: Boolean
        get() = apiVersionInt == 0 || apiVersionInt == com.missa.tv360.api.PLUGIN_API_VERSION

    companion object {
        const val STATUS_DOWN = 0
        const val STATUS_OK = 1
        const val STATUS_SLOW = 2
        const val STATUS_BETA_ONLY = 3
    }
}

/** Extension réellement présente sur l'appareil (écrite par l'application). */
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

/**
 * Échec conservé séparément de l'installation : le fichier et l'entrée installée
 * sont purgés, mais la version en échec ne doit pas être réinstallée automatiquement.
 */
data class ExtensionFailure(
    val internalName: String,
    val repositoryUrl: String,
    val name: String,
    val version: Int,
    val message: String,
    val details: String? = null,
    val failedAt: Long = System.currentTimeMillis(),
)

/** Dépôt ajouté par l'utilisateur ; builtIn ne sert qu'à la migration des anciennes installations. */
data class RepositoryEntry(
    val url: String,
    val name: String,
    val builtIn: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Extension publiée par un dépôt distant, avec son dépôt d'origine. */
data class RemotePlugin(
    val entry: PluginEntry,
    val repositoryUrl: String,
)

/** État d'un dépôt après tentative de lecture. */
data class RepositoryState(
    val entry: RepositoryEntry,
    val manifest: RepositoryManifest? = null,
    val error: String? = null,
)

/** État exposé à l'interface (StateFlow). */
data class ExtensionsState(
    val repositories: List<RepositoryState> = emptyList(),
    val remote: List<RemotePlugin> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val failures: List<ExtensionFailure> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val lastRefreshAt: Long = 0L,
)
