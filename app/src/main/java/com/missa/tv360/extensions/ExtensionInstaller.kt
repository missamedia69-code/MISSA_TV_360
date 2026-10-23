package com.missa.tv360.extensions

import android.content.Context
import com.missa.tv360.api.PLUGIN_API_VERSION
import com.missa.tv360.api.PLUGIN_FILE_EXTENSION
import java.io.File

/**
 * Téléchargement et installation atomique d'une extension MISSA ou Cloudstream.
 *
 * Comme Cloudstream, `fileHash` est facultatif dans un dépôt mais est toujours
 * vérifié lorsqu'il est fourni. `fileSize` sert de métadonnée de dépôt, pas de
 * preuve d'intégrité : plusieurs dépôts Cloudstream annoncent la taille d'une
 * autre révision. L'URL HTTPS, la taille maximale et la structure réelle du
 * paquet restent contrôlées avant tout remplacement du fichier installé.
 */
internal class ExtensionInstaller(private val context: Context) {

    /** Dossier racine : `filesDir/extensions/<hash dépôt>/`. */
    fun repositoryDir(repositoryUrl: String): File =
        File(File(context.filesDir, ROOT_FOLDER), folderName(repositoryUrl))

    /** Chemin final du binaire installé. */
    fun targetFile(
        repositoryUrl: String,
        internalName: String,
        extension: String = PLUGIN_FILE_EXTENSION,
    ): File = File(repositoryDir(repositoryUrl), "${safeName(internalName)}.$extension")

    fun install(
        entry: PluginEntry,
        repositoryUrl: String,
        onProgress: ((downloaded: Long, total: Long?) -> Unit)? = null,
    ): InstalledExtension {
        val url = entry.urlOrNull
            ?: throw ExtensionException("Extension « ${entry.normalizedInternalName} » sans URL d'artefact.")
        val internalName = entry.normalizedInternalName
        if (internalName.isEmpty()) {
            throw ExtensionException("Extension sans identifiant interne (internalName).")
        }
        if (!entry.isCompatible) {
            throw ExtensionException(
                "Extension « $internalName » compilée pour une API ${entry.apiVersionInt} " +
                    "non prise en charge (API MISSA $PLUGIN_API_VERSION / manifeste Cloudstream absent)."
            )
        }

        ExtensionHttp.checkUrl(url)
        val bytes = ExtensionHttp.getBytes(url, MAX_PLUGIN_BYTES)
        onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())

        if (bytes.isEmpty()) throw ExtensionException("Artefact vide : $url")

        entry.hashOrNull?.let { expected ->
            val actual = ExtensionHash.sha256(bytes)
            if (!ExtensionHash.matches(expected, actual)) {
                throw ExtensionException(
                    "Empreinte invalide pour « $internalName » " +
                        "(attendu ${ExtensionHash.normalize(expected)}, obtenu $actual)."
                )
            }
        }

        val dir = repositoryDir(repositoryUrl)
        if (!dir.exists() && !dir.mkdirs()) {
            throw ExtensionException("Impossible de créer ${dir.absolutePath}")
        }

        val artifactExtension = if (url.substringBefore('?').endsWith(".cs3", ignoreCase = true)) {
            CLOUDSTREAM_FILE_EXTENSION
        } else {
            PLUGIN_FILE_EXTENSION
        }
        val target = targetFile(repositoryUrl, internalName, artifactExtension)
        // Fichier temporaire dans le MÊME dossier : rename atomique et aucun
        // ancien binaire n'est supprimé avant la validation complète du nouveau.
        val temp = File.createTempFile("install-", ".part", dir)
        try {
            temp.writeBytes(bytes)
            ExtensionPackageValidator.validate(temp)
            if (target.exists() && !target.delete()) {
                throw ExtensionException("Ancienne version non remplaçable : ${target.absolutePath}")
            }
            if (!temp.renameTo(target)) {
                throw ExtensionException("Déplacement atomique impossible vers ${target.absolutePath}")
            }
            target.setReadOnly()
        } finally {
            if (temp.exists()) temp.delete()
        }

        return InstalledExtension(
            internalName = internalName,
            repositoryUrl = repositoryUrl,
            name = entry.normalizedName,
            version = entry.versionInt,
            filePath = target.absolutePath,
            apiVersion = entry.apiVersionInt,
            status = entry.statusInt,
            enabled = !entry.isDisabledByAuthor,
        )
    }

    /** Nom de dossier stable et sûr, dérivé de l'URL du dépôt. */
    private fun folderName(repositoryUrl: String): String {
        val digest = ExtensionHash.sha256(repositoryUrl.toByteArray(Charsets.UTF_8))
        return digest.take(16)
    }

    /** Réduit un identifiant à un nom de fichier sûr (aucun séparateur de chemin). */
    private fun safeName(raw: String): String {
        val cleaned = raw.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
        }.joinToString("")
        return cleaned.take(64).ifBlank { "extension" }
    }

    private companion object {
        const val ROOT_FOLDER = "extensions"
        const val CLOUDSTREAM_FILE_EXTENSION = "cs3"

        /** 8 Mio : borne anti-saturation, indépendante de la métadonnée fileSize. */
        const val MAX_PLUGIN_BYTES = 8 * 1024 * 1024
    }
}
