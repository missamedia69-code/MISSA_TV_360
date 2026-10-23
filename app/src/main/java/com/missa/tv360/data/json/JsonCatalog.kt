package com.missa.tv360.data.json

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Game
import com.missa.tv360.data.GamesRow
import com.missa.tv360.data.Video
import com.missa.tv360.data.VideoRow
import java.net.URL

/** Entrée normalisée d'un fichier déclaré dans sources/index.json. */
data class JsonSource(
    val id: String,
    val name: String,
    val path: String,
)

data class MergedJsonCatalog(
    val rows: List<VideoRow> = emptyList(),
    val games: List<GamesRow> = emptyList(),
)

private data class JsonSourceIndexDto(
    @SerializedName("schema_version") val schemaVersion: Int? = null,
    val sources: List<JsonSourceEntryDto>? = null,
)

private data class JsonSourceEntryDto(
    val id: String? = null,
    val name: String? = null,
    val path: String? = null,
    val enabled: Boolean? = true,
)

data class JsonCatalogDocument(
    @SerializedName("schema_version") val schemaVersion: Int? = null,
    val rows: List<JsonRowDto>? = null,
)

data class JsonRowDto(
    val id: String? = null,
    val title: String? = null,
    val items: List<JsonItemDto>? = null,
)

data class JsonStreamDto(
    val url: String? = null,
    val format: String? = null,
    val live: Boolean = false,
)

data class JsonItemDto(
    val id: String? = null,
    val title: String? = null,
    val kind: String? = null,
    val description: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    val stream: JsonStreamDto? = null,
    @SerializedName("series_title") val seriesTitle: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("duration_sec") val durationSec: Int? = null,
    @SerializedName("audio_only") val audioOnly: Boolean = false,
    val tags: List<String>? = null,
    val creator: String? = null,
    @SerializedName("release_year") val releaseYear: String? = null,
    val source: String? = null,
    val license: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("size_bytes") val sizeBytes: Long? = null,
    val sha256: String? = null,
    @SerializedName("package_type") val packageType: String? = null,
    @SerializedName("version_name") val versionName: String? = null,
    @SerializedName("version_code") val versionCode: Int? = null,
    val genre: String? = null,
)

/** Lecture et fusion sans réseau : toutes les décisions de validation sont testables. */
object JsonCatalog {
    private const val MAX_SOURCES = 64
    private const val MAX_ROWS_PER_SOURCE = 100
    private const val MAX_ITEMS_PER_ROW = 500
    private val idPattern = Regex("[a-z0-9][a-z0-9_-]{0,47}")
    private val pathPattern = Regex("[A-Za-z0-9._/-]+\\.json")
    private val checksumPattern = Regex("[a-fA-F0-9]{64}")
    private val canonicalOrder = listOf(
        "tv_direct", "sport", "films", "series", "films_courts",
        "radio", "podcasts", "musique", "jeunesse", "jeux",
    )
    private val gson = Gson()

    fun parseIndex(text: String): List<JsonSource> {
        val index = gson.fromJson(text, JsonSourceIndexDto::class.java)
            ?: throw IllegalArgumentException("Index JSON vide")
        require(index.schemaVersion == 1) { "Version d'index non supportée" }
        val declared = index.sources.orEmpty()
        require(declared.size <= MAX_SOURCES) { "Trop de sources JSON" }
        val enabled = declared.filter { it.enabled != false }
        val normalized = enabled.map { entry ->
            val id = entry.id?.trim().orEmpty()
            val path = entry.path?.trim().orEmpty()
            require(idPattern.matches(id)) { "Identifiant de source invalide" }
            require(isSafePath(path)) { "Chemin JSON invalide pour $id" }
            JsonSource(
                id = id,
                name = entry.name?.clean(160)?.ifBlank { null } ?: id,
                path = path,
            )
        }
        require(normalized.map { it.id }.distinct().size == normalized.size) {
            "Identifiant de source dupliqué"
        }
        require(normalized.map { it.path }.distinct().size == normalized.size) {
            "Chemin JSON dupliqué"
        }
        return normalized
    }

    fun parseDocument(text: String): JsonCatalogDocument {
        val document = gson.fromJson(text, JsonCatalogDocument::class.java)
            ?: throw IllegalArgumentException("Catalogue JSON vide")
        require(document.schemaVersion == 1) { "Version de catalogue non supportée" }
        require(document.rows.orEmpty().size <= MAX_ROWS_PER_SOURCE) { "Trop de rangées dans une source" }
        return document
    }

    fun merge(documents: List<Pair<JsonSource, JsonCatalogDocument>>): MergedJsonCatalog {
        val mergedRows = linkedMapOf<String, RowAccumulator>()
        documents.forEach { (source, document) ->
            document.rows.orEmpty().forEach { row ->
                val rowId = row.id?.trim().orEmpty()
                val title = row.title?.clean(160).orEmpty()
                if (!idPattern.matches(rowId) || title.isBlank()) return@forEach
                val gamesRow = rowId == "jeux" || rowId == "games"
                val accumulator = mergedRows.getOrPut(rowId) { RowAccumulator(rowId, title, gamesRow) }
                row.items.orEmpty().take(MAX_ITEMS_PER_ROW).forEach { item ->
                    if (gamesRow) {
                        item.toGame(source)?.let { game -> accumulator.gameItems.putIfAbsent(game.id, game) }
                    } else {
                        item.toVideo(source)?.let { video -> accumulator.videoItems.putIfAbsent(video.id, video) }
                    }
                }
            }
        }

        val ordered = mergedRows.values.sortedWith(
            compareBy<RowAccumulator> { row ->
                canonicalOrder.indexOf(row.id).let { if (it < 0) canonicalOrder.size else it }
            }.thenBy { it.title.lowercase() },
        )
        val rows = ordered.mapNotNull { row ->
            if (row.isGamesRow) {
                if (row.gameItems.isEmpty()) return@mapNotNull null
                val launcher = Video(
                    id = "games-open-${row.id}",
                    title = row.title,
                    description = "",
                    cardRes = 0,
                    streamUrl = "",
                    kind = ContentKind.GAME,
                    isGamesLauncher = true,
                )
                VideoRow(videos = listOf(launcher), headerTitle = row.title, rowId = row.id)
            } else {
                if (row.videoItems.isEmpty()) return@mapNotNull null
                VideoRow(videos = row.videoItems.values.toList(), headerTitle = row.title, rowId = row.id)
            }
        }
        val games = ordered.mapNotNull { row ->
            if (!row.isGamesRow || row.gameItems.isEmpty()) return@mapNotNull null
            GamesRow(row.title, row.gameItems.values.toList())
        }
        return MergedJsonCatalog(rows, games)
    }

    private fun JsonItemDto.toVideo(source: JsonSource): Video? {
        val rawId = id?.clean(128).orEmpty()
        val itemTitle = title?.clean(240).orEmpty()
        val mediaUrl = stream?.url?.httpsUrlOrNull() ?: return null
        if (rawId.isBlank() || itemTitle.isBlank()) return null
        val contentKind = ContentKind.fromApi(kind)
        val normalizedFormat = (stream?.format).normalizedFormat(mediaUrl, audioOnly) ?: return null
        return Video(
            id = "${source.id}:$rawId",
            title = itemTitle,
            description = description?.clean(4_000).orEmpty(),
            cardRes = 0,
            streamUrl = mediaUrl,
            kind = contentKind,
            seriesTitle = seriesTitle?.clean(240)?.ifBlank { null },
            season = seasonNumber?.takeIf { it > 0 },
            episode = episodeNumber?.takeIf { it > 0 },
            durationSec = durationSec?.takeIf { it > 0 },
            format = normalizedFormat,
            cardUrl = thumbnailUrl?.httpsUrlOrNull(),
            tags = tags.orEmpty().mapNotNull { it.clean(80).ifBlank { null } }.take(20),
            creator = creator?.clean(240)?.ifBlank { null },
            releaseYear = releaseYear?.clean(20)?.ifBlank { null },
            source = this.source?.clean(160)?.ifBlank { null } ?: source.name,
            license = license?.clean(160)?.ifBlank { null },
            forceLive = stream?.live == true,
            audioOnly = audioOnly,
            originalKind = kind?.clean(40),
            shapeHint = ContentKind.shapeFromApi(kind),
        )
    }

    private fun JsonItemDto.toGame(source: JsonSource): Game? {
        val rawId = id?.clean(128).orEmpty()
        val itemTitle = title?.clean(240).orEmpty()
        val url = downloadUrl?.httpsUrlOrNull() ?: return null
        val checksum = sha256?.trim().orEmpty()
        if (rawId.isBlank() || itemTitle.isBlank() || !checksumPattern.matches(checksum)) return null
        return Game(
            id = "${source.id}:$rawId",
            title = itemTitle,
            description = description?.clean(4_000).orEmpty(),
            thumbnailUrl = thumbnailUrl?.httpsUrlOrNull(),
            downloadUrl = url,
            sizeBytes = sizeBytes?.takeIf { it >= 0L } ?: 0L,
            sha256 = checksum,
            packageType = packageType?.clean(20)?.ifBlank { null } ?: "apk",
            versionName = versionName?.clean(80)?.ifBlank { null },
            versionCode = versionCode?.takeIf { it >= 0 },
            genre = genre?.clean(120)?.ifBlank { null },
        )
    }

    private fun isSafePath(path: String): Boolean =
        pathPattern.matches(path) && !path.startsWith("/") && !path.contains("..") && !path.contains("//")

    private fun String?.normalizedFormat(url: String, audioOnly: Boolean): String? {
        val explicit = this?.trim()?.lowercase().orEmpty()
        if (explicit.isNotBlank()) return explicit.takeIf { it in setOf("mp4", "hls", "mp3") }
        val path = runCatching { URL(url).path.lowercase() }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "hls"
            audioOnly || path.endsWith(".mp3") -> "mp3"
            else -> "mp4"
        }
    }

    private fun String.httpsUrlOrNull(): String? {
        val value = trim()
        if (value.length > 4_096 || value.any(Char::isISOControl)) return null
        return try {
            val parsed = URL(value)
            if (parsed.protocol == "https" && parsed.host.isNotBlank()) value else null
        } catch (_: Exception) {
            null
        }
    }

    private fun String.clean(maxLength: Int): String = trim().filterNot(Char::isISOControl).take(maxLength)

    private data class RowAccumulator(
        val id: String,
        val title: String,
        val isGamesRow: Boolean,
        val videoItems: LinkedHashMap<String, Video> = linkedMapOf(),
        val gameItems: LinkedHashMap<String, Game> = linkedMapOf(),
    )
}
