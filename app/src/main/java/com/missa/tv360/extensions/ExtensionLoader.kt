package com.missa.tv360.extensions

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.missa.tv360.api.MissaPlugin
import com.missa.tv360.api.PluginManifest
import com.missa.tv360.api.PluginRegistry
import com.missa.tv360.cloudstream.compat.CloudstreamCompatRegistry
import dalvik.system.PathClassLoader
import java.io.File

/** Chargeur des extensions natives MISSA et du prototype Cloudstream. */
internal object ExtensionLoader {

    private const val TAG = "ExtensionLoader"
    private val loaded = LinkedHashMap<String, Loaded>()

    class Loaded(
        val internalName: String,
        val plugin: Any,
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
        if (!file.isFile) {
            throw ExtensionException("Fichier d'extension absent : ${extension.filePath}")
        }
        file.setReadOnly()

        // Un seul nom de classe est lu depuis le manifeste. Le marqueur DEX
        // choisit le contrat, sans essayer une liste de noms de classes.
        return if (ExtensionPackageValidator.cloudstreamDependency(file) != null) {
            loadCloudstream(context, extension, file)
        } else {
            loadNative(context, extension, file)
        }
    }

    private fun loadNative(
        context: Context,
        extension: InstalledExtension,
        file: File,
    ): Loaded {
        // Dépendances et manifeste sont contrôlés AVANT PathClassLoader.
        val manifest = ExtensionPackageValidator.validateNative(file)
        val className = manifest.pluginClassName?.trim().orEmpty()
        val classLoader = PathClassLoader(file.absolutePath, context.classLoader)
        val pluginClass = try {
            classLoader.loadClass(className)
        } catch (t: Throwable) {
            throw classFailure(className, t)
        }

        if (!MissaPlugin::class.java.isAssignableFrom(pluginClass)) {
            throw ExtensionException(
                "$className n'étend pas com.missa.tv360.api.MissaPlugin.",
                type = ExtensionErrorType.INCOMPATIBLE,
                technicalDetails = "La classe du manifeste ne respecte pas le contrat natif MISSA.",
            )
        }

        val plugin = try {
            pluginClass.getDeclaredConstructor().newInstance() as MissaPlugin
        } catch (t: Throwable) {
            throw dependencyFailure(t)
                ?: ExtensionException(
                    "Instanciation impossible de $className : ${rootMessage(t)}.",
                    t,
                    ExtensionErrorType.LOAD_FAILED,
                    Log.getStackTraceString(t),
                )
        }

        plugin.filename = file.absolutePath
        try {
            plugin.load()
        } catch (t: Throwable) {
            PluginRegistry.clear(file.absolutePath)
            throw ExtensionException(
                "load() a échoué : ${rootMessage(t)}.",
                t,
                ExtensionErrorType.LOAD_FAILED,
                Log.getStackTraceString(t),
            )
        }

        return remember(extension, plugin, classLoader, manifest)
    }

    private fun loadCloudstream(
        context: Context,
        extension: InstalledExtension,
        file: File,
    ): Loaded {
        // Les .cs3 n'ont pas d'apiVersion MISSA : leur contrat est BasePlugin.
        val manifest = ExtensionPackageValidator.validate(file)
        val className = manifest.pluginClassName?.trim().orEmpty()
        AcraApplication.setContext(context)
        CloudStreamApp.setApplicationContext(context)
        val classLoader = PathClassLoader(file.absolutePath, context.classLoader)
        val pluginClass = try {
            classLoader.loadClass(className)
        } catch (t: Throwable) {
            throw classFailure(className, t)
        }

        if (!BasePlugin::class.java.isAssignableFrom(pluginClass)) {
            throw ExtensionException(
                "$className n'étend pas BasePlugin Cloudstream.",
                type = ExtensionErrorType.INCOMPATIBLE,
                technicalDetails = "La classe du manifeste ne respecte pas le contrat Cloudstream pris en charge.",
            )
        }

        val plugin = try {
            pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
        } catch (t: Throwable) {
            throw dependencyFailure(t)
                ?: ExtensionException(
                    "Instanciation Cloudstream impossible de $className : ${rootMessage(t)}.",
                    t,
                    ExtensionErrorType.LOAD_FAILED,
                    Log.getStackTraceString(t),
                )
        }

        plugin.filename = file.absolutePath
        try {
            if (plugin is Plugin && manifest.requiresResources) {
                attachResources(context, plugin, file)
            }
            // Ne pas remplacer ce dispatch par BasePlugin.load() : les plugins
            // Cloudstream qui reçoivent un Context l'utilisent réellement.
            if (plugin is Plugin) plugin.load(context) else plugin.load()
        } catch (t: Throwable) {
            CloudstreamCompatRegistry.unload(file.absolutePath)
            throw dependencyFailure(t)
                ?: ExtensionException(
                    "Chargement Cloudstream impossible : ${rootMessage(t)}.",
                    t,
                    ExtensionErrorType.LOAD_FAILED,
                    Log.getStackTraceString(t),
                )
        }

        return remember(extension, plugin, classLoader, manifest)
    }

    private fun attachResources(context: Context, plugin: Plugin, file: File) {
        val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            .invoke(assets, file.absolutePath) as Int
        if (cookie == 0) throw IllegalStateException("Ressources Cloudstream illisibles")
        plugin.resources = Resources(
            assets,
            context.resources.displayMetrics,
            context.resources.configuration,
        )
    }

    private fun remember(
        extension: InstalledExtension,
        plugin: Any,
        classLoader: PathClassLoader,
        manifest: PluginManifest,
    ): Loaded {
        val entry = Loaded(extension.internalName, plugin, classLoader, manifest)
        synchronized(loaded) { loaded[extension.internalName] = entry }
        Log.i(TAG, "Extension chargée : ${extension.internalName} v${manifest.version ?: "?"}")
        return entry
    }

    private fun classFailure(className: String, error: Throwable): ExtensionException {
        val classification = classifyClassFailure(className, error)
        return when (classification.type) {
            ExtensionErrorType.BAD_MANIFEST -> ExtensionException(
                "Classe du manifeste introuvable : $className.",
                error,
                classification.type,
                Log.getStackTraceString(error),
            )
            ExtensionErrorType.MISSING_DEPENDENCY -> ExtensionException(
                "Dépendance manquante : ${classification.missingClass}.",
                error,
                classification.type,
                Log.getStackTraceString(error),
            )
            else -> ExtensionException(
                "Liaison impossible de $className : ${rootMessage(error)}.",
                error,
                ExtensionErrorType.LOAD_FAILED,
                Log.getStackTraceString(error),
            )
        }
    }

    private fun dependencyFailure(error: Throwable): ExtensionException? {
        val missing = causes(error).firstOrNull { it is NoClassDefFoundError } ?: return null
        val dependency = extractMissingClassName(missing) ?: return null
        return ExtensionException(
            "Dépendance manquante : $dependency.",
            error,
            ExtensionErrorType.MISSING_DEPENDENCY,
            Log.getStackTraceString(error),
        )
    }

    private fun rootMessage(error: Throwable): String {
        val root = causes(error).lastOrNull() ?: error
        return root.message?.takeIf(String::isNotBlank) ?: root::class.java.simpleName
    }

    private fun causes(error: Throwable): List<Throwable> =
        generateSequence(error) { it.cause }.take(12).toList()

    /** Décharge une extension et retire tous les providers qu'elle a publiés. */
    fun unload(internalName: String) {
        val entry = synchronized(loaded) { loaded.remove(internalName) } ?: return
        when (val plugin = entry.plugin) {
            is MissaPlugin -> {
                runCatching { plugin.beforeUnload() }
                    .onFailure { Log.w(TAG, "beforeUnload a échoué pour $internalName", it) }
                plugin.filename?.let { PluginRegistry.clear(it) }
            }
            is BasePlugin -> {
                runCatching { plugin.beforeUnload() }
                    .onFailure { Log.w(TAG, "beforeUnload Cloudstream a échoué pour $internalName", it) }
                plugin.filename?.let { CloudstreamCompatRegistry.unload(it) }
            }
        }
        Log.i(TAG, "Extension déchargée : $internalName")
    }

    fun unloadAll() {
        loadedNames().forEach(::unload)
    }
}
