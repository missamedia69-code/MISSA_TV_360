package com.missa.tv360.extensions

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.missa.tv360.api.MissaPlugin
import com.missa.tv360.api.PLUGIN_API_VERSION
import com.missa.tv360.api.PluginManifest
import com.missa.tv360.api.PluginRegistry
import dalvik.system.PathClassLoader
import java.io.File

/**
 * Chargeur commun MISSA / Cloudstream.
 *
 * Le nom de classe vient exclusivement du manifeste, comme dans Cloudstream :
 * aucun scan de DEX et aucun nom de provider inventé. Le classloader de l'app
 * est le parent afin que les `.cs3` utilisent la vraie bibliothèque embarquée.
 */
internal object ExtensionLoader {

    private const val TAG = "ExtensionLoader"
    private val loaded = LinkedHashMap<String, Loaded>()

    class Loaded(
        val internalName: String,
        val plugin: Any,
        val classLoader: PathClassLoader,
        val manifest: PluginManifest,
        val resourceAssets: AssetManager? = null,
    )

    fun isLoaded(internalName: String): Boolean = synchronized(loaded) {
        loaded.containsKey(internalName)
    }

    fun loadedNames(): List<String> = synchronized(loaded) { loaded.keys.toList() }

    fun load(context: Context, extension: InstalledExtension): Loaded {
        if (isLoaded(extension.internalName)) unload(extension.internalName)

        val file = File(extension.filePath)
        if (!file.isFile) {
            throw ExtensionException("Fichier d'extension absent : ${extension.filePath}")
        }
        file.setReadOnly()
        val manifest = ExtensionPackageValidator.validate(file)
        val className = manifest.pluginClassName?.trim().orEmpty()
        val classLoader = PathClassLoader(file.absolutePath, context.classLoader)
        val pluginClass = try {
            classLoader.loadClass(className)
        } catch (t: Throwable) {
            throw ExtensionException("Classe $className introuvable dans ${file.name} : ${details(t)}", t)
        }

        val isMissa = MissaPlugin::class.java.isAssignableFrom(pluginClass)
        val isCloudstream = BasePlugin::class.java.isAssignableFrom(pluginClass)
        if (!isMissa && !isCloudstream) {
            throw ExtensionException(
                "$className n'étend ni com.missa.tv360.api.MissaPlugin " +
                    "ni com.lagradost.cloudstream3.plugins.BasePlugin"
            )
        }
        if (isMissa && manifest.apiVersion != PLUGIN_API_VERSION) {
            throw ExtensionException(
                "« ${extension.internalName} » : apiVersion ${manifest.apiVersion} " +
                    "≠ API MISSA $PLUGIN_API_VERSION"
            )
        }
        if (isCloudstream && manifest.apiVersion !in setOf(0, PLUGIN_API_VERSION)) {
            throw ExtensionException(
                "« ${extension.internalName} » : apiVersion Cloudstream ${manifest.apiVersion} non pris en charge"
            )
        }

        val plugin = try {
            pluginClass.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            throw ExtensionException("Instanciation impossible de $className : ${details(t)}", t)
        }

        var resourceAssets: AssetManager? = null
        try {
            when (plugin) {
                is MissaPlugin -> {
                    plugin.filename = file.absolutePath
                    plugin.load()
                }
                is BasePlugin -> {
                    plugin.filename = file.absolutePath
                    if (manifest.requiresResources && plugin is Plugin) {
                        resourceAssets = createResources(context, file, plugin)
                    }
                    // Même distinction que PluginManager.loadPlugin : les anciens
                    // plugins Android reçoivent le Context, les multiplateformes load().
                    if (plugin is Plugin) plugin.load(context) else plugin.load()
                }
            }
        } catch (t: Throwable) {
            when (plugin) {
                is MissaPlugin -> plugin.filename?.let(PluginRegistry::clear)
                is BasePlugin -> runCatching { CloudstreamRuntime.unload(plugin) }
            }
            resourceAssets?.close()
            val method = if (plugin is Plugin) "load(Context)" else "load()"
            throw ExtensionException(
                "$method a échoué pour ${extension.internalName} : ${details(t)}",
                t,
            )
        }

        val entry = Loaded(extension.internalName, plugin, classLoader, manifest, resourceAssets)
        synchronized(loaded) { loaded[extension.internalName] = entry }
        val engine = if (plugin is BasePlugin) "Cloudstream" else "MISSA"
        Log.i(TAG, "Extension $engine chargée : ${extension.internalName} v${manifest.version ?: "?"}")
        return entry
    }

    private fun createResources(context: Context, file: File, plugin: Plugin): AssetManager {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(assets, file.absolutePath)
        @Suppress("DEPRECATION")
        plugin.resources = Resources(
            assets,
            context.resources.displayMetrics,
            context.resources.configuration,
        )
        return assets
    }

    /** Décharge une extension et retire tout ce qu'elle a publié. */
    fun unload(internalName: String) {
        val entry = synchronized(loaded) { loaded.remove(internalName) } ?: return
        when (val plugin = entry.plugin) {
            is MissaPlugin -> {
                runCatching { plugin.beforeUnload() }
                    .onFailure { Log.w(TAG, "beforeUnload a échoué pour $internalName", it) }
                plugin.filename?.let(PluginRegistry::clear)
            }
            is BasePlugin -> {
                runCatching { plugin.beforeUnload() }
                    .onFailure { Log.w(TAG, "beforeUnload a échoué pour $internalName", it) }
                runCatching { CloudstreamRuntime.unload(plugin) }
                    .onFailure { Log.w(TAG, "Nettoyage Cloudstream impossible pour $internalName", it) }
            }
        }
        entry.resourceAssets?.close()
        Log.i(TAG, "Extension déchargée : $internalName")
    }

    fun unloadAll() {
        loadedNames().forEach(::unload)
    }

    /** Remonte la cause racine : indispensable pour NoClassDefFoundError/ClassCastException. */
    private fun details(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .distinctBy { "${it::class.java.name}:${it.message}" }
            .toList()
        return chain.joinToString(" ← ") { cause ->
            val message = cause.message?.takeIf(String::isNotBlank) ?: "sans message"
            "${cause::class.java.simpleName}: $message"
        }
    }
}
