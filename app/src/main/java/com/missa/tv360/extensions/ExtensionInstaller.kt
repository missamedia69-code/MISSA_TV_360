package com.missa.tv360.extensions

import android.content.Context
import com.missa.tv360.api.PLUGIN_API_VERSION
import com.missa.tv360.api.PLUGIN_FILE_EXTENSION
import java.io.File

/**
 * Téléchargement et installation atomique d'une extension.
 *
 * Séquence (aucune étape ne peut être sautée) :
 *  1. URL validée (HTTPS) et métadonnées cohérentes (`apiVersion`) ;
 *  2. `fileHash` OBLIGATOIRE ;
 *  3. téléchargement en mémoire, taille bornée ;
 *  4. vérification SHA-256 (+ `fileSize` déclaré, s'il existe) ;
 *  5. écriture dans un fichier temporaire du dossier cible, puis `renameTo`
 *     — le remplacement est atomique sur le même système de fichiers, donc une
 *     coupure ne laisse jamais un plugin à moitié écrit ;
 *  6. fichier passé en lecture seule (durcissement).
 */
internal class ExtensionInstaller(private val context: Context) {

    /** Dossier racine : `filesDir/extensions/<hash dépôt>/`. */
    fun repositoryDir(repositoryUrl: String): File =
        File(File(context.filesDir, ROOT_FOLDER), folderName(repositoryUrl))

    /** Chemin final du binaire installé. */
    fun targetFile(repositoryUrl: String, internalName: String): File =
        File(repositoryDir(repositoryUrl), "${safeName(internalName)}.$PLUGIN_FILE_EXTENSION")

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
                "Extension « $internalName » compilée pour l'API ${entry.apiVersionInt}, " +
                    "l'application fournit l'API $PLUGIN_API_VERSION."
            )
        }
        val hash = entry.hashOrNull
            ?: throw ExtensionException(
                "Extension « $internalName » publiée sans empreinte SHA-256 : installation refusée."
            )

        ExtensionHttp.checkUrl(url)
        val bytes = ExtensionHttp.getBytes(url, MAX_PLUGIN_BYTES)
        onProgress?.invoke(bytes.size.toLong(), entry.fileSize ?: bytes.size.toLong())

        if (bytes.isEmpty()) throw ExtensionException("Artefact vide : $url")

        val actual = ExtensionHash.sha256(bytes)
        if (!ExtensionHash.matches(hash, actual)) {
            throw ExtensionException(
                "Empreinte invalide pour « $internalName » (attendu ${ExtensionHash.normalize(hash)}, " +
                    "obtenu $actual)."
            )
        }
        if (entry.fileSize != null && entry.fileSize > 0 && entry.fileSize != bytes.size.toLong()) {
            throw ExtensionException(
                "Taille inattendue pour « $internalName » (${bytes.size} o au lieu de ${entry.fileSize} o)."
            )
        }

        val dir = repositoryDir(repositoryUrl)
        if (!dir.exists() && !dir.mkdirs()) {
            throw ExtensionException("Impossible de créer ${dir.absolutePath}")
        }

        val target = targetFile(repositoryUrl, internalName)
        // Fichier temporaire dans le MÊME dossier : garantit un rename atomique.
        val temp = File.createTempFile("install-", ".part", dir)
        try {
            temp.writeBytes(bytes)
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

        /** 8 Mio : largement suffisant pour une extension de catalogue. */
        const val MAX_PLUGIN_BYTES = 8 * 1024 * 1024
    }
}
