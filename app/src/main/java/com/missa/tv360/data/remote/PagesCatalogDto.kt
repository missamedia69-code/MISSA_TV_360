package com.missa.tv360.data.remote

import com.google.gson.annotations.SerializedName
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Game
import com.missa.tv360.data.Video

/**
 * DTO du fichier catalog.json servi par GitHub Pages depuis le dépôt.
 *
 * Forme produite par engines/_core/schema.py (consolidation de tous les
 * moteurs) :
 *
 * {
 *   "generated_at": "...",
 *   "rows": [ { "id": "tv_direct", "title": "TV Direct", "items": [...] } ]
 * }
 */
data class PagesCatalogDto(
    @SerializedName("generated_at") val generatedAt: String? = null,
    @SerializedName("rows") val rows: List<PagesRowDto> = emptyList(),
)

data class PagesRowDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("items") val items: List<PagesItemDto> = emptyList(),
)

data class PagesStreamDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("format") val format: String? = null,
    @SerializedName("live") val live: Boolean = false,
)

data class PagesItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("stream") val stream: PagesStreamDto? = null,
    @SerializedName("series_title") val seriesTitle: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("duration_sec") val durationSec: Int? = null,
    @SerializedName("audio_only") val audioOnly: Boolean = false,
    // Champs des jeux téléchargeables (kind = "game")
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("package_type") val packageType: String? = null,
    @SerializedName("version_name") val versionName: String? = null,
    @SerializedName("version_code") val versionCode: Int? = null,
    @SerializedName("genre") val genre: String? = null,
) {
    /** Convertit un item distant en Video, ou null si illisible (pas d'id/titre). */
    fun toVideo(): Video? {
        val vid = id?.takeIf { it.isNotBlank() } ?: return null
        val vTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val contentKind = ContentKind.fromApi(kind)
        return Video(
            id = vid,
            title = vTitle,
            description = description.orEmpty(),
            cardRes = 0, // la vignette distante (thumbnailUrl) prend le relais
            streamUrl = stream?.url.orEmpty(),
            kind = contentKind,
            seriesTitle = seriesTitle,
            season = seasonNumber,
            episode = episodeNumber,
            durationSec = durationSec,
            format = stream?.format ?: if (audioOnly) "mp3" else "mp4",
            cardUrl = thumbnailUrl,
        )
    }

    /** Convertit un item de type "game" en Game téléchargeable. */
    fun toGame(): Game? {
        val gid = id?.takeIf { it.isNotBlank() } ?: return null
        val gTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return Game(
            id = gid,
            title = gTitle,
            description = description.orEmpty(),
            thumbnailUrl = thumbnailUrl,
            downloadUrl = downloadUrl.orEmpty(),
            sizeBytes = sizeBytes ?: 0L,
            sha256 = sha256,
            packageType = packageType ?: "apk",
            versionName = versionName,
            versionCode = versionCode,
            genre = genre,
        )
    }
}
