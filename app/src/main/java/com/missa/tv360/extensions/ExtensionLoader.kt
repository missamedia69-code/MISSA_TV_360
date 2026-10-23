package com.missa.tv360.extensions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.missa.tv360.api.MissaPlugin
import com.missa.tv360.api.PLUGIN_API_VERSION
import com.missa.tv360.api.PLUGIN_MANIFEST_ENTRY
import com.missa.tv360.api.PluginManifest
import com.missa.tv360.api.PluginRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.util.zip.ZipFile

/**
 * Chargement des extensions dans le processus de l'application.
 * Gère la résolution robuste multi-candidats des classes de plugins (compatibilité Cloudstream / Lagradost).
 */
internal object ExtensionLoader {

    private val TAG = "ExtensionLoader"

    private val gson = Gson()
    private val loaded = LinkedHashMap<String, Loaded>()

    class Loaded(
        val internalName: String,
        val plugin: MissaPlugin,
        val classLoader: PathClassLoader,
        val manifest: PluginManifest,
    )

    fun isLoaded(internalName: String): Boolean = synchronized(loaded) {
        loaded.containsKey(internalName)
    }

    fun loadedNames(): List<String> = synchronized(loaded) { loaded.keys.toList() }

    fun load(context: Context, extension: InstalledExtension): Loaded {
        if (isLoaded(extension.internalName)) unload(extension.internalName)

        val file = File(extension.filePath)
        if (!file.exists()) {
            throw ExtensionException("Fichier d'extension absent : ${extension.filePath}")
        }

        val manifest: PluginManifest = readManifest(file)
            ?: throw ExtensionException("manifest.json absent de ${file.name}")

        val manifestApi = manifest.apiVersion
        if (manifestApi != PLUGIN_API_VERSION && manifestApi != 0) {
            throw ExtensionException(
                "« ${extension.internalName} » : apiVersion $manifestApi " +
                    "≠ API de l'application $PLUGIN_API_VERSION"
            )
        }

        val className = manifest.pluginClassName?.takeIf { it.isNotBlank() }
            ?: "com.lagradost.UnJourUnFilmProvider"

        val classLoader = PathClassLoader(file.absolutePath, context.classLoader)
        val candidateNames = mutableListOf<String>()
        candidateNames.add(className)
        if (className.endsWith("Plugin")) {
            candidateNames.add(className.removeSuffix("Plugin"))
        } else {
            candidateNames.add(className + "Plugin")
        }
        val simpleName = className.substringAfterLast('.')
        candidateNames.add("com.lagradost.$simpleName")
        candidateNames.add("com.lagradost.cloudstream3.plugins.$simpleName")
        candidateNames.add("com.lagradost.UnJourUnFilmProvider")
        candidateNames.add("com.lagradost.UnJourUnFilmProviderPlugin")

        var pluginClass: Class<*>? = null
        var lastError: Throwable? = null

        for (cand in candidateNames) {
            try {
                pluginClass = classLoader.loadClass(cand)
                break
            } catch (t: Throwable) {
                lastError = t
            }
        }

        if (pluginClass == null) {
            throw ExtensionException("Classe introuvable dans ${file.name} (tentatives : $candidateNames)", lastError)
        }

        val plugin = try {
            pluginClass.getDeclaredConstructor().newInstance() as MissaPlugin
        } catch (t: Throwable) {
            try {
                val instanceField = pluginClass.getDeclaredField("INSTANCE")
                instanceField.isAccessible = true
                instanceField.get(null) as MissaPlugin
            } catch (_: Throwable) {
                throw ExtensionException("Instanciation impossible de $pluginClass", t)
            }
        }

        plugin.filename = file.absolutePath
        try {
            plugin.load()
        } catch (t: Throwable) {
            PluginRegistry.clear(file.absolutePath)
            throw ExtensionException("load() a échoué pour ${extension.internalName}", t)
        }

        val entry = Loaded(extension.internalName, plugin, classLoader, manifest)
        synchronized(loaded) { loaded[extension.internalName] = entry }
        Log.i(TAG, "Extension chargée : ${extension.internalName} v${manifest.version ?: "?"}")
        return entry
    }

    fun unload(internalName: String) {
        val entry = synchronized(loaded) { loaded.remove(internalName) } ?: return
        runCatching { entry.plugin.beforeUnload() }
            .onFailure { Log.w(TAG, "beforeUnload a échoué pour $internalName", it) }
        entry.plugin.filename?.let { PluginRegistry.clear(it) }
        Log.i(TAG, "Extension déchargée : $internalName")
    }

    fun unloadAll() {
        loadedNames().forEach { unload(it) }
    }

    private fun readManifest(file: File): PluginManifest? = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(PLUGIN_MANIFEST_ENTRY) ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            gson.fromJson(text, PluginManifest::class.java)
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Manifeste illisible : ${file.absolutePath}", t)
        null
    }
}
