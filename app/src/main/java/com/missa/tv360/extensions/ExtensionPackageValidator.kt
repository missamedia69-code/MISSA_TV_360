package com.missa.tv360.extensions

import com.google.gson.Gson
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

    /**
     * Ne désassemble et n'extrait rien : seules les deux entrées nécessaires au
     * chargeur Android sont lues. Le fichier reste dans le dossier privé de l'app.
     */
    fun validate(file: File): PluginManifest {
        try {
            ZipFile(file).use { zip ->
                val manifestEntry = zip.getEntry(PLUGIN_MANIFEST_ENTRY)
                    ?: throw ExtensionException("manifest.json absent de ${file.name}")
                val dexEntry = zip.getEntry("classes.dex")
                    ?: throw ExtensionException("classes.dex absent de ${file.name}")

                if (manifestEntry.isDirectory || manifestEntry.size > MAX_MANIFEST_BYTES) {
                    throw ExtensionException("manifest.json invalide ou trop volumineux : ${file.name}")
                }
                if (dexEntry.isDirectory || dexEntry.size <= 0L || dexEntry.size > MAX_DEX_BYTES) {
                    throw ExtensionException("classes.dex invalide ou trop volumineux : ${file.name}")
                }
                zip.getInputStream(dexEntry).use { input ->
                    val signature = ByteArray(4)
                    readFully(input, signature)
                    if (!signature.contentEquals(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte()))) {
                        throw ExtensionException("classes.dex n'est pas un DEX valide : ${file.name}")
                    }
                }

                val manifestText = zip.getInputStream(manifestEntry).use { input ->
                    input.readLimited(MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                }
                val manifest = try {
                    gson.fromJson(manifestText, PluginManifest::class.java)
                } catch (t: Throwable) {
                    throw ExtensionException("manifest.json illisible : ${file.name}", t)
                } ?: throw ExtensionException("manifest.json vide : ${file.name}")

                val className = manifest.pluginClassName?.trim().orEmpty()
                if (!classNamePattern.matches(className)) {
                    throw ExtensionException("pluginClassName absent ou invalide : ${file.name}")
                }
                return manifest
            }
        } catch (e: ExtensionException) {
            throw e
        } catch (t: Throwable) {
            throw ExtensionException("Archive d'extension invalide : ${file.name}", t)
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw ExtensionException("Entrée ZIP tronquée")
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
            if (total > maxBytes) throw ExtensionException("manifest.json dépasse $maxBytes octets")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
