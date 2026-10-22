package com.missa.tv360.data.source

import android.net.Uri
import com.missa.tv360.R
import com.missa.tv360.data.ContentKind
import com.missa.tv360.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Adaptateur natif de la première source brute du moteur Films : Internet Archive. */
object InternetArchiveFilmSource : FilmSource {
    override val adapterId: String = "internet_archive"

    private const val SEARCH_ENDPOINT = "https://archive.org/advancedsearch.php"
    private const val METADATA_ENDPOINT = "https://archive.org/metadata/"
    private const val THUMBNAIL_ENDPOINT = "https://archive.org/services/img/"
    private const val USER_AGENT = "MISSA-TV-360/1.0 (Android film engine)"

    private val tagsPattern = Regex("<[^>]*>")
    private val whitespacePattern = Regex("\\s+")
    private val identifierPattern = Regex("[A-Za-z0-9._-]{1,120}")

    override suspend fun trending(config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            searchArchive(
                query = config.trendingQuery,
                sort = config.trendingSort,
                limit = config.trendingLimit,
                config = config,
            )
        }

    override suspend fun search(query: String, config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            val cleanedQuery = query.trim().take(120)
            if (cleanedQuery.isBlank()) return@withContext emptyList()
            val phrase = escapePhrase(cleanedQuery)
            val fieldQuery = config.searchFields.joinToString(" OR ") { field ->
                "$field:\"$phrase\""
            }
            searchArchive(
                query = "mediatype:${config.mediaType} AND ($fieldQuery)",
                sort = config.searchSort,
                limit = config.searchLimit,
                config = config,
            )
        }

    override suspend fun related(video: Video, config: FilmSourceConfig): List<Video> =
        withContext(Dispatchers.IO) {
            val matchFields = config.relatedFields.toSet()
            val clauses = mutableListOf<String>()
            if ("subject" in matchFields) {
                video.tags.asSequence().map { it.trim().take(120) }.filter { it.isNotBlank() }
                    .distinct().take(4)
                    .forEach { subject -> clauses += "subject:\"${escapePhrase(subject)}\"" }
            }
            if ("creator" in matchFields) {
                video.creator?.trim()?.take(160)?.takeIf { it.isNotBlank() }?.let { creator ->
                    clauses += "creator:\"${escapePhrase(creator)}\""
                }
            }
            if (clauses.isEmpty() && "title" in matchFields) {
                video.title.split(whitespacePattern).filter { it.length >= 3 }
                    .take(3).forEach { word -> clauses += "title:\"${escapePhrase(word)}\"" }
            }
            if (clauses.isEmpty()) return@withContext emptyList()

            val query = "mediatype:${config.mediaType} AND (${clauses.joinToString(" OR ")}) " +
                "AND -identifier:\"${escapePhrase(video.id)}\""
            searchArchive(query, config.relatedSort, config.relatedLimit, config)
        }

    override suspend fun load(result: Video, config: FilmSourceConfig): ResolvedSource? =
        withContext(Dispatchers.IO) {
            if (result.sourceId != config.id || !identifierPattern.matches(result.id)) {
                return@withContext null
            }

            val identifier = result.id
            val payload = JSONObject(request(METADATA_ENDPOINT + encodePathSegment(identifier)))
            val metadata = payload.optJSONObject("metadata") ?: JSONObject()
            val files = payload.optJSONArray("files") ?: JSONArray()
            val playableFiles = (0 until files.length()).mapNotNull { index ->
                val file = files.optJSONObject(index) ?: return@mapNotNull null
                val name = file.optString("name").trim()
                val format = name.substringAfterLast('.', "").lowercase()
                if (name.isBlank() || name.length > 512 || name.any { it.isISOControl() }) {
                    return@mapNotNull null
                }
                if (format !in config.playableFormats) return@mapNotNull null
                val pathParts = name.replace('\\', '/').split('/')
                if (pathParts.any { it == "." || it == ".." }) return@mapNotNull null
                ArchiveFile(
                    name = name,
                    format = format,
                    height = file.optString("height").toLongOrNull() ?: 0L,
                    size = file.optString("size").toLongOrNull() ?: 0L,
                )
            }
            val qualityOrder = when (config.qualityOrder) {
                "height_then_size" -> compareBy<ArchiveFile> { it.height }.thenBy { it.size }
                else -> compareBy<ArchiveFile> { it.size }.thenBy { it.height }
            }
            val selected = playableFiles.maxWithOrNull(qualityOrder)
                ?: return@withContext null

            val title = readText(metadata.opt("title")).trim().take(240)
                .ifBlank { result.title }
            val description = cleanText(readText(metadata.opt("description")))
                .ifBlank { result.description }
            val subjects = readStrings(metadata.opt("subject")).ifEmpty { result.tags }
            val creator = readText(metadata.opt("creator")).trim().take(240)
                .ifBlank { result.creator.orEmpty() }
            val year = (readText(metadata.opt("year")).ifBlank {
                readText(metadata.opt("date"))
            }).trim().take(20).ifBlank { result.releaseYear.orEmpty() }
            val encodedName = Uri.encode(selected.name, "-._~()@:+")
            val links = playableFiles.map { file ->
                ExtractorLink(
                    url = downloadUrl(identifier, Uri.encode(file.name, "-._~()@:+")),
                    quality = file.height.toInt(),
                    format = file.format,
                    headers = ExtractorLink.sanitizeHeaders(mapOf("User-Agent" to USER_AGENT)),
                )
            }

            ResolvedSource(
                video = Video(
                    id = identifier,
                    title = title,
                    description = description,
                    cardRes = R.drawable.card_tbd,
                    streamUrl = "https://archive.org/download/" +
                        "${encodePathSegment(identifier)}/$encodedName",
                    kind = ContentKind.MOVIE,
                    format = selected.format,
                    cardUrl = thumbnailUrl(identifier),
                    sourceId = config.id,
                    tags = subjects,
                    creator = creator,
                    releaseYear = year,
                    source = "Internet Archive",
                    license = normalizeLicense(
                        readText(metadata.opt("licenseurl")).ifBlank {
                            readText(metadata.opt("rights"))
                        },
                    ),
                ),
                links = links,
            )
        }

    private fun searchArchive(
        query: String,
        sort: String,
        limit: Int,
        config: FilmSourceConfig,
    ): List<Video> {
        val parameters = buildList {
            add("q" to query)
            listOf("identifier", "title", "description", "year", "subject", "creator")
                .forEach { add("fl[]" to it) }
            add("sort[]" to sort)
            add("rows" to limit.coerceIn(1, 50).toString())
            add("page" to "1")
            add("output" to "json")
        }
        val url = "$SEARCH_ENDPOINT?" + parameters.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val payload = JSONObject(request(url))
        val documents = payload.optJSONObject("response")?.optJSONArray("docs")
            ?: return emptyList()
        return (0 until documents.length()).mapNotNull { index ->
            val document = documents.optJSONObject(index) ?: return@mapNotNull null
            val identifier = document.optString("identifier").trim()
            val title = readText(document.opt("title")).take(240)
            if (!identifierPattern.matches(identifier) || title.isBlank()) return@mapNotNull null

            val description = cleanText(readText(document.opt("description"))).take(1200)
            val subjects = readStrings(document.opt("subject"))
            val creator = readText(document.opt("creator")).trim().take(240)
            val year = readText(document.opt("year")).trim().take(20)
            val cardDescription = when {
                description.isNotBlank() -> description
                year.isNotBlank() -> "Année : $year · Internet Archive"
                else -> "Film référencé sur Internet Archive."
            }
            Video(
                id = identifier,
                title = title,
                description = cardDescription,
                cardRes = R.drawable.card_tbd,
                streamUrl = "",
                kind = ContentKind.MOVIE,
                cardUrl = thumbnailUrl(identifier),
                sourceId = config.id,
                tags = subjects,
                creator = creator,
                releaseYear = year,
                source = "Internet Archive",
            )
        }.distinctBy(Video::id)
    }

    /**
     * Ramène une URL ou une mention de licence d'Internet Archive vers un libellé
     * court affichable sur la fiche détail. Retourne null si rien n'est exploitable
     * (la fiche n'affichera alors aucun badge de licence).
     */
    private fun normalizeLicense(raw: String): String? {
        val value = raw.trim().take(120)
        if (value.isEmpty()) return null
        val lower = value.lowercase(Locale.ROOT)
        return when {
            lower.contains("cc0") -> "CC0"
            lower.contains("publicdomain") || lower.contains("public domain") -> "Domaine public"
            lower.contains("by-nc-sa") -> "CC BY-NC-SA"
            lower.contains("by-nc") -> "CC BY-NC"
            lower.contains("by-sa") -> "CC BY-SA"
            lower.contains("creative commons") || lower.contains("cc by") -> "CC BY"
            else -> value
        }
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Internet Archive HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun escapePhrase(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun encodePathSegment(value: String): String = Uri.encode(value, "-._~")

    private fun thumbnailUrl(identifier: String): String =
        "$THUMBNAIL_ENDPOINT${encodePathSegment(identifier)}"

    /** URL de téléchargement d'un fichier d'un item Internet Archive. */
    private fun downloadUrl(identifier: String, encodedFileName: String): String =
        "https://archive.org/download/${encodePathSegment(identifier)}/$encodedFileName"

    private fun readText(value: Any?): String = when (value) {
        is String -> value
        is Number -> value.toString()
        is JSONArray -> readStrings(value).joinToString(" ")
        else -> ""
    }

    private fun readStrings(value: Any?): List<String> = when (value) {
        is String -> listOf(value.trim().take(160)).filter { it.isNotBlank() }
        is JSONArray -> (0 until minOf(value.length(), 30))
            .map { value.optString(it).trim().take(160) }
            .filter { it.isNotBlank() && it != "null" }
        else -> emptyList()
    }

    private fun cleanText(value: String): String =
        value.replace(tagsPattern, " ")
            .replace(whitespacePattern, " ")
            .trim()
            .take(1200)

    private data class ArchiveFile(
        val name: String,
        val format: String,
        val height: Long,
        val size: Long,
    )
}
