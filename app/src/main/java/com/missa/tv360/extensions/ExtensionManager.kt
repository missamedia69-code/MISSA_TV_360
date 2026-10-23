package com.missa.tv360.extensions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.missa.tv360.api.PluginLog
import com.missa.tv360.api.PluginRegistry
import com.missa.tv360.api.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Moteur d'extensions : dépôts → installation → chargement → contenus.
 *
 * Cycle de vie :
 *  1. [init] au démarrage de l'application (idempotent) ;
 *  2. [bootstrapIfNeeded] charge uniquement les extensions déjà installées,
 *     sans ajouter de dépôt ni de fournisseur préconfiguré ;
 *  3. [loadInstalled] charge les `.msa` déjà installés et enregistre leurs
 *     providers ;
 *  4. [refresh] met à jour les dépôts ajoutés manuellement, applique le « kill switch » (`status = 0`)
 *     et les mises à jour de version.
 *
 * Toutes les opérations sont sérialisées par un [Mutex] : l'interface, le
 * démarrage et le rafraîchissement automatique peuvent appeler sans se marcher
 * dessus (pas de double téléchargement, pas d'écriture concurrente des prefs).
 */
object ExtensionManager {

    private const val TAG = "Extensions"

    /** Bornes de sécurité (anti-DoS / anti-saturation mémoire). */
    private const val MAX_PLUGIN_LISTS = 10
    private const val MAX_REPO_BYTES = 64 * 1024
    private const val MAX_LIST_BYTES = 512 * 1024
    private const val MAX_ERROR_DETAILS = 6_000

    private val gson = Gson()
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var store: ExtensionStore? = null
    private var installer: ExtensionInstaller? = null

    private val _state = MutableStateFlow(ExtensionsState())
    val state: StateFlow<ExtensionsState> = _state

    // ── Initialisation ────────────────────────────────────────────────────

    fun init(context: Context) {
        if (store != null) return
        val ctx = context.applicationContext
        appContext = ctx
        store = ExtensionStore(ctx)
        installer = ExtensionInstaller(ctx)
        PluginLog.sink = { level, message ->
            if (level == "E") Log.e("Plugin", message) else Log.i("Plugin", message)
        }
        val current = store!!.installed()
        _state.value = ExtensionsState(
            installed = current,
            failures = store!!.failures(),
            lastRefreshAt = store!!.lastRefreshAt,
        )
    }

    /** Providers actuellement chargés (utilisé par le catalogue). */
    fun providers(): List<Provider> = PluginRegistry.providers()

    fun isInitialized(): Boolean = store != null

    // ── Chargement local ──────────────────────────────────────────────────

    /**
     * Charge les extensions déjà installées. Tout échec est purgé (fichier +
     * enregistrement) et mémorisé séparément : aucun nouvel essai au démarrage.
     */
    suspend fun loadInstalled(): Int = mutex.withLock { loadInstalledLocked() }

    private fun loadInstalledLocked(): Int {
        val context = appContext ?: return 0
        val store = this.store ?: return 0
        var loadedCount = 0
        val retained = mutableListOf<InstalledExtension>()

        store.installed().forEach { extension ->
            if (extension.status == PluginEntry.STATUS_DOWN) {
                retained += extension
                return@forEach
            }
            if (!extension.enabled) {
                // Les anciennes versions désactivaient précisément après un
                // échec. Il n'existe pas de désactivation manuelle dans l'UI.
                recordFailureAndPurge(
                    extension,
                    ExtensionException("Chargement précédent en échec."),
                )
                return@forEach
            }
            if (ExtensionLoader.isLoaded(extension.internalName)) {
                loadedCount++
                retained += extension
                return@forEach
            }
            try {
                ExtensionLoader.load(context, extension)
                loadedCount++
                retained += extension
            } catch (error: Throwable) {
                Log.w(TAG, "Chargement impossible puis purge : ${extension.internalName}", error)
                recordFailureAndPurge(extension, error)
            }
        }
        store.saveInstalled(retained)
        _state.value = _state.value.copy(
            installed = retained,
            failures = store.failures(),
        )
        return loadedCount
    }

    // ── Chargement au démarrage ──────────────────────────────────────────

    /** Aucun dépôt n'est ajouté automatiquement : seules les installations manuelles sont conservées. */
    suspend fun bootstrapIfNeeded(): Unit = mutex.withLock {
        val store = this.store ?: return@withLock
        // Migration depuis l'ancien bootstrap : les dépôts intégrés et leurs
        // extensions sont retirés, sans toucher aux dépôts ajoutés par l'utilisateur.
        store.repositories().filter { it.builtIn }.forEach { legacyRepository ->
            store.installed()
                .filter { it.repositoryUrl == legacyRepository.url }
                .forEach { uninstallLocked(it.internalName) }
            store.saveRepositories(store.repositories().filterNot { it.url == legacyRepository.url })
        }
        store.bootstrapDone = true
        loadInstalledLocked()
    }

    // ── Dépôts ────────────────────────────────────────────────────────────

    /** Ajoute un dépôt ; le manifeste est lu immédiatement pour validation. */
    suspend fun addRepository(rawUrl: String): Result<RepositoryManifest> = mutex.withLock {
        val store = this.store
            ?: return@withLock Result.failure(ExtensionException("Moteur non initialisé"))
        val url = normalizeRepositoryUrl(rawUrl)
        try {
            ExtensionHttp.checkUrl(url)
            val manifest = fetchManifest(url)
            val repos = store.repositories().filterNot { it.url == url } +
                RepositoryEntry(
                    url = url,
                    name = manifest.name?.takeIf { it.isNotBlank() } ?: url,
                )
            store.saveRepositories(repos)
            _state.value = _state.value.copy(
                message = "Dépôt ajouté : ${manifest.name ?: url}",
            )
            Result.success(manifest)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun removeRepository(url: String): Unit = mutex.withLock {
        val store = this.store ?: return@withLock
        // On retire d'abord les extensions du dépôt (fichiers + chargement),
        // sinon elles continueraient à publier des contenus après suppression.
        store.installed()
            .filter { it.repositoryUrl == url }
            .forEach { uninstallLocked(it.internalName) }
        store.saveRepositories(store.repositories().filterNot { it.url == url })
        store.saveFailures(store.failures().filterNot { it.repositoryUrl == url })
        _state.value = _state.value.copy(
            repositories = _state.value.repositories.filterNot { it.entry.url == url },
            remote = _state.value.remote.filterNot { it.repositoryUrl == url },
            failures = store.failures(),
            message = "Dépôt supprimé.",
        )
    }

    fun repositories(): List<RepositoryEntry> = store?.repositories().orEmpty()

    // ── Rafraîchissement / mises à jour ──────────────────────────────────

    /**
     * Relit tous les dépôts, applique le kill switch et les mises à jour.
     * @param installMissing installe aussi les extensions absentes des dépôts manuels.
     */
    suspend fun refresh(installMissing: Boolean = false): ExtensionsState = mutex.withLock {
        val store = this.store ?: return@withLock _state.value
        val installer = this.installer ?: return@withLock _state.value
        _state.value = _state.value.copy(busy = true, message = null)

        val repos = store.repositories()
        if (repos.isEmpty()) {
            _state.value = _state.value.copy(busy = false, message = "Aucun dépôt enregistré.")
            return@withLock _state.value
        }

        val states = mutableListOf<RepositoryState>()
        val sources = mutableListOf<Pair<String, RepositoryManifest>>()

        repos.forEach { repo ->
            val result = runCatching { fetchManifest(repo.url) }
            result.onSuccess { manifest ->
                states += RepositoryState(repo, manifest)
                sources += repo.url to manifest
            }.onFailure { t ->
                states += RepositoryState(repo, null, t.message ?: "Dépôt illisible")
            }
        }

        val remote = runCatching { collectPluginEntries(sources, installer) }
            .getOrElse { emptyList() }

        // Kill switch : une extension passée en status=0 est déchargée.
        applyRemoteStatuses(remote)

        // Mises à jour (et installations si demandé).
        if (store.autoUpdate || installMissing) {
            remote.forEach { item ->
                val entry = item.entry
                if (entry.isDisabledByAuthor) return@forEach
                val key = entry.normalizedInternalName
                // Une version qui a déjà échoué au chargement n'est jamais
                // réinstallée automatiquement. Une nouvelle version peut l'être.
                if (store.failures().any { it.internalName == key && it.version == entry.versionInt }) {
                    return@forEach
                }
                val installed = store.installed().firstOrNull { it.internalName == key }
                val missing = installed == null
                val outdated = installed != null && entry.versionInt > installed.version
                if ((missing && installMissing) || outdated) {
                    runCatching { installLocked(item) }
                        .onFailure { Log.w(TAG, "Mise à jour échouée : $key", it) }
                }
            }
        }

        store.lastRefreshAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            repositories = states,
            remote = remote,
            installed = store.installed(),
            failures = store.failures(),
            busy = false,
            lastRefreshAt = store.lastRefreshAt,
            message = messageFor(states, remote),
        )
        _state.value
    }

    // ── Installation / désinstallation ───────────────────────────────────

    suspend fun install(plugin: RemotePlugin): Result<InstalledExtension> = mutex.withLock {
        runCatching { installLocked(plugin) }
            .onFailure { _state.value = _state.value.copy(busy = false, message = it.message) }
    }

    private fun installLocked(plugin: RemotePlugin): InstalledExtension {
        val context = appContext ?: throw ExtensionException("Moteur non initialisé")
        val store = this.store ?: throw ExtensionException("Moteur non initialisé")
        val installer = this.installer ?: throw ExtensionException("Moteur non initialisé")

        _state.value = _state.value.copy(
            busy = true,
            message = "Installation de ${plugin.entry.normalizedName}…",
        )

        // 1) décharger l'ancienne version avant d'écraser son fichier
        ExtensionLoader.unload(plugin.entry.normalizedInternalName)

        // 2) télécharger + vérifier + écrire de façon atomique
        val record = installer.install(plugin.entry, plugin.repositoryUrl)

        // 3) persister l'état AVANT de charger (un crash de chargement ne doit
        //    pas laisser l'app croire que rien n'est installé)
        val updated = store.installed().filterNot { it.internalName == record.internalName } + record
        store.saveInstalled(updated)
        _state.value = _state.value.copy(installed = updated)

        // 4) charger ; tout échec supprime immédiatement le fichier et le record.
        val loaded = runCatching { ExtensionLoader.load(context, record) }
        if (loaded.isFailure) {
            val failure = loaded.exceptionOrNull()
                ?: ExtensionException("Échec de chargement inconnu")
            recordFailureAndPurge(record, failure)
            _state.value = _state.value.copy(
                installed = store.installed(),
                failures = store.failures(),
                busy = false,
            )
            throw ExtensionException(
                "${plugin.entry.normalizedName} non chargé ; installation purgée. ${failure.message}",
                failure,
            )
        }

        store.saveFailures(store.failures().filterNot { it.internalName == record.internalName })
        _state.value = _state.value.copy(
            busy = false,
            message = "${plugin.entry.normalizedName} v${plugin.entry.versionInt} installé.",
            installed = store.installed(),
            failures = store.failures(),
        )
        return record
    }

    suspend fun uninstall(internalName: String): Unit = mutex.withLock {
        uninstallLocked(internalName)
        _state.value = _state.value.copy(
            installed = store?.installed().orEmpty(),
            message = "Extension supprimée.",
        )
    }

    private fun uninstallLocked(internalName: String) {
        val store = this.store ?: return
        val extension = store.installed().firstOrNull { it.internalName == internalName } ?: return
        ExtensionLoader.unload(internalName)
        runCatching { File(extension.filePath).delete() }
            .onFailure { Log.w(TAG, "Suppression impossible : ${extension.filePath}", it) }
        store.saveInstalled(store.installed().filterNot { it.internalName == internalName })
        store.saveFailures(store.failures().filterNot { it.internalName == internalName })
        _state.value = _state.value.copy(
            installed = store.installed(),
            failures = store.failures(),
        )
    }

    private fun recordFailureAndPurge(extension: InstalledExtension, error: Throwable) {
        val store = this.store ?: return
        ExtensionLoader.unload(extension.internalName)
        val artifact = File(extension.filePath)
        if (artifact.exists() && !artifact.delete()) {
            Log.w(TAG, "Artefact en échec non supprimé : ${artifact.absolutePath}")
        }
        store.saveInstalled(store.installed().filterNot { it.internalName == extension.internalName })

        val failure = ExtensionFailure(
            internalName = extension.internalName,
            repositoryUrl = extension.repositoryUrl,
            name = extension.name,
            version = extension.version,
            message = error.message?.takeIf(String::isNotBlank) ?: "Chargement impossible.",
            details = Log.getStackTraceString(error).take(MAX_ERROR_DETAILS),
        )
        store.saveFailures(
            store.failures().filterNot { it.internalName == extension.internalName } + failure,
        )
    }

    // ── Interne ───────────────────────────────────────────────────────────

    private fun applyRemoteStatuses(remote: List<RemotePlugin>) {
        val store = this.store ?: return
        val byName = remote.associateBy { it.entry.normalizedInternalName }
        val updated = store.installed().map { extension ->
            val entry = byName[extension.internalName]?.entry ?: return@map extension
            if (entry.isDisabledByAuthor && extension.enabled) {
                ExtensionLoader.unload(extension.internalName)
                _state.value = _state.value.copy(
                    message = "« ${extension.name} » a été désactivée par son auteur.",
                )
                extension.copy(enabled = false, status = PluginEntry.STATUS_DOWN)
            } else if (!entry.isDisabledByAuthor && extension.status == PluginEntry.STATUS_DOWN) {
                extension.copy(enabled = true, status = entry.statusInt)
            } else {
                extension.copy(status = entry.statusInt)
            }
        }
        store.saveInstalled(updated)
    }

    private fun collectPluginEntries(
        sources: List<Pair<String, RepositoryManifest>>,
        installer: ExtensionInstaller,
    ): List<RemotePlugin> {
        val out = mutableListOf<RemotePlugin>()
        sources.forEach { (repoUrl, manifest) ->
            manifest.lists.take(MAX_PLUGIN_LISTS).forEach { listUrl ->
                val entries = runCatching { fetchPluginList(listUrl) }
                    .getOrElse {
                        Log.w(TAG, "Liste illisible : $listUrl", it)
                        emptyList()
                    }
                entries
                    .filter { it.normalizedInternalName.isNotEmpty() }
                    .forEach { entry -> out += RemotePlugin(entry, repoUrl) }
            }
        }
        return out.distinctBy { "${it.repositoryUrl}|${it.entry.normalizedInternalName}" }
    }

    private fun fetchManifest(url: String): RepositoryManifest {
        ExtensionHttp.checkUrl(url)
        val text = ExtensionHttp.getText(url, MAX_REPO_BYTES)
        val manifest = try {
            gson.fromJson(text, RepositoryManifest::class.java)
        } catch (e: Exception) {
            throw ExtensionException("repo.json illisible : $url", e)
        } ?: throw ExtensionException("repo.json vide : $url")
        if (manifest.lists.isEmpty()) {
            throw ExtensionException("repo.json sans pluginLists exploitables : $url")
        }
        if (manifest.lists.size > MAX_PLUGIN_LISTS) {
            throw ExtensionException(
                "repo.json déclare ${manifest.lists.size} listes (maximum $MAX_PLUGIN_LISTS)."
            )
        }
        return manifest
    }

    private fun fetchPluginList(url: String): List<PluginEntry> {
        ExtensionHttp.checkUrl(url)
        val text = ExtensionHttp.getText(url, MAX_LIST_BYTES)
        val type = object : TypeToken<List<PluginEntry>>() {}.type
        return try {
            gson.fromJson<List<PluginEntry>>(text, type) ?: emptyList()
        } catch (e: Exception) {
            throw ExtensionException("plugins.json illisible : $url", e)
        }
    }

    /** Accepte une URL nue ou le format de partage « Nom : URL ». */
    private fun normalizeRepositoryUrl(raw: String): String {
        var value = raw.trim()
        if (value.contains(" : ")) {
            value = value.substringAfter(" : ").trim()
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://$value"
        }
        return value
    }

    private fun messageFor(states: List<RepositoryState>, remote: List<RemotePlugin>): String {
        val failures = states.count { it.error != null }
        return when {
            failures == 0 && remote.isEmpty() -> "Dépôt(s) lu(s) : aucune extension publiée."
            failures == 0 -> "${remote.size} extension(s) disponible(s)."
            failures == states.size -> "Tous les dépôts sont injoignables."
            else -> "${states.size - failures} dépôt(s) lisible(s), $failures en échec."
        }
    }
}
