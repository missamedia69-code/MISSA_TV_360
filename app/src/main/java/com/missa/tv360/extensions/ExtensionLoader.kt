package com.missa.tv360.extensions

import android.content.Context
import android.util.Log
import com.missa.tv360.api.MissaPlugin
import com.missa.tv360.api.PluginManifest
import com.missa.tv360.api.PluginRegistry
import dalvik.system.PathClassLoader
import java.io.File

/** Chargeur des extensions natives MISSA, avec diagnostic préflight des `.cs3`. */
internal object ExtensionLoader {

    private const val TAG = "ExtensionLoader"
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
        if (!file.isFile) {
            throw ExtensionException("Fichier d'extension absent : ${extension.filePath}")
        }
        file.setReadOnly()

        // Dépendances et manifeste sont contrôlés AVANT PathClassLoader. Le nom
        // exact du manifeste est le seul nom de classe qui sera ensuite chargé.
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

        val entry = Loaded(extension.internalName, plugin, classLoader, manifest)
        synchronized(loaded) { loaded[extension.internalName] = entry }
        Log.i(TAG, "Extension chargée : ${extension.internalName} v${manifest.version ?: "?"}")
        return entry
    }

    private fun classFailure(className: String, error: Throwable): ExtensionException {
        dependencyFailure(error)?.let { return it }
        val notFound = causes(error).any { it is ClassNotFoundException }
        return if (notFound) {
            ExtensionException(
                "Classe du manifeste introuvable : $className.",
                error,
                ExtensionErrorType.BAD_MANIFEST,
                Log.getStackTraceString(error),
            )
        } else {
            ExtensionException(
                "Liaison impossible de $className : ${rootMessage(error)}.",
                error,
                ExtensionErrorType.LOAD_FAILED,
                Log.getStackTraceString(error),
            )
        }
    }

    private fun dependencyFailure(error: Throwable): ExtensionException? {
        val missing = causes(error).firstOrNull { it is NoClassDefFoundError } ?: return null
        val dependency = missingClassName(missing)
        return ExtensionException(
            "Dépendance manquante : $dependency.",
            error,
            ExtensionErrorType.MISSING_DEPENDENCY,
            Log.getStackTraceString(error),
        )
    }

    private fun missingClassName(error: Throwable): String {
        val message = error.message.orEmpty()
        val descriptor = Regex("L[A-Za-z0-9_$/.-]+;").find(message)?.value
        val token = descriptor ?: message.split(Regex("\\s+"))
            .firstOrNull { it.contains('/') || it.startsWith("L") }
            ?.removePrefix(":")
        return token
            ?.removePrefix("L")
            ?.removeSuffix(";")
            ?.replace('/', '.')
            ?.takeIf(String::isNotBlank)
            ?: error::class.java.simpleName
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
        runCatching { entry.plugin.beforeUnload() }
            .onFailure { Log.w(TAG, "beforeUnload a échoué pour $internalName", it) }
        entry.plugin.filename?.let { PluginRegistry.clear(it) }
        Log.i(TAG, "Extension déchargée : $internalName")
    }

    fun unloadAll() {
        loadedNames().forEach(::unload)
    }
}
