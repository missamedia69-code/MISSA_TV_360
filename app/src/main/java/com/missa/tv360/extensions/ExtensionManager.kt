package com.missa.tv360.extensions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.missa.tv360.BuildConfig
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
 *  2. [bootstrapIfNeeded] au premier lancement : ajoute le dépôt intégré
 *     (`BuildConfig.EXTENSION_REPO_URL`), lit `repo.json` puis les
 *     `pluginLists`, télécharge et installe les extensions publiées ;
 *  3. [loadInstalled] charge les `.msa` déjà installés et enregistre leurs
 *     providers ;
 *  4. [refresh] met à jour la liste, applique le « kill switch » (`status = 0`)
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
    private const val MAX_AUTO_INSTALL = 16
    private const val MAX_BOOTSTRAP_ATTEMPTS = 5

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
            lastRefreshAt = store!!.lastRefreshAt,
        )
    }

    /** Providers actuellement chargés (utilisé par le catalogue). */
    fun providers(): List<Provider> = PluginRegistry.providers()

    fun isInitialized(): Boolean = store != null

    // ── Chargement local ──────────────────────────────────────────────────

    /**
     * Charge les extensions déjà installées.
     * Une extension qui ne se charge plus est désactivée (le fichier reste sur
     * l'appareil : réinstallable depuis l'écran Extensions).
     */
    suspend fun loadInstalled(): Int = mutex.withLock { loadInstalledLocked() }

    private fun loadInstalledLocked(): Int {
        val context = appContext ?: return 0
        val store = this.store ?: return 0
        var loadedCount = 0
        val updated = store.installed().map { extension ->
            if (!extension.enabled || extension.status == PluginEntry.STATUS_DOWN) return@map extension
            if (ExtensionLoader.isLoaded(extension.internalName)) {
                loadedCount++
                return@map extension
            }
            val result = runCatching { ExtensionLoader.load(context, extension) }
            if (result.isSuccess) {
                loadedCount++
                extension
            } else {
                Log.w(TAG, "Chargement impossible : ${extension.internalName}", result.exceptionOrNull())
                extension.copy(enabled = false)
            }
        }
        store.saveInstalled(updated)
        _state.value = _state.value.copy(installed = updated)
        return loadedCount
    }

    // ── Premier démarrage ─────────────────────────────────────────────────

    /**
     * Bootstrap : n'exécute quelque chose qu'une seule fois.
     *
     * Volontairement tolérant à l'échec : si le dépôt est injoignable, le
     * drapeau n'est PAS posé et l'application retentera au prochain démarrage
     * (dans la limite de [MAX_BOOTSTRAP_ATTEMPTS]).
     */
    suspend fun bootstrapIfNeeded(): Unit = mutex.withLock {
        val store = this.store ?: return@withLock
        val installer = this.installer ?: return@withLock

        if (store.bootstrapDone) {
            loadInstalledLocked()
            return@withLock
        }
        if (store.bootstrapAttempts >= MAX_BOOTSTRAP_ATTEMPTS) {
            loadInstalledLocked()
            return@withLock
        }

        val repoUrl = BuildConfig.EXTENSION_REPO_URL.trim()
        if (repoUrl.isEmpty()) {
            store.bootstrapDone = true
            loadInstalledLocked()
            return@withLock
        }

        store.bootstrapAttempts = store.bootstrapAttempts + 1
        _state.value = _state.value.copy(busy = true, message = null)

        try {
            // Le manifeste est validé AVANT de marquer le bootstrap comme fait.
            // L'URL principale peut être indisponible (site pas encore
            // déployé, coupure) : on essaie alors le miroir publié avec la
            // même release que les archives.
            var manifest: RepositoryManifest? = null
            var chosenUrl: String? = null
            for (candidate in bootstrapCandidates(repoUrl)) {
                val fetched = try {
                    fetchManifest(candidate)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Dépôt injoignable : $candidate", e)
                    null
                }
                if (fetched != null) {
                    manifest = fetched
                    chosenUrl = candidate
                    break
                }
            }
            val activeManifest = manifest
                ?: throw ExtensionException("Aucun dépôt d'extensions joignable.")
            val activeUrl = chosenUrl ?: repoUrl
            if (activeUrl != repoUrl) {
                Log.i(TAG, "Bootstrap via le miroir : $activeUrl")
            }

            val repos = store.repositories().toMutableList()
            if (repos.none { it.url == activeUrl }) {
                repos += RepositoryEntry(
                    url = activeUrl,
                    name = activeManifest.name?.takeIf { it.isNotBlank() } ?: "Dépôt intégré",
                    builtIn = true,
                )
                store.saveRepositories(repos)
            }
            store.bootstrapDone = true

            val entries = collectPluginEntries(listOf(activeUrl to activeManifest), installer)
            var installedCount = 0
            entries.take(MAX_AUTO_INSTALL).forEach { remote ->
                if (remote.entry.isDisabledByAuthor) return@forEach
                val name = remote.entry.normalizedInternalName
                val already = store.installed().firstOrNull { it.internalName == name }
                if (already != null && already.version >= remote.entry.versionInt) return@forEach
                runCatching { installLocked(remote) }
                    .onSuccess { installedCount++ }
                    .onFailure { Log.w(TAG, "Installation bootstrap échouée : $name", it) }
            }

            loadInstalledLocked()
            store.lastRefreshAt = System.currentTimeMillis()
            _state.value = _state.value.copy(
                busy = false,
                message = if (installedCount > 0) {
                    "$installedCount extension(s) installée(s) au premier démarrage."
                } else {
                    "Dépôt d'extensions à jour."
                },
                lastRefreshAt = store.lastRefreshAt,
            )
            Log.i(TAG, "Bootstrap terminé : $installedCount extension(s), ${PluginRegistry.size()} provider(s)")
        } catch (t: Throwable) {
            _state.value = _state.value.copy(busy = false, message = "Bootstrap indisponible : ${t.message}")
            Log.w(TAG, "Bootstrap impossible ($repoUrl)", t)
        }
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
        _state.value = _state.value.copy(
            repositories = _state.value.repositories.filterNot { it.entry.url == url },
            remote = _state.value.remote.filterNot { it.repositoryUrl == url },
            message = "Dépôt supprimé.",
        )
    }

    fun repositories(): List<RepositoryEntry> = store?.repositories().orEmpty()

    // ── Rafraîchissement / mises à jour ──────────────────────────────────

    /**
     * Relit tous les dépôts, applique le kill switch et les mises à jour.
     * @param installMissing installe aussi les extensions absentes (bootstrap).
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

        // 4) charger ; en cas d'échec on garde le fichier mais on désactive
        val loaded = runCatching { ExtensionLoader.load(context, record) }
        if (loaded.isFailure) {
            val failure = loaded.exceptionOrNull()
            val disabled = updated.map {
                if (it.internalName == record.internalName) it.copy(enabled = false) else it
            }
            store.saveInstalled(disabled)
            _state.value = _state.value.copy(installed = disabled, busy = false)
            throw ExtensionException(
                "${plugin.entry.normalizedName} installé mais non chargé : ${failure?.message}",
                failure,
            )
        }

        _state.value = _state.value.copy(
            busy = false,
            message = "${plugin.entry.normalizedName} v${plugin.entry.versionInt} installé.",
            installed = store.installed(),
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
        _state.value = _state.value.copy(installed = store.installed())
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

    /**
     * URL de dépôt à essayer au bootstrap : l'URL configurée d'abord, puis le
     * miroir de la release si elle est renseignée et différente.
     */
    private fun bootstrapCandidates(primary: String): List<String> {
        val mirror = BuildConfig.EXTENSION_REPO_MIRROR_URL.trim()
        if (mirror.isEmpty() || mirror == primary) return listOf(primary)
        return listOf(primary, mirror)
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
