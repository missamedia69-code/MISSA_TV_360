package com.missa.tv360.extensions

import android.util.Log
import com.missa.tv360.api.Item
import com.missa.tv360.api.Provider
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video
import com.missa.tv360.data.VideoRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Traduction des contenus publiés par les extensions vers le modèle de
 * l'application ([VideoRow] / [Video]).
 *
 * Règles :
 *  - une extension qui lève une exception est isolée : elle ne produit
 *    simplement aucune rangée, les autres continuent ;
 *  - un item sans URL de flux est ignoré (jamais de carte menant à un lecteur
 *    vide — défaut B-06 de l'audit) ;
 *  - l'identifiant est préfixé par le provider pour rester unique entre
 *    extensions et avec le catalogue du dépôt ;
 *  - les identifiants de rangée sont préfixés `ext:` pour ne pas entrer en
 *    collision avec l'ordre canonique du catalogue.
 */
object ExtensionCatalog {

    private const val TAG = "ExtensionCatalog"
    private const val PROVIDER_TIMEOUT_MS = 8_000L

    /** Rangées issues de toutes les extensions chargées. */
    suspend fun rows(): List<VideoRow> = withContext(Dispatchers.IO) {
        val providers = ExtensionManager.providers()
        if (providers.isEmpty()) return@withContext emptyList()

        val result = mutableListOf<VideoRow>()
        providers.forEach { provider ->
            val rows = try {
                withTimeout(PROVIDER_TIMEOUT_MS) { provider.rows() }
            } catch (t: Throwable) {
                Log.w(TAG, "Provider ${provider.id} en échec", t)
                emptyList()
            }
            rows.forEach { row ->
                val videos = row.items.mapNotNull { it.toVideo(provider) }
                if (videos.isEmpty()) return@forEach
                val header = if (row.title.isBlank() || row.title.equals(provider.name, true)) {
                    provider.name
                } else {
                    "${provider.name} — ${row.title}"
                }
                result += VideoRow(
                    videos = videos,
                    headerTitle = header,
                    rowId = "ext:${provider.id}:${row.id}",
                )
            }
        }
        // Une extension peut publier deux fois la même rangée : on garde la 1re.
        result.distinctBy { it.rowId }
    }

    private fun Item.toVideo(provider: Provider): Video? {
        val url = stream.url.trim()
        if (url.isEmpty()) return null
        if (id.isBlank() || title.isBlank()) return null
        return Video(
            id = "ext:${provider.id}:$id",
            title = title,
            description = description,
            cardRes = 0,
            streamUrl = url,
            kind = kind.toContentKind(),
            seriesTitle = seriesTitle,
            season = season,
            episode = episode,
            durationSec = durationSec,
            format = stream.format.ifBlank { if (stream.audioOnly) "mp3" else "mp4" },
            audioOnly = stream.audioOnly,
            forceLive = stream.live,
            originalKind = kind.name.lowercase(),
            cardUrl = thumbnailUrl,
            source = source ?: provider.name,
            license = license,
        )
    }

    private fun com.missa.tv360.api.Kind.toContentKind(): ContentKind =
        ContentKind.entries.firstOrNull { it.name == name } ?: ContentKind.MOVIE
}
