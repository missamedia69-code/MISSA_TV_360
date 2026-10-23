package com.missa.tv360.extensions

import com.google.gson.Gson
import com.missa.tv360.api.PLUGIN_API_VERSION
import com.missa.tv360.api.PLUGIN_MANIFEST_ENTRY
import com.missa.tv360.api.PluginManifest
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Contrôles structurels communs aux paquets `.msa` et `.cs3`. */
internal object ExtensionPackageValidator {

    private const val MAX_MANIFEST_BYTES = 64 * 1024L
    private const val MAX_DEX_BYTES = 64 * 1024 * 1024L
    private val classNamePattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    private val gson = Gson()

    private val cloudstreamMarkers = linkedMapOf(
        "Lcom/lagradost/cloudstream3/plugins/Plugin;" to "com.lagradost.cloudstream3.plugins.Plugin",
        "Lcom/lagradost/cloudstream3/plugins/BasePlugin;" to "com.lagradost.cloudstream3.plugins.BasePlugin",
        "Lcom/lagradost/cloudstream3/MainAPI;" to "com.lagradost.cloudstream3.MainAPI",
        "Lcom/lagradost/cloudstream3/" to "com.lagradost.cloudstream3",
    )

    /** Valide la structure sans refuser un `.cs3` avant son diagnostic. */
    fun validate(file: File): PluginManifest {
        try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry(PLUGIN_MANIFEST_ENTRY)
                    ?: throw badManifest("manifest.json absent de ${file.name}")
                val dexEntry = zip.getEntry("classes.dex")
                    ?: throw badManifest("classes.dex absent de ${file.name}")

                if (manifestEntry.isDirectory || manifestEntry.size > MAX_MANIFEST_BYTES) {
                    throw badManifest("manifest.json invalide ou trop volumineux : ${file.name}")
                }
                if (dexEntry.isDirectory || dexEntry.size <= 0L || dexEntry.size > MAX_DEX_BYTES) {
                    throw badManifest("classes.dex invalide ou trop volumineux : ${file.name}")
                }
                zip.getInputStream(dexEntry).use { input ->
                    val signature = ByteArray(4)
                    readFully(input, signature)
                    val expected = byteArrayOf(
                        'd'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(),
                    )
                    if (!signature.contentEquals(expected)) {
                        throw badManifest("classes.dex n'est pas un DEX valide : ${file.name}")
                    }
                }

                val manifestText = zip.getInputStream(manifestEntry).use { input ->
                    input.readLimited(MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                }
                val manifest = try {
                    gson.fromJson(manifestText, PluginManifest::class.java)
                } catch (t: Throwable) {
                    throw badManifest("manifest.json illisible : ${file.name}", t)
                } ?: throw badManifest("manifest.json vide : ${file.name}")

                val className = manifest.pluginClassName?.trim().orEmpty()
                if (!classNamePattern.matches(className)) {
                    throw badManifest("pluginClassName absent ou invalide : ${file.name}")
                }
                return manifest
            }
        } catch (e: ExtensionException) {
            throw e
        } catch (t: Throwable) {
            throw badManifest("Archive d'extension invalide : ${file.name}", t)
        }
    }

    /**
     * Préflight du chargeur natif : aucune classe n'est chargée avant d'avoir
     * identifié une dépendance Cloudstream dans les chaînes du DEX.
     */
    fun validateNative(file: File): PluginManifest {
        val manifest = validate(file)
        val cloudstreamDependency = cloudstreamDependency(file)
        if (cloudstreamDependency != null) {
            throw ExtensionException(
                "Dépendance Cloudstream manquante : $cloudstreamDependency.",
                type = ExtensionErrorType.MISSING_DEPENDENCY,
                technicalDetails = "La classe ${manifest.pluginClassName} est présente dans ${file.name}, " +
                    "mais son DEX référence $cloudstreamDependency. Cette API n'est pas encore " +
                    "fournie par le moteur natif MISSA ; aucun autre nom de classe n'a été essayé.",
            )
        }
        if (manifest.apiVersion != PLUGIN_API_VERSION) {
            throw ExtensionException(
                "API d'extension ${manifest.apiVersion} incompatible avec l'API MISSA $PLUGIN_API_VERSION.",
                type = ExtensionErrorType.INCOMPATIBLE,
                technicalDetails = "Paquet ${file.name}, classe ${manifest.pluginClassName}.",
            )
        }
        return manifest
    }

    /** Retourne la dépendance Cloudstream la plus précise trouvée, ou null. */
    fun cloudstreamDependency(file: File): String? {
        try {
            ZipFile(file).use { zip ->
                val dex = zip.getEntry("classes.dex")
                    ?: throw badManifest("classes.dex absent de ${file.name}")
                val found = zip.getInputStream(dex).use { input ->
                    scanMarkers(
                        input,
                        cloudstreamMarkers.keys.map { it.toByteArray(Charsets.ISO_8859_1) },
                    )
                }
                return cloudstreamMarkers.entries.firstOrNull { found[it.key] == true }?.value
            }
        } catch (e: ExtensionException) {
            throw e
        } catch (t: Throwable) {
            throw badManifest("DEX illisible : ${file.name}", t)
        }
    }

    /** Scan KMP en flux : pas de chargement du DEX complet en mémoire. */
    private fun scanMarkers(input: InputStream, markers: List<ByteArray>): Map<String, Boolean> {
        val found = BooleanArray(markers.size)
        val matched = IntArray(markers.size)
        val prefixes = markers.map(::prefixTable)
        val texts = markers.map { String(it, Charsets.ISO_8859_1) }
        val buffer = ByteArray(16 * 1024)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            for (index in 0 until count) {
                val value = buffer[index]
                for (markerIndex in markers.indices) {
                    val marker = markers[markerIndex]
                    var current = matched[markerIndex]
                    while (current > 0 && value != marker[current]) {
                        current = prefixes[markerIndex][current - 1]
                    }
                    if (value == marker[current]) current++
                    if (current == marker.size) {
                        found[markerIndex] = true
                        current = prefixes[markerIndex][current - 1]
                    }
                    matched[markerIndex] = current
                }
            }
        }
        return texts.mapIndexed { index, text -> text to found[index] }.toMap()
    }

    private fun prefixTable(pattern: ByteArray): IntArray {
        val result = IntArray(pattern.size)
        var length = 0
        var index = 1
        while (index < pattern.size) {
            if (pattern[index] == pattern[length]) {
                result[index++] = ++length
            } else if (length > 0) {
                length = result[length - 1]
            } else {
                result[index++] = 0
            }
        }
        return result
    }

    private fun badManifest(message: String, cause: Throwable? = null) = ExtensionException(
        message,
        cause,
        ExtensionErrorType.BAD_MANIFEST,
    )

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw badManifest("Entrée ZIP tronquée")
            if (read == 0) continue
            offset += read
        }
    }

    private fun InputStream.readLimited(maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) throw badManifest("manifest.json dépasse $maxBytes octets")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
